package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实服务器边界与异常场景测试。
 *
 * <p>这些测试直接连接本地 Openfire 服务器，验证认证失败、重复创建、删除不存在用户、
 * 以及未实现管理命令等异常路径。</p>
 *
 * @since 2026-03-13
 */
@Slf4j
class XmppRealServerEdgeCaseTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    private XmppTcpConnection connection;

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    @Test
    void testAuthenticationFailsWithWrongPassword() throws Exception {
        XmppTcpConnection badConnection = null;
        try {
            badConnection = createConnection(ADMIN_USERNAME, "wrong-password");
            XmppTcpConnection finalBadConnection = badConnection;

            XmppException exception = assertThrows(XmppException.class, finalBadConnection::connect);
            assertFalse(finalBadConnection.isAuthenticated(), "错误密码不应认证成功");
            log.info("Wrong password authentication failed as expected: {}", exception.getMessage());
        } finally {
            if (badConnection != null) {
                badConnection.disconnect();
            }
        }
    }

    @Test
    void testDuplicateAddUserIsHandledIdempotently() throws Exception {
        AdminManager adminManager = connectAsAdmin();
        String username = "dup_user_" + System.currentTimeMillis();

        adminManager.addUser(username, "TestPass123!").get(20, TimeUnit.SECONDS);

        Iq duplicateResponse = awaitAdminSuccess(adminManager.addUser(username, "TestPass123!"));
        assertEquals(Iq.Type.RESULT, duplicateResponse.getType(), "本地服务器将重复创建视为幂等操作");
        log.info("Duplicate add user response XML: {}", duplicateResponse.toXml());

        adminManager.deleteUser(username).get(20, TimeUnit.SECONDS);
    }

    @Test
    void testDeleteNonexistentUserIsHandledIdempotently() throws Exception {
        AdminManager adminManager = connectAsAdmin();
        String username = "missing_user_" + System.currentTimeMillis();

        Iq deleteResponse = awaitAdminSuccess(adminManager.deleteUser(username));
        assertEquals(Iq.Type.RESULT, deleteResponse.getType(), "本地服务器将删除不存在用户视为幂等操作");
        log.info("Delete nonexistent user response XML: {}", deleteResponse.toXml());
    }

    @Test
    void testRepeatedAdminConnectDisconnectRemainsStable() throws Exception {
        for (int i = 0; i < 3; i++) {
            XmppTcpConnection cycleConnection = createConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
            try {
                awaitAuthenticated(cycleConnection);
                assertTrue(cycleConnection.isAuthenticated(), "第 " + (i + 1) + " 次连接应认证成功");
            } finally {
                cycleConnection.disconnect();
            }
        }
    }

    /**
     * 以管理员身份连接。
     *
     * @return 管理器
     * @throws Exception 连接失败
     */
    private AdminManager connectAsAdmin() throws Exception {
        connection = createConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        awaitAuthenticated(connection);
        return new AdminManager(connection, connection.getConfig());
    }

    /**
     * 创建测试连接。
     *
     * @param username 用户名
     * @param password 密码
     * @return 连接实例
     */
    private XmppTcpConnection createConnection(String username, String password) {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(username)
                .password(password.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        return new XmppTcpConnection(config);
    }

    /**
     * 等待连接完成认证。
     *
     * @param xmppConnection 待认证连接
     * @throws Exception 认证失败
     */
    private void awaitAuthenticated(XmppTcpConnection xmppConnection) throws Exception {
        XmppEventBus eventBus = XmppEventBus.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        Runnable authSubscription = eventBus.subscribe(xmppConnection, ConnectionEventType.AUTHENTICATED, event -> latch.countDown());
        Runnable errorSubscription = eventBus.subscribe(xmppConnection, ConnectionEventType.ERROR, event -> {
            errorRef.set(event.error());
            latch.countDown();
        });

        try {
            xmppConnection.connect();
            boolean completed = latch.await(15, TimeUnit.SECONDS);
            assertTrue(completed, "认证应在超时时间内完成");
            if (errorRef.get() != null) {
                fail("连接返回错误事件: " + errorRef.get().getMessage(), errorRef.get());
            }
            assertTrue(xmppConnection.isAuthenticated(), "连接应完成认证");
        } finally {
            authSubscription.run();
            errorSubscription.run();
        }
    }

    /**
     * 等待管理命令失败，并提取服务端错误。
     *
     * @param future 管理命令 Future
     * @return 管理命令异常
     * @throws Exception 非预期异常
     */
    private Iq awaitAdminFailure(CompletableFuture<XmppStanza> future) throws Exception {
        try {
            XmppStanza stanza = future.get(20, TimeUnit.SECONDS);
            fail("Expected admin command to fail, but received: " + stanza);
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof XmppStanzaErrorException see) {
                return see.getErrorIq();
            }
            if (cause instanceof AdminCommandException ace) {
                return ace.getErrorResponse();
            }
            throw e;
        }
    }

    /**
     * 等待管理命令成功并返回 IQ。
     *
     * @param future 管理命令 Future
     * @return 成功 IQ
     * @throws Exception 非预期异常
     */
    private Iq awaitAdminSuccess(CompletableFuture<XmppStanza> future) throws Exception {
        XmppStanza stanza = future.get(20, TimeUnit.SECONDS);
        assertInstanceOf(Iq.class, stanza, "管理命令应返回 IQ");
        return (Iq) stanza;
    }
}
