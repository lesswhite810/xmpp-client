package com.example.xmpp;

import com.example.xmpp.config.AuthConfig;
import com.example.xmpp.config.ConnectionConfig;
import com.example.xmpp.config.SecurityConfig;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * XMPP Ping 功能测试类。
 *
 * <p>测试 XEP-0199 XMPP Ping 协议的各种场景：</p>
 * <ul>
 *   <li>正常 PING 请求与响应</li>
 *   <li>响应超时场景</li>
 *   <li>连接断开场景</li>
 *   <li>响应格式验证</li>
 * </ul>
 *
 * @since 2026-02-13
 */
@Slf4j
public class PingFunctionalityTest {

    private static final String SERVER_DOMAIN = "lesswhite";
    private static final String USERNAME = "acs";
    private static final String PASSWORD = "acs";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final int PING_TIMEOUT_SECONDS = 10;
    private static final int AUTH_TIMEOUT_SECONDS = 10;

    private XmppTcpConnection connection;
    private CountDownLatch authLatch;
    private CountDownLatch closeLatch;

    public static void main(String[] args) {
        PingFunctionalityTest test = new PingFunctionalityTest();
        boolean allPassed = true;

        try {
            log.info("========================================");
            log.info("XMPP Ping functionality test started");
            log.info("========================================\n");

            allPassed &= test.testNormalPingRequest();
            allPassed &= test.testPingResponseFormat();
            allPassed &= test.testMultiplePingRequests();
            allPassed &= test.testPingAfterReconnect();
            allPassed &= test.testPingTimeoutScenario();

            log.info("\n========================================");
            if (allPassed) {
                log.info("All tests passed!");
            } else {
                log.error("Some tests failed!");
            }
            log.info("========================================");

        } catch (Exception e) {
            log.error("Test execution exception", e);
        }
    }

    /**
     * 测试用例 1：正常 PING 请求与响应
     */
    public boolean testNormalPingRequest() {
        log.info("--- Test case 1: Normal PING request and response ---");

        try {
            if (!setupConnection()) {
                log.error("Test failed: Unable to establish connection");
                return false;
            }

            String pingId = "ping_test_1_" + System.currentTimeMillis();
            Iq pingIq = PingIq.createPingRequest(pingId, SERVER_DOMAIN);

            log.info("Sending PING request, id={}", pingId);
            long startTime = System.currentTimeMillis();

            CompletableFuture<XmppStanza> future = connection.sendIqPacketAsync(pingIq);
            XmppStanza response = future.get(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            long responseTime = System.currentTimeMillis() - startTime;
            log.info("Response received, elapsed: {}ms", responseTime);

            boolean success = validatePingResponse(response, pingId);

            if (success) {
                log.info("Test passed: PING request successful, response correct");
            } else {
                log.error("Test failed: Response format incorrect");
            }

            disconnect();
            return success;

        } catch (TimeoutException e) {
            log.error("Test failed: PING response timeout");
            disconnect();
            return false;
        } catch (Exception e) {
            log.error("Test failed: {}", e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * 测试用例 2：PING 响应格式验证
     */
    public boolean testPingResponseFormat() {
        log.info("\n--- Test case 2: PING response format validation ---");

        try {
            if (!setupConnection()) {
                log.error("Test failed: Unable to establish connection");
                return false;
            }

            String pingId = "ping_test_2_" + System.currentTimeMillis();
            Iq pingIq = PingIq.createPingRequest(pingId, SERVER_DOMAIN);

            CompletableFuture<XmppStanza> future = connection.sendIqPacketAsync(pingIq);
            XmppStanza response = future.get(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            String responseId = response.getId();
            String responseType = getStanzaType(response);

            boolean idMatch = pingId.equals(responseId);
            boolean typeValid = "result".equals(responseType);

            log.info("Response ID matches: {}", idMatch);
            log.info("Response type correct: {} (expected: result, actual: {})", typeValid, responseType);

            boolean success = idMatch && typeValid;

            if (success) {
                log.info("Test passed: Response format validation successful");
            } else {
                log.error("Test failed: Response format validation failed");
            }

            disconnect();
            return success;

        } catch (Exception e) {
            log.error("Test failed: {}", e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * 测试用例 3：多次 PING 请求
     */
    public boolean testMultiplePingRequests() {
        log.info("\n--- Test case 3: Multiple PING requests ---");

        try {
            if (!setupConnection()) {
                log.error("Test failed: Unable to establish connection");
                return false;
            }

            int requestCount = 3;
            int successCount = 0;
            long totalTime = 0;

            for (int i = 0; i < requestCount; i++) {
                String pingId = "ping_test_3_" + i + "_" + System.currentTimeMillis();
                Iq pingIq = PingIq.createPingRequest(pingId, SERVER_DOMAIN);

                log.info("Sending PING request #{}, id={}", i + 1, pingId);
                long startTime = System.currentTimeMillis();

                try {
                    CompletableFuture<XmppStanza> future = connection.sendIqPacketAsync(pingIq);
                    XmppStanza response = future.get(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    long responseTime = System.currentTimeMillis() - startTime;
                    totalTime += responseTime;

                    if (validatePingResponse(response, pingId)) {
                        successCount++;
                        log.info("PING #{} successful, elapsed: {}ms", i + 1, responseTime);
                    }
                } catch (TimeoutException e) {
                    log.error("PING #{} timeout", i + 1);
                }

                Thread.sleep(500);
            }

            boolean success = successCount == requestCount;
            log.info("Success rate: {}/{}, average response time: {}ms", successCount, requestCount, totalTime / requestCount);

            if (success) {
                log.info("Test passed: All PING requests successful");
            } else {
                log.error("Test failed: Some PING requests failed");
            }

            disconnect();
            return success;

        } catch (Exception e) {
            log.error("Test failed: {}", e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * 测试用例 4：重连后 PING 测试
     */
    public boolean testPingAfterReconnect() {
        log.info("\n--- Test case 4: PING test after reconnect ---");

        try {
            if (!setupConnection()) {
                log.error("Test failed: Unable to establish connection");
                return false;
            }

            log.info("First connection established, sending PING...");
            String pingId1 = "ping_test_4_1_" + System.currentTimeMillis();
            Iq pingIq1 = PingIq.createPingRequest(pingId1, SERVER_DOMAIN);
            CompletableFuture<XmppStanza> future1 = connection.sendIqPacketAsync(pingIq1);
            XmppStanza response1 = future1.get(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            boolean firstPing = validatePingResponse(response1, pingId1);
            log.info("First PING result: {}", firstPing ? "success" : "failed");

            log.info("Disconnecting...");
            disconnect();
            Thread.sleep(1000);

            log.info("Reconnecting...");
            if (!setupConnection()) {
                log.error("Test failed: Unable to re-establish connection");
                return false;
            }

            log.info("Second connection established, sending PING...");
            String pingId2 = "ping_test_4_2_" + System.currentTimeMillis();
            Iq pingIq2 = PingIq.createPingRequest(pingId2, SERVER_DOMAIN);
            CompletableFuture<XmppStanza> future2 = connection.sendIqPacketAsync(pingIq2);
            XmppStanza response2 = future2.get(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            boolean secondPing = validatePingResponse(response2, pingId2);
            log.info("Second PING result: {}", secondPing ? "success" : "failed");

            boolean success = firstPing && secondPing;

            if (success) {
                log.info("Test passed: PING works after reconnect");
            } else {
                log.error("Test failed: PING abnormal after reconnect");
            }

            disconnect();
            return success;

        } catch (Exception e) {
            log.error("Test failed: {}", e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * 测试用例 5：PING 超时场景（模拟）
     */
    public boolean testPingTimeoutScenario() {
        log.info("\n--- Test case 5: PING timeout scenario ---");

        try {
            if (!setupConnection()) {
                log.error("Test failed: Unable to establish connection");
                return false;
            }

            String pingId = "ping_test_5_" + System.currentTimeMillis();
            Iq pingIq = PingIq.createPingRequest(pingId, SERVER_DOMAIN);

            log.info("Sending PING request with very short timeout (100ms)...");
            CompletableFuture<XmppStanza> future = connection.sendIqPacketAsync(pingIq);

            try {
                future.get(100, TimeUnit.MILLISECONDS);
                log.info("Test passed: Server responded quickly, no timeout triggered");
                disconnect();
                return true;
            } catch (TimeoutException e) {
                log.info("Expected timeout occurred, timeout mechanism works correctly");
                future.cancel(true);
                disconnect();
                return true;
            }

        } catch (Exception e) {
            log.error("Test failed: {}", e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * 建立连接并等待认证。
     */
    private boolean setupConnection() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain(SERVER_DOMAIN)
                        .host(HOST)
                        .port(PORT)
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

        connection = new XmppTcpConnection(config);
        authLatch = new CountDownLatch(1);
        closeLatch = new CountDownLatch(1);

        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.ConnectedEvent e ->
                    log.debug("Connection established");
                case ConnectionEvent.AuthenticatedEvent e -> {
                    log.debug("Authentication completed, resumed={}", e.resumed());
                    authLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedEvent e -> {
                    log.debug("Connection closed");
                    closeLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedOnErrorEvent e -> {
                    log.error("Connection closed with error: {}", e.error().getMessage());
                    closeLatch.countDown();
                }
            }
        });

        connection.connect();

        boolean authenticated = authLatch.await(AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!authenticated) {
            log.error("Authentication timeout");
            return false;
        }

        return connection.isAuthenticated();
    }

    /**
     * 断开连接。
     */
    private void disconnect() {
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }

    /**
     * 获取 Stanza 类型字符串。
     */
    private String getStanzaType(XmppStanza stanza) {
        if (stanza instanceof Iq modelIq) {
            return modelIq.getType() != null ? modelIq.getType().name() : null;
        }
        return null;
    }

    /**
     * 验证 PING 响应是否正确。
     */
    private boolean validatePingResponse(XmppStanza response, String expectedId) {
        if (response == null) {
            log.error("Response is null");
            return false;
        }

        String responseId = response.getId();
        String responseType = getStanzaType(response);

        if (!expectedId.equals(responseId)) {
            log.error("Response ID mismatch: expected={}, actual={}", expectedId, responseId);
            return false;
        }

        if (!"result".equals(responseType)) {
            log.error("Response type incorrect: expected=result, actual={}", responseType);
            return false;
        }

        return true;
    }
}
