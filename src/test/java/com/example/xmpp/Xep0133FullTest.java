package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XEP-0133 Service Administration 完整功能测试
 *
 * 测试流程：
 * 1. 使用管理员账户连接到 Openfire
 * 2. 创建测试用户
 * 3. 验证用户创建成功（通过查询用户列表）
 * 4. 删除测试用户
 * 5. 验证用户删除成功
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@RealServerTest
public class Xep0133FullTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;

    // 测试用户
    private static final String TEST_USERNAME = "testuser_xep0133";
    private static final String TEST_PASSWORD = "TestPass123!";
    private static final String TEST_JID = TEST_USERNAME + "@" + XMPP_DOMAIN;

    private static XmppTcpConnection connection;
    private static AdminManager adminManager;

    @BeforeAll
    public static void setUp() throws Exception {
        log.info("========================================");
        log.info("XEP-0133 Service Administration Full Test");
        log.info("========================================");

        // 创建不验证证书的 TrustManager（用于本地测试）
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .sendPresence(true)
                .username(ADMIN_USERNAME)
                .password(ADMIN_PASSWORD.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .customTrustManager(trustAllCerts)
                .build();

        connection = new XmppTcpConnection(config);
        adminManager = new AdminManager(connection, config);

        CountDownLatch authLatch = new CountDownLatch(1);

        XmppEventBus eventBus = XmppEventBus.getInstance();
        eventBus.subscribe(connection, ConnectionEventType.CONNECTED, e -> log.info("=== CONNECTED ==="));
        eventBus.subscribe(connection, ConnectionEventType.AUTHENTICATED, e -> {
            log.info("=== AUTHENTICATED as {} ===", ADMIN_USERNAME);
            authLatch.countDown();
        });
        eventBus.subscribe(connection, ConnectionEventType.ERROR, e -> {
            log.error("=== CONNECTION ERROR ===", e.error());
        });

        log.info("正在连接到 Openfire 服务器...");
        connection.connect();

        boolean authenticated = authLatch.await(15, TimeUnit.SECONDS);
        assertTrue(authenticated, "认证超时");
        assertTrue(connection.isAuthenticated(), "应该已认证");
        log.info("成功以管理员身份连接到服务器");
    }

    @AfterAll
    public static void tearDown() {
        if (connection != null && connection.isConnected()) {
            log.info("断开连接...");
            connection.disconnect();
            log.info("已断开连接");
        }
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: 清理环境 - 删除可能存在的测试用户")
    public void testCleanupEnvironment() throws Exception {
        log.info("\n--- Step 1: 清理环境（删除可能存在的测试用户）---");
        deleteUserQuietly(TEST_USERNAME);
        Thread.sleep(500);
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: 创建测试用户")
    public void testAddUser() throws Exception {
        log.info("\n--- Step 2: 创建测试用户 ---");
        boolean result = addUser(TEST_USERNAME, TEST_PASSWORD);
        log.info("创建用户结果: {}", result ? "成功" : "失败");
        assertTrue(result, "创建用户应该成功");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: 删除测试用户")
    public void testDeleteUser() throws Exception {
        log.info("\n--- Step 3: 删除测试用户 ---");
        boolean result = deleteUser(TEST_USERNAME);
        log.info("删除用户结果: {}", result ? "成功" : "失败");
        assertTrue(result, "删除用户应该成功");
    }

    // ==================== 辅助方法 ====================

    private boolean addUser(String username, String password) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        AtomicReference<Iq> responseRef = new AtomicReference<>();

        adminManager.addUser(username, password)
                .thenAccept(response -> {
                    logResponse("Add User", response);
                    if (response instanceof Iq iq) {
                        responseRef.set(iq);
                        result.set(iq.getType() == Iq.Type.RESULT);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Add user failed with exception: {}", ex.getMessage());
                    latch.countDown();
                    return null;
                });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        if (!completed) {
            log.error("Add user request timed out");
            return false;
        }

        // 打印响应详情
        Iq iq = responseRef.get();
        if (iq != null) {
            log.info("Add user response XML: {}", iq.toXml());
            ExtensionElement child = iq.getChildElement();
            if (child != null) {
                log.info("Child element: name={}, ns={}", child.getElementName(), child.getNamespace());
            }
        }

        return result.get();
    }

    private boolean deleteUser(String username) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        AtomicReference<Iq> responseRef = new AtomicReference<>();

        adminManager.deleteUser(username)
                .thenAccept(response -> {
                    logResponse("Delete User", response);
                    if (response instanceof Iq iq) {
                        responseRef.set(iq);
                        result.set(iq.getType() == Iq.Type.RESULT);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Delete user failed with exception: {}", ex.getMessage());
                    latch.countDown();
                    return null;
                });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        if (!completed) {
            log.error("Delete user request timed out");
            return false;
        }

        // 打印响应详情
        Iq iq = responseRef.get();
        if (iq != null) {
            log.info("Delete user response XML: {}", iq.toXml());
        }

        return result.get();
    }

    private void deleteUserQuietly(String username) {
        try {
            CountDownLatch latch = new CountDownLatch(1);

            adminManager.deleteUser(username)
                    .thenAccept(response -> {
                        if (response instanceof Iq iq) {
                            log.info("Quiet delete result: type={}", iq.getType());
                        }
                        latch.countDown();
                    })
                    .exceptionally(ex -> {
                        log.debug("Quiet delete failed (expected if user doesn't exist): {}", ex.getMessage());
                        latch.countDown();
                        return null;
                    });

            latch.await(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Quiet delete exception: {}", e.getMessage());
        }
    }

    private void logResponse(String operation, XmppStanza response) {
        if (response instanceof Iq iq) {
            log.info("{} response:", operation);
            log.info("  Type: {}", iq.getType());
            log.info("  ID: {}", iq.getId());
            log.info("  From: {}", iq.getFrom());
            log.info("  To: {}", iq.getTo());

            ExtensionElement child = iq.getChildElement();
            if (child != null) {
                log.info("  Child: {} (ns={})", child.getElementName(), child.getNamespace());
            }

            if (iq.getType() == Iq.Type.ERROR) {
                log.warn("  ERROR response received!");
                log.warn("  Full XML: {}", iq.toXml());
            }
        } else {
            log.info("{} response: {}", operation, response);
        }
    }
}
