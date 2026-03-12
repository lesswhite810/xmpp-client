package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * XEP-0133 Service Administration 综合测试
 */
@Slf4j
public class Xep0133ComprehensiveTest {

    private XmppTcpConnection connection;
    private AdminManager adminManager;

    @BeforeEach
    void setUp() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("lesswhite")
                .host("localhost")
                .port(5222)
                .username("admin")
                .password("admin".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();

        connection = new XmppTcpConnection(config);
        connection.connect();

        // 等待连接完成
        Thread.sleep(2000);

        adminManager = new AdminManager(connection, config);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    @Test
    void testAddUser() throws Exception {
        String username = "testuser_" + System.currentTimeMillis();
        String password = "testpass123";

        log.info("=== Testing Add User: {} ===", username);

        XmppStanza response = adminManager.addUser(username, password, "test@example.com")
                .get(20, TimeUnit.SECONDS);

        assertNotNull(response);
        assertTrue(response instanceof Iq);
        Iq iq = (Iq) response;
        assertEquals(Iq.Type.RESULT, iq.getType(), "Add user should succeed");
        log.info("Add user test passed");
    }

    @Test
    void testDeleteUser() throws Exception {
        // 先创建一个用户
        String username = "delete_test_" + System.currentTimeMillis();
        String password = "testpass123";

        log.info("=== Testing Delete User ===");

        // 先添加用户
        adminManager.addUser(username, password).get(20, TimeUnit.SECONDS);
        log.info("Created test user: {}", username);

        // 然后删除
        XmppStanza response = adminManager.deleteUser(username).get(20, TimeUnit.SECONDS);

        assertNotNull(response);
        assertTrue(response instanceof Iq);
        Iq iq = (Iq) response;
        assertEquals(Iq.Type.RESULT, iq.getType(), "Delete user should succeed");
        log.info("Delete user test passed");
    }

    @Test
    void testListUsers() throws Exception {
        log.info("=== Testing List Users ===");

        XmppStanza response = adminManager.listUsers().get(20, TimeUnit.SECONDS);

        assertNotNull(response);
        assertTrue(response instanceof Iq);
        Iq iq = (Iq) response;
        assertEquals(Iq.Type.RESULT, iq.getType(), "List users should succeed");

        // 解析用户列表
        ExtensionElement childElement = iq.getChildElement();
        if (childElement instanceof GenericExtensionElement genericElement) {
            log.info("Response element: {}", genericElement.getElementName());
            // 检查是否有用户数据
            List<GenericExtensionElement> children = genericElement.getChildren();
            if (children != null) {
                log.info("Found {} child elements", children.size());
                for (GenericExtensionElement child : children) {
                    log.info("  - {}: {}", child.getElementName(), child.getText());
                }
            }
        }

        log.info("List users test passed");
    }

    @Test
    void testGetOnlineUsers() throws Exception {
        log.info("=== Testing Get Online Users ===");

        XmppStanza response = awaitOrSkipUnsupported(
                "get-online-users",
                adminManager.getOnlineUsers());

        assertNotNull(response);
        assertTrue(response instanceof Iq);
        Iq iq = (Iq) response;
        assertEquals(Iq.Type.RESULT, iq.getType(), "Get online users should succeed");

        log.info("Get online users test passed");
    }

    @Test
    void testEditUser() throws Exception {
        // 先创建一个用户
        String username = "edit_test_" + System.currentTimeMillis();
        String password = "oldpass123";
        String newPassword = "newpass456";

        log.info("=== Testing Edit User ===");

        // 先添加用户
        adminManager.addUser(username, password).get(20, TimeUnit.SECONDS);
        log.info("Created test user: {}", username);

        XmppStanza response;
        try {
            response = awaitOrSkipUnsupported(
                    "edit-user",
                    adminManager.editUser(username, newPassword, "newemail@example.com"));
        } finally {
            adminManager.deleteUser(username).get(20, TimeUnit.SECONDS);
        }

        assertNotNull(response);
        assertTrue(response instanceof Iq);
        Iq iq = (Iq) response;
        assertEquals(Iq.Type.RESULT, iq.getType(), "Edit user should succeed");
        log.info("Edit user test passed");
    }

    @Test
    void testGetUser() throws Exception {
        // 先创建一个用户
        String username = "get_test_" + System.currentTimeMillis();
        String password = "testpass123";

        log.info("=== Testing Get User ===");

        // 先添加用户
        adminManager.addUser(username, password).get(20, TimeUnit.SECONDS);

        XmppStanza response;
        try {
            response = awaitOrSkipUnsupported("get-user", adminManager.getUser(username));
        } finally {
            adminManager.deleteUser(username).get(20, TimeUnit.SECONDS);
        }

        assertNotNull(response);
        assertTrue(response instanceof Iq);
        Iq iq = (Iq) response;
        assertEquals(Iq.Type.RESULT, iq.getType(), "Get user should succeed");

        log.info("Get user test passed");
    }

    /**
     * 等待管理命令结果；如果服务器未实现该命令则跳过测试。
     *
     * @param commandName 管理命令名称
     * @param future 命令 Future
     * @return 成功响应
     * @throws Exception 非预期异常
     */
    private XmppStanza awaitOrSkipUnsupported(String commandName, java.util.concurrent.CompletableFuture<XmppStanza> future)
            throws Exception {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AdminCommandException ace && ace.hasErrorResponse()) {
                Iq errorIq = ace.getErrorResponse();
                XmppError error = errorIq.getError();
                boolean unsupported = error != null && error.getCondition() == XmppError.Condition.item_not_found;
                assumeFalse(unsupported,
                        () -> String.format("Server does not support admin command '%s': %s", commandName, errorIq.toXml()));
            }
            throw e;
        }
    }
}
