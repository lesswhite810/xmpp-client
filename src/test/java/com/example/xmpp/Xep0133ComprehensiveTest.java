package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * XEP-0133 Service Administration 综合测试
 */
public class Xep0133ComprehensiveTest extends AbstractRealServerTest {

    private static final Logger log = LoggerFactory.getLogger(Xep0133ComprehensiveTest.class);

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

    /**
     * 等待管理命令结果；如果服务器未实现该命令则跳过测试。
     *
     * @param commandName 管理命令名称
     * @param future 命令 Future
     * @return 成功响应
     * @throws Exception 非预期异常
     */
    private XmppStanza awaitOrSkipUnsupported(String commandName, CompletableFuture<XmppStanza> future)
            throws Exception {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof XmppStanzaErrorException see) {
                XmppError error = see.getXmppError();
                boolean unsupported = error != null && error.getCondition() == XmppError.Condition.ITEM_NOT_FOUND;
                assumeFalse(unsupported,
                        () -> String.format("Server does not support admin command '%s'", commandName));
            }
            if (cause instanceof AdminCommandException ace) {
                boolean unsupported = ace.getErrorCondition() == XmppError.Condition.ITEM_NOT_FOUND;
                assumeFalse(unsupported,
                        () -> String.format("Server does not support admin command '%s'", commandName));
            }
            throw e;
        }
    }
}
