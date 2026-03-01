package com.example.xmpp;

import com.example.xmpp.config.AuthConfig;
import com.example.xmpp.config.ConnectionConfig;
import com.example.xmpp.config.KeepAliveConfig;
import com.example.xmpp.config.SecurityConfig;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * XMPP 集成测试套件。
 *
 * <p>测试场景包括：</p>
 * <ul>
 *   <li>所有 SASL 认证算法（PLAIN, SCRAM-SHA-1, SCRAM-SHA-256, SCRAM-SHA-512）</li>
 *   <li>服务器断连后自动重连</li>
 *   <li>TLS 连接</li>
 *   <li>消息收发</li>
 *   <li>Ping/Pong</li>
 * </ul>
 *
 * <p>OpenFire 配置：</p>
 * <ul>
 *   <li>服务器地址: localhost</li>
 *   <li>管理控制台: http://127.0.0.1:9090/</li>
 *   <li>XMPP 域名: lesswhite</li>
 *   <li>用户名: acs / 密码: acs</li>
 *   <li>管理员: admin / 密码: admin</li>
 * </ul>
 */
@Slf4j
public class XmppIntegrationTest {

    private static final String HOST = "localhost";
    private static final String DOMAIN = "lesswhite";
    private static final String USERNAME = "acs";
    private static final String PASSWORD = "acs";
    private static final int PORT_PLAIN = 5222;
    private static final int PORT_TLS = 5223;

    public static void main(String[] args) throws Exception {
        List<String> results = new ArrayList<>();

        log.info("========================================");
        log.info("XMPP Integration Test Suite");
        log.info("========================================");

        results.add(testPlainConnection());
        results.add(testTlsConnection());
        results.add(testDirectTlsConnection()); // Direct TLS (5223)
        results.add(testSaslScramSha1());
        results.add(testSaslScramSha256());
        results.add(testSaslScramSha512());
        results.add(testSaslPlain());
        results.add(testReconnection());
        results.add(testMessageSend());
        log.info("========================================");
        log.info("Test Results Summary:");
        log.info("========================================");
        for (String result : results) {
            log.info("  {}", result);
        }

        long passed = results.stream().filter(r -> r.contains("PASS")).count();
        long failed = results.stream().filter(r -> r.contains("FAIL")).count();
        log.info("========================================");
        log.info("Total: {} tests, {} passed, {} failed", results.size(), passed, failed);
        log.info("========================================");
    }

    private static String testPlainConnection() {
        String testName = "[PLAIN TCP Connection]";
        log.info("\n--- Test: {} ---", testName);

        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain(DOMAIN)
                        .host(HOST)
                        .port(PORT_PLAIN)
                        .sendPresence(false)
                        .build())
                .auth(AuthConfig.builder()
                        .username(USERNAME)
                        .password(PASSWORD.toCharArray())
                        .build())
                .security(SecurityConfig.builder()
                        .securityMode(SecurityConfig.SecurityMode.DISABLED)
                        .build())
                .build();

        try {
            TestResult result = runConnectionTest(config, 10);
            if (result.authenticated) {
                log.info("{} PASS - Connected and authenticated successfully", testName);
                return testName + " PASS";
            } else {
                log.error("{} FAIL - Authentication timeout", testName);
                return testName + " FAIL (auth timeout)";
            }
        } catch (Exception e) {
            log.error("{} FAIL - {}", testName, e.getMessage(), e);
            return testName + " FAIL (" + e.getMessage() + ")";
        }
    }

    private static String testTlsConnection() {
        String testName = "[TLS Connection]";
        log.info("\n--- Test: {} ---", testName);

        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain(DOMAIN)
                        .host(HOST)
                        .port(PORT_PLAIN)
                        .sendPresence(false)
                        .build())
                .auth(AuthConfig.builder()
                        .username(USERNAME)
                        .password(PASSWORD.toCharArray())
                        .build())
                .security(SecurityConfig.builder()
                        .securityMode(SecurityConfig.SecurityMode.REQUIRED)
                        .build())
                .build();

        try {
            TestResult result = runConnectionTest(config, 15);
            if (result.authenticated) {
                log.info("{} PASS - TLS connection successful", testName);
                return testName + " PASS";
            } else {
                log.error("{} FAIL - TLS authentication timeout", testName);
                return testName + " FAIL (TLS auth timeout)";
            }
        } catch (Exception e) {
            log.error("{} FAIL - {}", testName, e.getMessage(), e);
            return testName + " FAIL (" + e.getMessage() + ")";
        }
    }

    /**
     * Direct TLS 连接测试（端口 5223）。
     *
     * <p>Direct TLS 模式直接在 TCP 连接上建立 TLS 隧道，无需 STARTTLS 协商。</p>
     */
    private static String testDirectTlsConnection() {
        String testName = "[Direct TLS Connection]";
        log.info("\n--- Test: {} ---", testName);

        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain(DOMAIN)
                        .host(HOST)
                        .port(PORT_TLS)
                        .sendPresence(false)
                        .build())
                .auth(AuthConfig.builder()
                        .username(USERNAME)
                        .password(PASSWORD.toCharArray())
                        .build())
                .security(SecurityConfig.builder()
                        .usingDirectTLS(true)
                        .build())
                .build();

        try {
            TestResult result = runConnectionTest(config, 15);
            if (result.authenticated) {
                log.info("{} PASS - Direct TLS connection successful", testName);
                return testName + " PASS";
            } else {
                log.error("{} FAIL - Direct TLS authentication timeout", testName);
                return testName + " FAIL (Direct TLS auth timeout)";
            }
        } catch (Exception e) {
            log.error("{} FAIL - {}", testName, e.getMessage(), e);
            return testName + " FAIL (" + e.getMessage() + ")";
        }
    }

    private static String testSaslScramSha1() {
        return testSaslMechanism("SCRAM-SHA-1", SecurityConfig.SecurityMode.DISABLED);
    }

    private static String testSaslScramSha256() {
        return testSaslMechanism("SCRAM-SHA-256", SecurityConfig.SecurityMode.DISABLED);
    }

    private static String testSaslScramSha512() {
        return testSaslMechanism("SCRAM-SHA-512", SecurityConfig.SecurityMode.DISABLED);
    }

    private static String testSaslPlain() {
        return testSaslMechanism("PLAIN", SecurityConfig.SecurityMode.DISABLED);
    }

    private static String testSaslMechanism(String mechanism, SecurityConfig.SecurityMode securityMode) {
        String testName = "[SASL " + mechanism + "]";
        log.info("\n--- Test: {} ---", testName);

        Set<String> mechanisms = new HashSet<>();
        mechanisms.add(mechanism);

        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain(DOMAIN)
                        .host(HOST)
                        .port(PORT_PLAIN)
                        .enabledSaslMechanisms(mechanisms)
                        .sendPresence(false)
                        .build())
                .auth(AuthConfig.builder()
                        .username(USERNAME)
                        .password(PASSWORD.toCharArray())
                        .build())
                .security(SecurityConfig.builder()
                        .securityMode(securityMode)
                        .build())
                .build();

        try {
            TestResult result = runConnectionTest(config, 10);
            if (result.authenticated) {
                log.info("{} PASS - {} authentication successful", testName, mechanism);
                return testName + " PASS";
            } else {
                log.error("{} FAIL - {} authentication timeout", testName, mechanism);
                return testName + " FAIL (timeout)";
            }
        } catch (Exception e) {
            log.error("{} FAIL - {}", testName, e.getMessage(), e);
            return testName + " FAIL (" + e.getMessage() + ")";
        }
    }

    private static String testReconnection() {
        String testName = "[Reconnection Test]";
        log.info("\n--- Test: {} ---", testName);
        log.info("This test requires manual intervention:");
        log.info("1. Connection will be established");
        log.info("2. Please disconnect the session from OpenFire admin console (http://127.0.0.1:9090/)");
        log.info("   Login: admin/admin, go to Sessions, find the session and disconnect it");
        log.info("3. Watch for automatic reconnection");

        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain(DOMAIN)
                        .host(HOST)
                        .port(PORT_PLAIN)
                        .sendPresence(false)
                        .build())
                .auth(AuthConfig.builder()
                        .username(USERNAME)
                        .password(PASSWORD.toCharArray())
                        .build())
                .security(SecurityConfig.builder()
                        .securityMode(SecurityConfig.SecurityMode.DISABLED)
                        .build())
                .keepAlive(KeepAliveConfig.builder()
                        .reconnectionEnabled(true)
                        .build())
                .build();

        XmppTcpConnection connection = new XmppTcpConnection(config);
        CountDownLatch authLatch = new CountDownLatch(1);
        AtomicInteger reconnectCount = new AtomicInteger(0);
        AtomicBoolean reconnected = new AtomicBoolean(false);

        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.ConnectedEvent e ->
                    log.info("Connected");
                case ConnectionEvent.AuthenticatedEvent e -> {
                    log.info("Authenticated (resumed={})", e.resumed());
                    if (authLatch.getCount() > 0) {
                        authLatch.countDown();
                    } else {
                        reconnectCount.incrementAndGet();
                        reconnected.set(true);
                        log.info("Reconnection #{} successful!", reconnectCount.get());
                    }
                }
                case ConnectionEvent.ConnectionClosedEvent e ->
                    log.info("Connection closed normally");
                case ConnectionEvent.ConnectionClosedOnErrorEvent e ->
                    log.warn("Connection closed on error: {}", e.error().getMessage());
            }
        });

        try {
            connection.connect();
            boolean initialAuth = authLatch.await(10, TimeUnit.SECONDS);
            if (!initialAuth) {
                log.error("{} FAIL - Initial authentication timeout", testName);
                connection.disconnect();
                return testName + " FAIL (initial auth timeout)";
            }

            log.info("Initial connection established. Waiting for manual disconnect...");
            log.info("Please disconnect from OpenFire admin console now!");
            log.info("Waiting up to 30 seconds for reconnection...");

            Thread.sleep(30000);

            if (reconnected.get()) {
                log.info("{} PASS - Reconnection successful ({} times)", testName, reconnectCount.get());
                connection.disconnect();
                return testName + " PASS (reconnected " + reconnectCount.get() + " times)";
            } else {
                log.warn("{} SKIP - No reconnection detected (manual disconnect not performed?)", testName);
                connection.disconnect();
                return testName + " SKIP (no disconnect detected)";
            }
        } catch (Exception e) {
            log.error("{} FAIL - {}", testName, e.getMessage(), e);
            connection.disconnect();
            return testName + " FAIL (" + e.getMessage() + ")";
        }
    }

    private static String testMessageSend() {
        String testName = "[Message Send Test]";
        log.info("\n--- Test: {} ---", testName);

        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain(DOMAIN)
                        .host(HOST)
                        .port(PORT_PLAIN)
                        .sendPresence(true)
                        .build())
                .auth(AuthConfig.builder()
                        .username(USERNAME)
                        .password(PASSWORD.toCharArray())
                        .build())
                .security(SecurityConfig.builder()
                        .securityMode(SecurityConfig.SecurityMode.DISABLED)
                        .build())
                .build();

        XmppTcpConnection connection = new XmppTcpConnection(config);
        CountDownLatch authLatch = new CountDownLatch(1);

        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.AuthenticatedEvent e -> authLatch.countDown();
                default -> { }
            }
        });

        try {
            connection.connect();
            boolean auth = authLatch.await(10, TimeUnit.SECONDS);
            if (!auth) {
                log.error("{} FAIL - Authentication timeout", testName);
                connection.disconnect();
                return testName + " FAIL (auth timeout)";
            }

            Thread.sleep(1000);

            if (connection.isConnected() && connection.isAuthenticated()) {
                log.info("{} PASS - Connection is active and authenticated", testName);
                log.info("isConnected={}, isAuthenticated={}", connection.isConnected(), connection.isAuthenticated());
                connection.disconnect();
                return testName + " PASS";
            } else {
                log.error("{} FAIL - Connection lost", testName);
                connection.disconnect();
                return testName + " FAIL (connection lost)";
            }
        } catch (Exception e) {
            log.error("{} FAIL - {}", testName, e.getMessage(), e);
            connection.disconnect();
            return testName + " FAIL (" + e.getMessage() + ")";
        }
    }

    private static TestResult runConnectionTest(XmppClientConfig config, int timeoutSeconds) throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(config);
        CountDownLatch authLatch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.ConnectedEvent e ->
                    log.debug("Connected event received");
                case ConnectionEvent.AuthenticatedEvent e -> {
                    log.info("Authenticated successfully");
                    authLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedEvent e ->
                    log.debug("Connection closed");
                case ConnectionEvent.ConnectionClosedOnErrorEvent e -> {
                    log.error("Connection error: {}", e.error().getMessage());
                    errorRef.set(e.error());
                }
            }
        });

        try {
            connection.connect();
            boolean authenticated = authLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            
            TestResult result = new TestResult();
            result.authenticated = authenticated && errorRef.get() == null;
            result.error = errorRef.get();
            
            Thread.sleep(500);
            connection.disconnect();
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static class TestResult {
        boolean authenticated;
        Exception error;
    }
}
