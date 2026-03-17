package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实服务器并发压力测试。
 *
 * <p>用于验证高并发请求、多连接同时认证以及批量建删用户时，
 * collector 与连接状态是否存在竞争问题。</p>
 *
 * @since 2026-03-13
 */
@Slf4j
@Disabled("需要真实的 XMPP 服务器 (Openfire)")
class XmppRealServerConcurrencyStressTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private final List<XmppTcpConnection> openedConnections = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (XmppTcpConnection openedConnection : openedConnections) {
            openedConnection.disconnect();
        }
        openedConnections.clear();
    }

    @Test
    void testHighConcurrencyPingRoundsOnSingleConnection() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);

        for (int round = 0; round < 3; round++) {
            List<CompletableFuture<Iq>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String pingId = "stress-ping-" + round + "-" + i + "-" + System.nanoTime();
                futures.add(adminConnection.sendIqPacketAsync(PingIq.createPingRequest(pingId, XMPP_DOMAIN))
                        .thenApply(stanza -> assertAndCastIq(stanza, pingId)));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            for (CompletableFuture<Iq> future : futures) {
                Iq iq = future.getNow(null);
                assertNotNull(iq, "高并发 ping 每轮都应全部成功");
                assertEquals(Iq.Type.RESULT, iq.getType(), "高并发 ping 响应应为 result");
            }
        }
    }

    @Test
    void testParallelUserAuthenticationAndPing() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD);
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        List<String> usernames = new ArrayList<>();
        List<String> passwords = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String username = "parallel_login_" + i + "_" + System.currentTimeMillis();
            String password = "Pass" + i + "123!@#";
            usernames.add(username);
            passwords.add(password);
            adminManager.addUser(username, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        try {
            List<CompletableFuture<Iq>> futures = new ArrayList<>();
            for (int i = 0; i < usernames.size(); i++) {
                String username = usernames.get(i);
                String password = passwords.get(i);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    XmppTcpConnection userConnection = null;
                    try {
                        userConnection = openAuthenticatedConnection(username, password);
                        String pingId = "parallel-user-ping-" + username + "-" + System.nanoTime();
                        return sendPing(userConnection, pingId);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    } finally {
                        if (userConnection != null) {
                            userConnection.disconnect();
                            openedConnections.remove(userConnection);
                        }
                    }
                }));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            for (CompletableFuture<Iq> future : futures) {
                Iq iq = future.getNow(null);
                assertNotNull(iq, "并发用户登录后的 ping 应成功");
                assertEquals(Iq.Type.RESULT, iq.getType(), "并发用户 ping 应返回 result");
            }
        } finally {
            for (String username : usernames) {
                adminManager.deleteUser(username).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
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

        Runnable authSubscription = eventBus.subscribe(connection, ConnectionEventType.AUTHENTICATED, event -> authenticatedLatch.countDown());
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

    private void awaitCondition(java.util.function.BooleanSupplier supplier, String message) throws Exception {
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
