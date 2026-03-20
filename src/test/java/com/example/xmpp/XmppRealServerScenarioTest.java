package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实服务器综合场景测试。
 *
 * <p>覆盖真实 Openfire 服务器上的正常流程、异常流程、边界处理和并发行为。</p>
 *
 * @since 2026-03-13
 */
class XmppRealServerScenarioTest extends AbstractRealServerTest {

    private static final Logger log = LoggerFactory.getLogger(XmppRealServerScenarioTest.class);

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final long DEFAULT_TIMEOUT_SECONDS = 20L;

    private final List<XmppTcpConnection> openedConnections = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (XmppTcpConnection openedConnection : openedConnections) {
            openedConnection.disconnect();
        }
        openedConnections.clear();
    }

    @Test
    void testAdminConnectionPublishesAuthenticatedEvent() throws Exception {
        XmppTcpConnection adminConnection = createConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        openedConnections.add(adminConnection);
        CountDownLatch authenticatedLatch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        XmppEventBus eventBus = XmppEventBus.getInstance();
        Runnable authenticatedSubscription = eventBus.subscribe(adminConnection, ConnectionEventType.AUTHENTICATED, event -> authenticatedLatch.countDown());
        Runnable errorSubscription = eventBus.subscribe(adminConnection, ConnectionEventType.ERROR, event -> {
            errorRef.set(event.error());
            authenticatedLatch.countDown();
        });

        try {
            adminConnection.connect();
            assertTrue(authenticatedLatch.await(10, TimeUnit.SECONDS), "应收到 AUTHENTICATED 事件");
            assertNull(errorRef.get(), "认证过程中不应收到错误事件");
            assertTrue(adminConnection.isAuthenticated(), "管理员应完成认证");
        } finally {
            authenticatedSubscription.run();
            errorSubscription.run();
        }
    }

    @Test
    void testPingSucceedsOnAuthenticatedConnection() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        Iq response = sendPing(adminConnection, "real-ping-" + System.currentTimeMillis());
        assertEquals(Iq.Type.RESULT, response.getType(), "真实服务器 ping 应返回 result");
    }

    @Test
    void testCreatedUserCanAuthenticateAndPing() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        String username = "login_user_" + System.currentTimeMillis();
        String password = "Pass123!@#";
        adminManager.addUser(username, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        XmppTcpConnection userConnection = null;
        try {
            userConnection = openAuthenticatedConnection(username, password);
            Iq pingResponse = sendPing(userConnection, "user-ping-" + System.currentTimeMillis());
            assertEquals(Iq.Type.RESULT, pingResponse.getType(), "新用户登录后 ping 应成功");
        } finally {
            if (userConnection != null) {
                userConnection.disconnect();
                openedConnections.remove(userConnection);
            }
            adminManager.deleteUser(username).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void testDeletedUserCannotAuthenticate() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        String username = "delete_login_" + System.currentTimeMillis();
        String password = "Pass123!@#";

        adminManager.addUser(username, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        adminManager.deleteUser(username).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        XmppTcpConnection deletedUserConnection = createConnection(username, password);
        XmppException exception = assertThrows(XmppException.class, deletedUserConnection::connect);
        log.info("Deleted user authentication failed as expected: {}", exception.getMessage());
        deletedUserConnection.disconnect();
    }

    @Test
    void testConcurrentPingsOnSingleConnection() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);

        List<CompletableFuture<Iq>> pingFutures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String pingId = "concurrent-ping-" + i + "-" + System.nanoTime();
            CompletableFuture<Iq> future = adminConnection.sendIqPacketAsync(PingIq.createPingRequest(pingId, XMPP_DOMAIN))
                    .thenApply(stanza -> assertAndCastIq(stanza, pingId));
            pingFutures.add(future);
        }

        CompletableFuture.allOf(pingFutures.toArray(CompletableFuture[]::new))
                .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        for (CompletableFuture<Iq> pingFuture : pingFutures) {
            Iq iq = pingFuture.getNow(null);
            assertNotNull(iq, "并发 ping 应全部返回结果");
            assertEquals(Iq.Type.RESULT, iq.getType(), "并发 ping 应全部成功");
        }
    }

    @Test
    void testUnsupportedCustomQueryReturnsError() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        Iq versionQuery = new Iq.Builder(Iq.Type.GET)
                .id("unknown-query-" + System.currentTimeMillis())
                .to(XMPP_DOMAIN)
                .childElement(new GenericExtensionElement.Builder("query", "urn:example:unsupported:feature").build())
                .build();

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> adminConnection.sendIqPacketAsync(versionQuery).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof XmppStanzaErrorException, "应收到 IQ error 异常");
        XmppStanzaErrorException stanzaErrorException = (XmppStanzaErrorException) exception.getCause();
        assertNotNull(stanzaErrorException.getXmppError(), "错误异常应包含 xmpp error");
        assertNotNull(stanzaErrorException.getXmppError().getCondition(), "错误响应应包含错误条件");
        log.info("Unsupported custom query returned condition={}",
                stanzaErrorException.getXmppError().getCondition());
    }

    private XmppTcpConnection openAuthenticatedConnection(String username, String password) throws Exception {
        XmppTcpConnection connection = createConnection(username, password);
        openedConnections.add(connection);
        awaitAuthenticated(connection);
        return connection;
    }

    private XmppTcpConnection createConnection(String username, String password) {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .sendPresence(true)
                .username(username)
                .password(password.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        return new XmppTcpConnection(config);
    }

    private void awaitAuthenticated(XmppTcpConnection connection) throws Exception {
        XmppEventBus eventBus = XmppEventBus.getInstance();
        CountDownLatch authenticatedLatch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        Runnable authSubscription = eventBus.subscribe(connection, ConnectionEventType.AUTHENTICATED, event -> authenticatedLatch.countDown());
        Runnable errorSubscription = eventBus.subscribe(connection, ConnectionEventType.ERROR, event -> {
            errorRef.set(event.error());
            authenticatedLatch.countDown();
        });

        try {
            connection.connect();
            assertTrue(authenticatedLatch.await(15, TimeUnit.SECONDS), "认证应在超时时间内完成");
            if (errorRef.get() != null) {
                fail("连接过程收到错误事件: " + errorRef.get().getMessage(), errorRef.get());
            }
            assertTrue(connection.isAuthenticated(), "连接应处于已认证状态");
        } finally {
            authSubscription.run();
            errorSubscription.run();
        }
    }

    private Iq sendPing(XmppTcpConnection connection, String id) throws Exception {
        XmppStanza stanza = connection.sendIqPacketAsync(PingIq.createPingRequest(id, XMPP_DOMAIN))
                .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return assertAndCastIq(stanza, id);
    }

    private Iq assertAndCastIq(XmppStanza stanza, String expectedId) {
        assertInstanceOf(Iq.class, stanza, "响应应为 IQ");
        Iq iq = (Iq) stanza;
        assertEquals(expectedId, iq.getId(), "响应 id 应与请求匹配");
        return iq;
    }

    private void awaitCondition(BooleanSupplier supplier, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (supplier.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        fail(message);
    }
}
