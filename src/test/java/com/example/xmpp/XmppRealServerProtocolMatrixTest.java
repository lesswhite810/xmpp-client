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
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实服务器协议矩阵测试。
 *
 * <p>覆盖 TLS、SASL、客户端间 IQ 交互、断开后的超时清理等补充场景。</p>
 *
 * @since 2026-03-13
 */
class XmppRealServerProtocolMatrixTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final int DIRECT_TLS_PORT = 5223;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static final X509TrustManager REJECT_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Test trust manager rejected client certificate");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Test trust manager rejected server certificate");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private final List<XmppTcpConnection> openedConnections = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (XmppTcpConnection openedConnection : openedConnections) {
            openedConnection.disconnect();
        }
        openedConnections.clear();
    }

    @Test
    void testStartTlsRequiredWithTrustAllManagerSucceeds() throws Exception {
        XmppTcpConnection connection = openAuthenticatedConnection(createConnection(
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                "tls-required-" + System.currentTimeMillis(),
                XmppClientConfig.SecurityMode.REQUIRED,
                Set.of(),
                new TrustManager[]{TRUST_ALL_MANAGER},
                false));

        Iq response = sendPing(connection, "required-starttls-ping-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, response.getType(), "启用 STARTTLS 后 ping 应成功");
    }

    @Test
    void testStartTlsRequiredWithRejectingTrustManagerFails() {
        XmppTcpConnection connection = createConnection(
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                "tls-reject-" + System.currentTimeMillis(),
                XmppClientConfig.SecurityMode.REQUIRED,
                Set.of(),
                new TrustManager[]{REJECT_ALL_MANAGER},
                false);
        openedConnections.add(connection);

        XmppException exception = assertThrows(XmppException.class, connection::connect);
        assertNotNull(exception.getMessage(), "TLS 失败应暴露异常信息");
    }

    @Test
    void testDirectTlsSucceedsWhenPortAvailable() throws Exception {
        Assumptions.assumeTrue(isPortReachable(HOST, DIRECT_TLS_PORT, 1000), "本地真实服务器未开放 5223 Direct TLS 端口");

        XmppTcpConnection connection = openAuthenticatedConnection(createConnection(
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                "direct-tls-" + System.currentTimeMillis(),
                XmppClientConfig.SecurityMode.REQUIRED,
                Set.of(),
                new TrustManager[]{TRUST_ALL_MANAGER},
                true));

        Iq response = sendPing(connection, "direct-tls-ping-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, response.getType(), "Direct TLS 建连后 ping 应成功");
    }

    @Test
    void testForcedScramSha1AuthenticationSucceeds() throws Exception {
        XmppTcpConnection connection = openAuthenticatedConnection(createConnection(
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                "scram-only-" + System.currentTimeMillis(),
                XmppClientConfig.SecurityMode.DISABLED,
                Set.of("SCRAM-SHA-1"),
                null,
                false));

        Iq response = sendPing(connection, "scram-ping-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, response.getType(), "限制为 SCRAM-SHA-1 后认证与 ping 应成功");
    }

    @Test
    void testForcedPlainAuthenticationSucceeds() throws Exception {
        XmppTcpConnection connection = openAuthenticatedConnection(createConnection(
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                "plain-only-" + System.currentTimeMillis(),
                XmppClientConfig.SecurityMode.REQUIRED,
                Set.of("PLAIN"),
                new TrustManager[]{TRUST_ALL_MANAGER},
                false));

        Iq response = sendPing(connection, "plain-ping-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, response.getType(), "TLS 保护下限制为 PLAIN 后认证与 ping 应成功");
    }

    @Test
    void testClientToClientPingReturnsResult() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(createConnection(
                ADMIN_USERNAME, ADMIN_PASSWORD, "admin-c2c-" + System.currentTimeMillis(),
                XmppClientConfig.SecurityMode.DISABLED, Set.of(), null, false));
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        String userOne = "c2c_ping_a_" + System.currentTimeMillis();
        String userTwo = "c2c_ping_b_" + System.currentTimeMillis();
        String password = "Pass123!@#";
        String resourceOne = "res-a-" + System.nanoTime();
        String resourceTwo = "res-b-" + System.nanoTime();

        adminManager.addUser(userOne, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        adminManager.addUser(userTwo, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        try {
            XmppTcpConnection sender = openAuthenticatedConnection(createConnection(
                    userOne, password, resourceOne, XmppClientConfig.SecurityMode.DISABLED, Set.of(), null, false));
            XmppTcpConnection receiver = openAuthenticatedConnection(createConnection(
                    userTwo, password, resourceTwo, XmppClientConfig.SecurityMode.DISABLED, Set.of(), null, false));

            String targetJid = buildFullJid(userTwo, resourceTwo);
            Iq response = sendPing(sender, "client-to-client-ping-" + System.nanoTime(), targetJid);
            assertEquals(Iq.Type.RESULT, response.getType(), "客户端间 ping 应由接收端返回 result");
            assertEquals(targetJid, response.getFrom(), "响应 from 应为目标客户端完整 JID");
            assertEquals(buildFullJid(userOne, resourceOne), response.getTo(), "响应 to 应回到发送客户端完整 JID");
            assertTrue(receiver.isAuthenticated(), "接收端连接应保持认证状态");
        } finally {
            adminManager.deleteUser(userOne).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            adminManager.deleteUser(userTwo).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void testClientToClientUnsupportedIqReturnsStandardError() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(createConnection(
                ADMIN_USERNAME, ADMIN_PASSWORD, "admin-unsupported-" + System.currentTimeMillis(),
                XmppClientConfig.SecurityMode.DISABLED, Set.of(), null, false));
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        String userOne = "c2c_unsupported_a_" + System.currentTimeMillis();
        String userTwo = "c2c_unsupported_b_" + System.currentTimeMillis();
        String password = "Pass123!@#";
        String resourceOne = "res-c-" + System.nanoTime();
        String resourceTwo = "res-d-" + System.nanoTime();

        adminManager.addUser(userOne, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        adminManager.addUser(userTwo, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        try {
            XmppTcpConnection sender = openAuthenticatedConnection(createConnection(
                    userOne, password, resourceOne, XmppClientConfig.SecurityMode.DISABLED, Set.of(), null, false));
            openAuthenticatedConnection(createConnection(
                    userTwo, password, resourceTwo, XmppClientConfig.SecurityMode.DISABLED, Set.of(), null, false));

            Iq unsupportedRequest = new Iq.Builder(Iq.Type.GET)
                    .id("client-unsupported-" + System.nanoTime())
                    .to(buildFullJid(userTwo, resourceTwo))
                    .childElement(new GenericExtensionElement.Builder("query", "urn:example:real:unsupported").build())
                    .build();

            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> sender.sendIqPacketAsync(unsupportedRequest).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertInstanceOf(XmppStanzaErrorException.class, exception.getCause(), "应收到标准 IQ error");
            XmppStanzaErrorException stanzaErrorException = (XmppStanzaErrorException) exception.getCause();
        assertEquals(XmppError.Condition.SERVICE_UNAVAILABLE,
                    stanzaErrorException.getXmppError().getCondition(),
                    "未知命名空间应返回 service-unavailable");
        } finally {
            adminManager.deleteUser(userOne).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            adminManager.deleteUser(userTwo).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSendIqAfterDisconnectTimesOutAndCollectorIsCleaned() throws Exception {
        XmppTcpConnection connection = openAuthenticatedConnection(createConnection(
                ADMIN_USERNAME, ADMIN_PASSWORD, "disconnect-check-" + System.currentTimeMillis(),
                XmppClientConfig.SecurityMode.DISABLED, Set.of(), null, false));

        connection.disconnect();
        openedConnections.remove(connection);

        Iq pingRequest = PingIq.createPingRequest("after-disconnect-" + System.nanoTime(), XMPP_DOMAIN);
        CompletableFuture<XmppStanza> future = connection.sendIqPacketAsync(pingRequest, 1, TimeUnit.SECONDS);

        assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS),
                "断开后发送 IQ 应以超时失败");
        awaitCondition(() -> connection.collectors.isEmpty(), "断开后的超时 collector 应被清理");
        assertTrue(connection.collectors.isEmpty(), "断开后不应残留 collector");
    }

    private XmppTcpConnection openAuthenticatedConnection(XmppTcpConnection connection) throws Exception {
        openedConnections.add(connection);
        awaitAuthenticated(connection);
        return connection;
    }

    private XmppTcpConnection createConnection(String username,
                                               String password,
                                               String resource,
                                               XmppClientConfig.SecurityMode securityMode,
                                               Set<String> saslMechanisms,
                                               TrustManager[] trustManagers,
                                               boolean directTls) {
        return new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(directTls ? DIRECT_TLS_PORT : PORT)
                .resource(resource)
                .sendPresence(true)
                .username(username)
                .password(password.toCharArray())
                .securityMode(securityMode)
                .enabledSaslMechanisms(saslMechanisms)
                .customTrustManager(trustManagers)
                .usingDirectTLS(directTls)
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

    private Iq sendPing(XmppTcpConnection connection, String expectedId, String to) {
        try {
            XmppStanza stanza = connection.sendIqPacketAsync(PingIq.createPingRequest(expectedId, to))
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return assertAndCastIq(stanza, expectedId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new CompletionException(e);
        }
    }

    private Iq assertAndCastIq(XmppStanza stanza, String expectedId) {
        assertInstanceOf(Iq.class, stanza, "响应应为 IQ");
        Iq iq = (Iq) stanza;
        assertEquals(expectedId, iq.getId(), "响应 id 应与请求匹配");
        return iq;
    }

    private String buildFullJid(String username, String resource) {
        return username + "@" + XMPP_DOMAIN + "/" + resource;
    }

    private boolean isPortReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void awaitCondition(BooleanSupplier supplier, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (supplier.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(message);
    }
}
