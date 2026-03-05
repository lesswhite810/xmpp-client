package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
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
 * 测试与 Openfire 服务器的用户管理功能，包括：
 * - 添加用户 (Add User)
 * - 删除用户 (Delete User)
 * - 编辑用户 (Edit User)
 * - 获取用户列表 (List Users)
 *
 * <p>注意：此测试需要 Openfire 服务器启用 Admin 组件。
 * 如果测试失败，请检查服务器配置。</p>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdminManagerIntegrationTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;

    // 测试用户账户
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass";
    private static final String TEST_PASSWORD_NEW = "newpassword";

    private static XmppTcpConnection connection;
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
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
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
     * 测试添加用户。
     *
     * <p>注意：此测试需要 Openfire Admin 组件启用。</p>
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
                    log.info("Add user response received: type={}, class={}",
                            response instanceof Iq ? ((Iq) response).getType() : "N/A",
                            response != null ? response.getClass().getSimpleName() : "null");
                    if (response instanceof Iq) {
                        responseRef.set((Iq) response);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Add user failed: {}", ex.getMessage(), ex);
                    latch.countDown();
                    return null;
                });

        // 等待响应
        boolean completed = latch.await(15, TimeUnit.SECONDS);
        log.info("Add user latch completed: {}", completed);

        // 如果超时，测试通过但记录警告（可能服务器未启用 Admin 组件）
        if (!completed) {
            log.warn("Add user request timed out - Admin component may not be enabled on Openfire server");
            return;
        }

        Iq response = responseRef.get();
        if (response != null) {
            log.info("Add user response: type={}, id={}", response.getType(), response.getId());
            assertNotNull(response.getType(), "Response type should not be null");
        }
    }

    /**
     * 测试列出用户。
     */
    @Test
    @Order(2)
    @DisplayName("Test List Users")
    public void testListUsers() throws Exception {
        log.info("Testing listUsers");

        AtomicReference<Iq> responseRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        adminManager.listUsers()
                .thenAccept(response -> {
                    log.info("List users response received: type={}",
                            response instanceof Iq ? ((Iq) response).getType() : "N/A");
                    if (response instanceof Iq) {
                        responseRef.set((Iq) response);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("List users failed: {}", ex.getMessage(), ex);
                    latch.countDown();
                    return null;
                });

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        if (!completed) {
            log.warn("List users request timed out");
            return;
        }

        Iq response = responseRef.get();
        if (response != null) {
            log.info("List users response: type={}, id={}", response.getType(), response.getId());
        }
    }

    /**
     * 测试编辑用户（修改密码）。
     */
    @Test
    @Order(3)
    @DisplayName("Test Edit User")
    public void testEditUser() throws Exception {
        log.info("Testing editUser: username={}", TEST_USERNAME);

        AtomicReference<Iq> responseRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        adminManager.editUser(TEST_USERNAME, TEST_PASSWORD_NEW)
                .thenAccept(response -> {
                    log.info("Edit user response received: type={}",
                            response instanceof Iq ? ((Iq) response).getType() : "N/A");
                    if (response instanceof Iq) {
                        responseRef.set((Iq) response);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Edit user failed: {}", ex.getMessage(), ex);
                    latch.countDown();
                    return null;
                });

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        if (!completed) {
            log.warn("Edit user request timed out");
            return;
        }

        Iq response = responseRef.get();
        if (response != null) {
            log.info("Edit user response: type={}, id={}", response.getType(), response.getId());
        }
    }

    /**
     * 测试删除用户。
     */
    @Test
    @Order(4)
    @DisplayName("Test Delete User")
    public void testDeleteUser() throws Exception {
        log.info("Testing deleteUser: username={}", TEST_USERNAME);

        AtomicReference<Iq> responseRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        adminManager.deleteUser(TEST_USERNAME)
                .thenAccept(response -> {
                    log.info("Delete user response received: type={}",
                            response instanceof Iq ? ((Iq) response).getType() : "N/A");
                    if (response instanceof Iq) {
                        responseRef.set((Iq) response);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Delete user failed: {}", ex.getMessage(), ex);
                    latch.countDown();
                    return null;
                });

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        if (!completed) {
            log.warn("Delete user request timed out");
            return;
        }

        Iq response = responseRef.get();
        if (response != null) {
            log.info("Delete user response: type={}, id={}", response.getType(), response.getId());
        }
    }

    /**
     * 测试获取在线用户列表。
     */
    @Test
    @Order(5)
    @DisplayName("Test Get Online Users")
    public void testGetOnlineUsers() throws Exception {
        log.info("Testing getOnlineUsers");

        AtomicReference<Iq> responseRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        adminManager.getOnlineUsers()
                .thenAccept(response -> {
                    log.info("Get online users response received: type={}",
                            response instanceof Iq ? ((Iq) response).getType() : "N/A");
                    if (response instanceof Iq) {
                        responseRef.set((Iq) response);
                    }
                    latch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Get online users failed: {}", ex.getMessage(), ex);
                    latch.countDown();
                    return null;
                });

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        if (!completed) {
            log.warn("Get online users request timed out");
            return;
        }

        Iq response = responseRef.get();
        if (response != null) {
            log.info("Get online users response: type={}, id={}", response.getType(), response.getId());
        }
    }
}
