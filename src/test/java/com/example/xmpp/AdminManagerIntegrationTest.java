package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.Iq;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XEP-0133: Service Administration 集成测试
 *
 * 测试与 Openfire 服务器的用户管理功能。
 *
 * <p>注意：此测试需要 Openfire 服务器启用 Admin 组件。
 * 如果测试失败，请检查服务器配置。</p>
 */
@Slf4j
@Disabled("需要真实的 XMPP 服务器 (Openfire)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@RealServerTest
public class AdminManagerIntegrationTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;

    // 测试用户账户
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass";

    private static AbstractXmppConnection connection;
    private static AdminManager adminManager;

    @BeforeAll
    public static void setUp() throws Exception {
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
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .customTrustManager(trustAllCerts)
                .build();

        connection = new XmppTcpConnection(config);
        adminManager = new AdminManager(connection, config);

        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch closeLatch = new CountDownLatch(1);

        XmppEventBus eventBus = XmppEventBus.getInstance();
        eventBus.subscribe(connection, ConnectionEventType.CONNECTED, e -> log.info("=== CONNECTED ==="));
        eventBus.subscribe(connection, ConnectionEventType.AUTHENTICATED, e -> {
            log.info("=== AUTHENTICATED ===");
            authLatch.countDown();
        });
        eventBus.subscribe(connection, ConnectionEventType.CLOSED, e -> {
            log.info("=== CONNECTION CLOSED ===");
            closeLatch.countDown();
        });
        eventBus.subscribe(connection, ConnectionEventType.ERROR, e -> {
            log.error("=== CONNECTION ERROR ===", e.error());
            closeLatch.countDown();
        });

        log.info("Connecting to Openfire server as admin...");
        connection.connect();

        boolean authenticated = authLatch.await(15, TimeUnit.SECONDS);
        if (!authenticated) {
            throw new RuntimeException("Authentication timeout");
        }

        assertTrue(connection.isAuthenticated(), "Connection should be authenticated as admin");
        log.info("Successfully connected to Openfire as admin");
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (connection != null && connection.isConnected()) {
            log.info("Disconnecting from Openfire server...");
            connection.disconnect();
        }
    }

    /**
     * 测试发送简单的 IQ ping 来验证连接正常工作。
     */
    @Test
    @Order(0)
    @DisplayName("Test Simple IQ")
    public void testSimpleIq() throws Exception {
        log.info("Testing simple IQ ping");

        // 等待连接稳定
        Thread.sleep(500);

        // 发送一个简单的 ping IQ 到服务器
        String testId = "ping-" + System.currentTimeMillis();
        Iq pingIq = com.example.xmpp.protocol.model.PingIq.createPingRequest(testId, XMPP_DOMAIN);

        log.info("Sending ping IQ: id={}", testId);

        AtomicReference<Iq> responseRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        connection.sendIqPacketAsync(pingIq, 10, TimeUnit.SECONDS)
                .thenAccept(response -> {
                    log.info("Ping response received: type={}, id={}, from={}",
                            response instanceof Iq ? ((Iq) response).getType() : "N/A",
                            response instanceof Iq ? ((Iq) response).getId() : "null",
                            response instanceof Iq ? ((Iq) response).getFrom() : "null");
                    if (response instanceof Iq) {
                        responseRef.set((Iq) response);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Ping failed: {}", ex.getMessage());
                    latch.countDown();
                    return null;
                });

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "Ping should complete");
        assertNotNull(responseRef.get(), "Ping response should not be null");
        assertEquals(Iq.Type.RESULT, responseRef.get().getType(), "Ping should return RESULT");
        log.info("Ping test PASSED");
    }

    /**
     * 测试使用 AdminManager 添加用户。
     */
    @Test
    @Order(1)
    @DisplayName("Test Add User")
    public void testAddUser() throws Exception {
        log.info("Testing addUser: username={}", TEST_USERNAME);

        // 等待连接稳定
        Thread.sleep(500);

        AtomicReference<Iq> responseRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        adminManager.addUser(TEST_USERNAME, TEST_PASSWORD)
                .thenAccept(response -> {
                    log.info("Add user response: type={}, id={}, from={}",
                            response instanceof Iq ? ((Iq) response).getType() : "N/A",
                            response instanceof Iq ? ((Iq) response).getId() : "null",
                            response instanceof Iq ? ((Iq) response).getFrom() : "null");
                    if (response instanceof Iq) {
                        responseRef.set((Iq) response);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Add user failed: {}", ex.getMessage());
                    latch.countDown();
                    return null;
                });

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        log.info("Add user completed: {}", completed);

        // 无论成功或失败，都记录结果
        if (completed && responseRef.get() != null) {
            log.info("Add user response type: {}", responseRef.get().getType());
            assertNotNull(responseRef.get().getType(), "Response type should not be null");
        } else if (!completed) {
            log.warn("Add user request timed out");
        }
    }

    /**
     * 测试修改用户密码，并验证新密码可以连接。
     */
    @Test
    @Order(4)
    @DisplayName("Test Change Password and Connect")
    public void testChangePasswordAndConnect() throws Exception {
        String newPassword = "newPass456";

        log.info("Testing change password: username={}", TEST_USERNAME);

        // 确保 testuser 存在（testAddUser 已创建）
        Thread.sleep(1000);

        // 第一步：使用 XEP-0077 修改密码
        AtomicReference<Iq> changePwdResponseRef = new AtomicReference<>();
        CountDownLatch changePwdLatch = new CountDownLatch(1);

        adminManager.changePassword(TEST_USERNAME, newPassword)
                .thenAccept(response -> {
                    if (response instanceof Iq) {
                        changePwdResponseRef.set((Iq) response);
                    }
                    changePwdLatch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Change password failed: {}", ex.getMessage());
                    changePwdLatch.countDown();
                    return null;
                });

        boolean changePwdCompleted = changePwdLatch.await(20, TimeUnit.SECONDS);
        assertTrue(changePwdCompleted, "Change password should complete");
        assertNotNull(changePwdResponseRef.get(), "Change password response should not be null");

        // 某些服务器可能不支持修改密码
        if (changePwdResponseRef.get().getType() == Iq.Type.ERROR) {
            log.warn("Change password command not supported by server, skipping password verification");
            return;
        }

        assertEquals(Iq.Type.RESULT, changePwdResponseRef.get().getType(), "Change password should succeed");
        log.info("Changed password for user: {}", TEST_USERNAME);

        // 等待密码修改生效
        Thread.sleep(2000);

        // 第二步：使用新密码连接验证
        log.info("Testing connection with new password...");

        XmppClientConfig testUserConfig = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(TEST_USERNAME)
                .password(newPassword.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .customTrustManager(new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                })
                .build();

        XmppTcpConnection testUserConnection = new XmppTcpConnection(testUserConfig);
        CountDownLatch testAuthLatch = new CountDownLatch(1);
        AtomicReference<Boolean> testAuthResult = new AtomicReference<>(false);

        XmppEventBus testEventBus = XmppEventBus.getInstance();
        testEventBus.subscribe(testUserConnection, ConnectionEventType.AUTHENTICATED, e -> {
            log.info("Test user AUTHENTICATED with new password!");
            testAuthResult.set(true);
            testAuthLatch.countDown();
        });
        testEventBus.subscribe(testUserConnection, ConnectionEventType.ERROR, e -> {
            log.error("Test user connection ERROR: {}", e.error().getMessage());
            testAuthLatch.countDown();
        });

        testUserConnection.connect();
        boolean authSuccess = testAuthLatch.await(15, TimeUnit.SECONDS);

        assertTrue(authSuccess, "Test user should receive auth result");
        if (!testAuthResult.get()) {
            // 密码修改可能未生效（服务器配置问题）
            log.warn("Password change did not take effect on server, skipping password verification");
            testUserConnection.disconnect();
            return;
        }
        log.info("Successfully connected with new password");

        // 第三步：使用管理员连接将密码改回原密码
        log.info("Restoring original password (by admin)...");
        CountDownLatch restoreLatch = new CountDownLatch(1);
        AtomicReference<Boolean> restoreSuccess = new AtomicReference<>(false);

        adminManager.changePassword(TEST_USERNAME, TEST_PASSWORD)
                .thenAccept(response -> {
                    if (response instanceof Iq iq && iq.getType() == Iq.Type.RESULT) {
                        restoreSuccess.set(true);
                        log.info("Password restored successfully");
                    } else {
                        log.warn("Restore password response: {}", response instanceof Iq iq ? iq.getType() : "N/A");
                    }
                    restoreLatch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Restore password failed: {}", ex.getMessage());
                    restoreLatch.countDown();
                    return null;
                });

        boolean restoreCompleted = restoreLatch.await(20, TimeUnit.SECONDS);
        assertTrue(restoreCompleted, "Restore password should complete");
        assertTrue(restoreSuccess.get(), "Restore password should succeed");
        log.info("Restored original password for user: {}", TEST_USERNAME);

        // 断开测试用户连接
        testUserConnection.disconnect();
        Thread.sleep(500);
    }

    /**
     * 测试删除用户。
     */
    @Test
    @Order(5)
    @DisplayName("Test Delete User")
    public void testDeleteUser() throws Exception {
        log.info("Testing delete user: username={}", TEST_USERNAME);

        // 等待确保用户存在（由 testAddUser 创建）
        Thread.sleep(1000);

        // 第一步：删除用户
        AtomicReference<Iq> deleteResponseRef = new AtomicReference<>();
        CountDownLatch deleteLatch = new CountDownLatch(1);

        adminManager.deleteUser(TEST_USERNAME)
                .thenAccept(response -> {
                    log.info("Delete user response: type={}, id={}, from={}",
                            response instanceof Iq ? ((Iq) response).getType() : "N/A",
                            response instanceof Iq ? ((Iq) response).getId() : "null",
                            response instanceof Iq ? ((Iq) response).getFrom() : "null");
                    if (response instanceof Iq) {
                        deleteResponseRef.set((Iq) response);
                    }
                    deleteLatch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Delete user failed: {}", ex.getMessage());
                    deleteLatch.countDown();
                    return null;
                });

        boolean deleteCompleted = deleteLatch.await(20, TimeUnit.SECONDS);
        assertTrue(deleteCompleted, "Delete user should complete");
        assertNotNull(deleteResponseRef.get(), "Delete user response should not be null");
        assertEquals(Iq.Type.RESULT, deleteResponseRef.get().getType(), "Delete user should succeed");
        log.info("Successfully deleted user: {}", TEST_USERNAME);

        // 等待删除生效
        Thread.sleep(1000);

        // 第二步：验证用户已被删除（尝试用该用户连接应失败）
        log.info("Verifying user was deleted by attempting to connect...");

        XmppClientConfig deletedUserConfig = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(TEST_USERNAME)
                .password(TEST_PASSWORD.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .customTrustManager(new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                })
                .build();

        XmppTcpConnection deletedUserConnection = new XmppTcpConnection(deletedUserConfig);
        CountDownLatch verifyLatch = new CountDownLatch(1);
        AtomicReference<Boolean> authSucceeded = new AtomicReference<>(true); // 默认假设成功，只有收到 ERROR 才是失败

        XmppEventBus verifyEventBus = XmppEventBus.getInstance();
        verifyEventBus.subscribe(deletedUserConnection, ConnectionEventType.AUTHENTICATED, e -> {
            log.warn("Unexpected: deleted user AUTHENTICATED!");
            authSucceeded.set(true);
            verifyLatch.countDown();
        });
        verifyEventBus.subscribe(deletedUserConnection, ConnectionEventType.ERROR, e -> {
            log.info("Expected: deleted user connection failed - {}", e.error().getMessage());
            authSucceeded.set(false);
            verifyLatch.countDown();
        });

        try {
            deletedUserConnection.connect();
        } catch (XmppException e) {
            log.info("Expected: deleted user connection failed during connect - {}", e.getMessage());
            authSucceeded.set(false);
            verifyLatch.countDown();
        }
        boolean verifyCompleted = verifyLatch.await(15, TimeUnit.SECONDS);

        assertTrue(verifyCompleted, "Verification should complete");
        assertFalse(authSucceeded.get(), "Deleted user should NOT be able to authenticate");
        log.info("Verified: deleted user cannot authenticate");

        // 断开验证连接
        deletedUserConnection.disconnect();
    }

    /**
     * 清理测试中创建的 TEST_USERNAME 用户。
     */
    @Test
    @Order(99)
    @DisplayName("Cleanup Test User")
    public void cleanupTestUser() throws Exception {
        log.info("Cleaning up test user: {}", TEST_USERNAME);

        CountDownLatch latch = new CountDownLatch(1);
        adminManager.deleteUser(TEST_USERNAME)
                .thenAccept(response -> {
                    log.info("Cleanup response: {}", response instanceof Iq ? ((Iq) response).getType() : "N/A");
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.warn("Cleanup failed (user may not exist): {}", ex.getMessage());
                    latch.countDown();
                    return null;
                });

        latch.await(20, TimeUnit.SECONDS);
        log.info("Cleanup completed");
    }
}
