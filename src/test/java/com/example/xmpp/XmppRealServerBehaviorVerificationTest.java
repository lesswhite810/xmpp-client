package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实服务器行为校验测试。
 *
 * <p>重点验证服务端状态变化、资源路由和失败后的恢复能力，避免仅凭“测试用例未抛异常”误判为成功。</p>
 *
 * @since 2026-03-13
 */
@RealServerTest
class XmppRealServerBehaviorVerificationTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private final List<XmppTcpConnection> openedConnections = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (XmppTcpConnection openedConnection : openedConnections) {
            openedConnection.disconnect();
        }
        openedConnections.clear();
    }

    @Test
    void testAuthenticationFailureDoesNotBlockImmediateSuccessfulReconnect() throws Exception {
        XmppTcpConnection wrongPasswordConnection = createConnection(ADMIN_USERNAME, "definitely-wrong-password", "wrong-pass");
        XmppException exception = assertThrows(XmppException.class, wrongPasswordConnection::connect,
                "错误密码应认证失败");
        assertNotNull(exception.getMessage(), "失败连接应返回异常信息");
        assertFalse(wrongPasswordConnection.isAuthenticated(), "错误密码不应进入认证状态");
        wrongPasswordConnection.disconnect();

        XmppTcpConnection goodConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "reconnect-after-fail");
        Iq pingResponse = sendPing(goodConnection, "reconnect-ping-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, pingResponse.getType(), "失败后重新使用正确密码应立即恢复可用");
        assertTrue(goodConnection.collectors.isEmpty(), "恢复成功后不应残留 collector");
    }

    @Test
    void testSameAccountDifferentResourcesAreIndependentlyRoutable() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "admin-resource-route");
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        String sharedUsername = "shared_res_" + System.currentTimeMillis();
        String probeUsername = "probe_res_" + System.currentTimeMillis();
        String password = "Pass123!@#";
        String resourceA = "res-a-" + System.nanoTime();
        String resourceB = "res-b-" + System.nanoTime();

        try {
            adminManager.addUser(sharedUsername, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            adminManager.addUser(probeUsername, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            XmppTcpConnection sharedA = openAuthenticatedConnection(sharedUsername, password, resourceA);
            XmppTcpConnection sharedB = openAuthenticatedConnection(sharedUsername, password, resourceB);
            XmppTcpConnection probe = openAuthenticatedConnection(probeUsername, password, "probe-" + System.nanoTime());

            String targetA = buildFullJid(sharedUsername, resourceA);
            String targetB = buildFullJid(sharedUsername, resourceB);

            Iq responseA = sendPing(probe, "route-a-" + System.nanoTime(), targetA);
            Iq responseB = sendPing(probe, "route-b-" + System.nanoTime(), targetB);

            assertEquals(targetA, responseA.getFrom(), "发往 resource-a 的 IQ 应由 resource-a 返回");
            assertEquals(targetB, responseB.getFrom(), "发往 resource-b 的 IQ 应由 resource-b 返回");
            assertEquals(responseA.getTo(), responseB.getTo(), "两个资源响应都应回到同一个探测连接");
            assertTrue(sharedA.isAuthenticated(), "resource-a 应保持在线");
            assertTrue(sharedB.isAuthenticated(), "resource-b 应保持在线");
        } finally {
            adminManager.deleteUser(sharedUsername).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            adminManager.deleteUser(probeUsername).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSameResourceReloginDoesNotLeaveStaleSession() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "admin-same-resource");
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        String username = "same_res_" + System.currentTimeMillis();
        String probeUsername = "same_res_probe_" + System.currentTimeMillis();
        String password = "Pass123!@#";
        String sharedResource = "shared-" + System.nanoTime();

        try {
            adminManager.addUser(username, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            adminManager.addUser(probeUsername, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            XmppTcpConnection firstConnection = openAuthenticatedConnection(username, password, sharedResource);
            Iq firstPing = sendPing(firstConnection, "first-before-relogin-" + System.nanoTime(), XMPP_DOMAIN);
            assertEquals(Iq.Type.RESULT, firstPing.getType(), "首次登录后应可正常 ping");

            XmppTcpConnection secondConnection = openAuthenticatedConnection(username, password, sharedResource);
            XmppTcpConnection probeConnection = openAuthenticatedConnection(probeUsername, password, "probe-" + System.nanoTime());

            Iq routedPing = sendPing(probeConnection, "same-resource-route-" + System.nanoTime(),
                    buildFullJid(username, sharedResource));
            assertEquals(buildFullJid(username, sharedResource), routedPing.getFrom(),
                    "同一 resource 重登后，服务器仍应只暴露一个完整 JID");

            Iq secondPing = sendPing(secondConnection, "second-after-relogin-" + System.nanoTime(), XMPP_DOMAIN);
            assertEquals(Iq.Type.RESULT, secondPing.getType(), "重登后的连接应保持可用");

            firstConnection.cleanupCollectors();
            secondConnection.cleanupCollectors();
            assertTrue(firstConnection.collectors.stream().noneMatch(collector -> !collector.getFuture().isDone()),
                    "旧连接不应残留未完成 collector");
            assertTrue(secondConnection.collectors.stream().noneMatch(collector -> !collector.getFuture().isDone()),
                    "新连接不应残留未完成 collector");
        } finally {
            adminManager.deleteUser(username).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            adminManager.deleteUser(probeUsername).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void testReusingSameConnectionObjectAfterDisconnectRemainsHealthy() throws Exception {
        XmppTcpConnection connection = createConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "reusable-" + System.nanoTime());
        openedConnections.add(connection);

        awaitAuthenticated(connection);
        Iq firstPing = sendPing(connection, "reusable-first-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, firstPing.getType(), "第一次连接应可正常 ping");

        connection.disconnect();
        awaitCondition(() -> !connection.isConnected(), "断开后连接对象应真正进入非连接状态");
        connection.cleanupCollectors();
        assertTrue(connection.collectors.isEmpty(), "断开后不应残留 collector");

        awaitAuthenticated(connection);
        Iq secondPing = sendPing(connection, "reusable-second-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, secondPing.getType(), "同一连接对象重连后仍应可正常 ping");
        assertTrue(connection.isAuthenticated(), "重连后应重新处于已认证状态");
    }

    @Test
    void testRepeatedReuseOfSameConnectionObjectRemainsStableAcrossCycles() throws Exception {
        XmppTcpConnection connection = createConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "reusable-loop-" + System.nanoTime());
        openedConnections.add(connection);

        for (int i = 0; i < 5; i++) {
            awaitAuthenticated(connection);
            Iq ping = sendPing(connection, "reuse-loop-" + i + "-" + System.nanoTime(), XMPP_DOMAIN);
            assertEquals(Iq.Type.RESULT, ping.getType(), "每次重连后的 ping 都应成功");

            connection.disconnect();
            awaitCondition(() -> !connection.isConnected(), "每轮断开后连接都应真正关闭");
            connection.cleanupCollectors();
            assertTrue(connection.collectors.isEmpty(), "每轮断开后都不应残留 collector");
        }
    }

    @Test
    void testFailedIqAfterDisconnectDoesNotLeakCollectorsOrBreakReconnect() throws Exception {
        XmppTcpConnection connection = createConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "disconnect-fail-" + System.nanoTime());
        openedConnections.add(connection);

        awaitAuthenticated(connection);
        connection.disconnect();
        awaitCondition(() -> !connection.isConnected(), "断开后连接应退出 connected 状态");

        for (int i = 0; i < 3; i++) {
            String pingId = "after-disconnect-" + i + "-" + System.nanoTime();
            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> connection.sendIqPacketAsync(
                                    PingIq.createPingRequest(pingId, XMPP_DOMAIN),
                                    1,
                                    TimeUnit.SECONDS)
                            .get(3, TimeUnit.SECONDS),
                    "断开后发送 IQ 应持续失败");
            assertNotNull(exception.getCause(), "失败发送应保留底层异常");
        }

        awaitCondition(connection.collectors::isEmpty, "断开后连续失败发送不应泄漏 collector");

        awaitAuthenticated(connection);
        Iq ping = sendPing(connection, "after-failed-reconnect-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, ping.getType(), "连续失败发送后重连仍应恢复可用");
    }

    private XmppTcpConnection openAuthenticatedConnection(String username, String password, String resource) throws Exception {
        XmppTcpConnection connection = createConnection(username, password, resource);
        openedConnections.add(connection);
        awaitAuthenticated(connection);
        return connection;
    }

    private XmppTcpConnection createConnection(String username, String password, String resource) {
        return new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .resource(resource)
                .sendPresence(true)
                .username(username)
                .password(password.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build());
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
            if (errorRef.get() != null) {
                throw errorRef.get();
            }
            assertTrue(connection.isAuthenticated(), "连接应处于已认证状态");
        } finally {
            authSubscription.run();
            errorSubscription.run();
        }
    }

    private Iq sendPing(XmppTcpConnection connection, String expectedId, String to) throws Exception {
        XmppStanza stanza = connection.sendIqPacketAsync(PingIq.createPingRequest(expectedId, to))
                .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Iq iq = assertAndCastIq(stanza);
        assertEquals(expectedId, iq.getId(), "响应 id 应与请求匹配");
        return iq;
    }

    private Iq assertAndCastIq(XmppStanza stanza) {
        assertInstanceOf(Iq.class, stanza, "响应应为 IQ");
        return (Iq) stanza;
    }

    private String buildFullJid(String username, String resource) {
        return username + "@" + XMPP_DOMAIN + "/" + resource;
    }

    private void awaitCondition(BooleanSupplier supplier, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (supplier.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(message);
    }
}
