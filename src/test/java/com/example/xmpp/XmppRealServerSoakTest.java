package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 真实服务器耐久循环测试。
 *
 * <p>用于验证连接在较长时间重复使用、反复建立与回收，以及用户生命周期重复执行时，
 * 是否会出现状态泄漏、响应错配或资源清理不完整的问题。</p>
 *
 * @since 2026-03-13
 */
@Disabled("需要真实的 XMPP 服务器 (Openfire)")
class XmppRealServerSoakTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_LOOP_COUNT = 25;
    private static final int RECONNECT_LOOP_COUNT = 12;
    private static final int USER_LIFECYCLE_LOOP_COUNT = 6;

    private final List<XmppTcpConnection> openedConnections = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (XmppTcpConnection openedConnection : openedConnections) {
            openedConnection.disconnect();
        }
        openedConnections.clear();
    }

    @Test
    void testRepeatedPingAndListUsersLoopOnSingleConnection() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        for (int i = 0; i < REQUEST_LOOP_COUNT; i++) {
            String pingId = "soak-ping-" + i + "-" + System.nanoTime();
            Iq pingResponse = sendPing(adminConnection, pingId);
            assertEquals(Iq.Type.RESULT, pingResponse.getType(), "耐久 ping 应始终返回 result");

            String usersXml = listUsersXml(adminManager);
            assertTrue(usersXml.contains(ADMIN_USERNAME), "管理员应始终出现在用户列表中");
        }
    }

    @Test
    void testRepeatedConnectAuthenticateAndDisconnectLoop() throws Exception {
        for (int i = 0; i < RECONNECT_LOOP_COUNT; i++) {
            XmppTcpConnection connection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
            assertTrue(connection.isAuthenticated(), "反复建立连接后应完成认证");

            Iq pingResponse = sendPing(connection, "reconnect-ping-" + i + "-" + System.nanoTime());
            assertEquals(Iq.Type.RESULT, pingResponse.getType(), "反复连接后的 ping 应成功");

            connection.disconnect();
            openedConnections.remove(connection);
            awaitCondition(() -> !connection.isConnected(), "连接断开后应退出 connected 状态");
        }
    }

    @Test
    void testRepeatedUserLifecycleWithAuthenticationLoop() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        for (int i = 0; i < USER_LIFECYCLE_LOOP_COUNT; i++) {
            String username = "soak_user_" + i + "_" + System.currentTimeMillis();
            String password = "Pass" + i + "123!@#";
            String jid = username + "@" + XMPP_DOMAIN;

            adminManager.addUser(username, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            awaitCondition(() -> listUsersXml(adminManager).contains(jid), "创建后的用户应能被 list-users 观察到");

            XmppTcpConnection userConnection = null;
            try {
                userConnection = openAuthenticatedConnection(username, password);
                Iq pingResponse = sendPing(userConnection, "soak-user-ping-" + i + "-" + System.nanoTime());
                assertEquals(Iq.Type.RESULT, pingResponse.getType(), "循环创建的用户登录后 ping 应成功");
            } finally {
                if (userConnection != null) {
                    userConnection.disconnect();
                    openedConnections.remove(userConnection);
                }
            }

            adminManager.deleteUser(username).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            awaitCondition(() -> !listUsersXml(adminManager).contains(jid), "删除后的用户应从 list-users 中消失");
        }
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

        Runnable authSubscription = eventBus.subscribe(connection, ConnectionEventType.AUTHENTICATED,
                event -> authenticatedLatch.countDown());
        Runnable errorSubscription = eventBus.subscribe(connection, ConnectionEventType.ERROR, event -> {
            errorRef.set(event.error());
            authenticatedLatch.countDown();
        });

        try {
            connection.connect();
            assertTrue(authenticatedLatch.await(15, TimeUnit.SECONDS), "认证应在超时时间内完成");
            assertNull(errorRef.get(), "认证过程中不应收到错误事件");
            assertTrue(connection.isAuthenticated(), "连接应处于已认证状态");
        } finally {
            authSubscription.run();
            errorSubscription.run();
        }
    }

    private Iq sendPing(XmppTcpConnection connection, String expectedId) {
        try {
            XmppStanza stanza = connection.sendIqPacketAsync(PingIq.createPingRequest(expectedId, XMPP_DOMAIN))
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return assertAndCastIq(stanza, expectedId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new CompletionException(e);
        }
    }

    private Iq assertAndCastIq(XmppStanza stanza, String expectedId) {
        assertInstanceOf(Iq.class, stanza, "响应应为 IQ");
        Iq iq = (Iq) stanza;
        assertEquals(expectedId, iq.getId(), "响应 id 应与请求匹配");
        return iq;
    }

    private String listUsersXml(AdminManager adminManager) {
        try {
            return adminManager.listUsers()
                    .thenApply(stanza -> assertInstanceOf(Iq.class, stanza).toXml())
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new CompletionException(e);
        }
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
