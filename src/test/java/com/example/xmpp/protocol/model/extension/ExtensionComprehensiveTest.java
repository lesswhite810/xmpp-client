package com.example.xmpp.protocol.model.extension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extension 模型类单元测试 - 补充测试。
 * 覆盖所有 extension 类的 toXml() 方法分支。
 */
class ExtensionComprehensiveTest {

    @Nested
    @DisplayName("AbstractAdminCommand 测试")
    class AbstractAdminCommandTests {

        @Test
        @DisplayName("appendHiddenField 在 var 为 null 时应抛出异常")
        void testAppendHiddenFieldShouldRejectNullVar() {
            TestAdminCommand command = new TestAdminCommand();

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> command.appendHiddenField(new com.example.xmpp.util.XmlStringBuilder(), null, "value"));

            assertEquals("var must not be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("appendHiddenField 在 var 为空白时应抛出异常")
        void testAppendHiddenFieldShouldRejectBlankVar() {
            TestAdminCommand command = new TestAdminCommand();

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> command.appendHiddenField(new com.example.xmpp.util.XmlStringBuilder(), "   ", "value"));

            assertEquals("var must not be null or blank", exception.getMessage());
        }

        private static final class TestAdminCommand extends AbstractAdminCommand {

            @Override
            protected String getCommandNode() {
                return "test:node";
            }

            @Override
            protected void appendFields(com.example.xmpp.util.XmlStringBuilder xml) {
                // no-op
            }
        }
    }

    @Nested
    @DisplayName("AddUser 测试")
    class AddUserTests {

        @Test
        @DisplayName("AddUser 默认构造函数应设置 ACTION_COMPLETE")
        void testAddUserDefaultConstructor() {
            AddUser addUser = new AddUser();
            assertEquals(AddUser.ACTION_COMPLETE, addUser.getAction());
        }

        @Test
        @DisplayName("AddUser 构造函数应正确设置属性")
        void testAddUserConstructor() {
            AddUser addUser = new AddUser("testuser", "password");
            assertEquals("testuser", addUser.getUsername());
            assertEquals("password", addUser.getPassword());
            assertEquals(AddUser.ACTION_COMPLETE, addUser.getAction());
        }

        @Test
        @DisplayName("AddUser 构造函数应支持邮箱")
        void testAddUserConstructorWithEmail() {
            AddUser addUser = new AddUser("testuser", "password", "test@example.com");
            assertEquals("testuser", addUser.getUsername());
            assertEquals("password", addUser.getPassword());
            assertEquals("test@example.com", addUser.getEmail());
        }

        @Test
        @DisplayName("AddUser createExecuteCommand 应返回 execute 动作")
        void testAddUserCreateExecuteCommand() {
            AddUser addUser = AddUser.createExecuteCommand();
            assertEquals(AddUser.ACTION_EXECUTE, addUser.getAction());
        }

        @Test
        @DisplayName("AddUser createSubmitForm 应正确设置所有属性")
        void testAddUserCreateSubmitForm() {
            AddUser addUser = AddUser.createSubmitForm("session-123", "testuser", "password", "test@example.com");
            assertEquals("session-123", addUser.getSessionId());
            assertEquals("testuser", addUser.getUsername());
            assertEquals("password", addUser.getPassword());
            assertEquals("test@example.com", addUser.getEmail());
            assertEquals(AddUser.ACTION_COMPLETE, addUser.getAction());
        }

        @Test
        @DisplayName("AddUser.toXml execute 动作应生成简单 XML")
        void testAddUserToXmlExecute() {
            AddUser addUser = AddUser.createExecuteCommand();
            String xml = addUser.toXml();

            assertTrue(xml.contains("<command xmlns=\"http://jabber.org/protocol/commands\" node=\"http://jabber.org/protocol/admin#add-user\" action=\"execute\""));
            assertTrue(xml.contains("/>") || xml.contains("</command>"));
            assertFalse(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
        }

        @Test
        @DisplayName("AddUser.toXml complete 动作应生成完整 XML")
        void testAddUserToXmlComplete() {
            AddUser addUser = new AddUser("testuser", "password");
            String xml = addUser.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertTrue(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
            assertTrue(xml.contains("<field var=\"accountjid\"><value>testuser</value></field>"));
            assertTrue(xml.contains("<field var=\"password\"><value>password</value></field>"));
        }

        @Test
        @DisplayName("AddUser.toXml 应包含邮箱字段")
        void testAddUserToXmlWithEmail() {
            AddUser addUser = new AddUser("testuser", "password", "test@example.com");
            String xml = addUser.toXml();

            assertTrue(xml.contains("<field var=\"email\"><value>test@example.com</value></field>"));
        }

        @Test
        @DisplayName("AddUser.toXml 应包含 sessionid")
        void testAddUserToXmlWithSessionId() {
            AddUser addUser = AddUser.createSubmitForm("session-123", "testuser", "password", null);
            String xml = addUser.toXml();

            assertTrue(xml.contains("sessionid=\"session-123\""));
        }

        @Test
        @DisplayName("AddUser.getElementName 应返回 command")
        void testAddUserGetElementName() {
            AddUser addUser = new AddUser();
            assertEquals("command", addUser.getElementName());
        }

        @Test
        @DisplayName("AddUser.getNamespace 应返回 Commands 命名空间")
        void testAddUserGetNamespace() {
            AddUser addUser = new AddUser();
            assertEquals(AddUser.NAMESPACE, addUser.getNamespace());
        }
    }

    @Nested
    @DisplayName("ChangeUserPassword 测试")
    class ChangeUserPasswordTests {

        @Test
        @DisplayName("ChangeUserPassword 默认构造函数应设置 ACTION_COMPLETE")
        void testChangeUserPasswordDefaultConstructor() {
            ChangeUserPassword cmd = new ChangeUserPassword();
            assertEquals(ChangeUserPassword.ACTION_COMPLETE, cmd.getAction());
        }

        @Test
        @DisplayName("ChangeUserPassword 构造函数应正确设置属性")
        void testChangeUserPasswordConstructor() {
            ChangeUserPassword cmd = new ChangeUserPassword("user@domain.com", "newpass");
            assertEquals("user@domain.com", cmd.getAccountJid());
            assertEquals("newpass", cmd.getNewPassword());
        }

        @Test
        @DisplayName("ChangeUserPassword createExecuteCommand 应返回 execute 动作")
        void testChangeUserPasswordCreateExecuteCommand() {
            ChangeUserPassword cmd = ChangeUserPassword.createExecuteCommand();
            assertEquals(ChangeUserPassword.ACTION_EXECUTE, cmd.getAction());
        }

        @Test
        @DisplayName("ChangeUserPassword.toXml execute 动作应生成简单 XML")
        void testChangeUserPasswordToXmlExecute() {
            ChangeUserPassword cmd = ChangeUserPassword.createExecuteCommand();
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"execute\""));
            assertFalse(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
        }

        @Test
        @DisplayName("ChangeUserPassword.toXml complete 动作应生成完整 XML")
        void testChangeUserPasswordToXmlComplete() {
            ChangeUserPassword cmd = new ChangeUserPassword("user@domain.com", "newpass");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertTrue(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
            assertTrue(xml.contains("<field var=\"accountjid\"><value>user@domain.com</value></field>"));
            assertTrue(xml.contains("<field var=\"password\"><value>newpass</value></field>"));
        }

        @Test
        @DisplayName("ChangeUserPassword.toXml 应包含 sessionid")
        void testChangeUserPasswordToXmlWithSessionId() {
            ChangeUserPassword cmd = ChangeUserPassword.createSubmitForm("session-456", "user@domain.com", "newpass");
            String xml = cmd.toXml();

            assertTrue(xml.contains("sessionid=\"session-456\""));
        }

        @Test
        @DisplayName("ChangeUserPassword.getElementName 应返回 command")
        void testChangeUserPasswordGetElementName() {
            ChangeUserPassword cmd = new ChangeUserPassword();
            assertEquals("command", cmd.getElementName());
        }
    }

    @Nested
    @DisplayName("DeleteUser 测试")
    class DeleteUserTests {

        @Test
        @DisplayName("DeleteUser 默认构造函数应设置 ACTION_COMPLETE")
        void testDeleteUserDefaultConstructor() {
            DeleteUser cmd = new DeleteUser();
            assertEquals(DeleteUser.ACTION_COMPLETE, cmd.getAction());
        }

        @Test
        @DisplayName("DeleteUser 构造函数应正确设置属性")
        void testDeleteUserConstructor() {
            DeleteUser cmd = new DeleteUser("testuser@domain.com");
            assertEquals("testuser@domain.com", cmd.getAccountJid());
        }

        @Test
        @DisplayName("DeleteUser createExecuteCommand 应返回 execute 动作")
        void testDeleteUserCreateExecuteCommand() {
            DeleteUser cmd = DeleteUser.createExecuteCommand();
            assertEquals(DeleteUser.ACTION_EXECUTE, cmd.getAction());
        }

        @Test
        @DisplayName("DeleteUser.toXml execute 动作应生成简单 XML")
        void testDeleteUserToXmlExecute() {
            DeleteUser cmd = DeleteUser.createExecuteCommand();
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"execute\""));
            assertFalse(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
        }

        @Test
        @DisplayName("DeleteUser.toXml complete 动作应生成完整 XML")
        void testDeleteUserToXmlComplete() {
            DeleteUser cmd = new DeleteUser("testuser@domain.com");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertTrue(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
            assertTrue(xml.contains("testuser@domain.com"));
        }

        @Test
        @DisplayName("DeleteUser.toXml 应包含 sessionid")
        void testDeleteUserToXmlWithSessionId() {
            DeleteUser cmd = DeleteUser.createSubmitForm("session-789", "testuser@domain.com");
            String xml = cmd.toXml();

            assertTrue(xml.contains("sessionid=\"session-789\""));
        }

        @Test
        @DisplayName("DeleteUser.getElementName 应返回 command")
        void testDeleteUserGetElementName() {
            DeleteUser cmd = new DeleteUser();
            assertEquals("command", cmd.getElementName());
        }
    }

    @Nested
    @DisplayName("ConnectionRequest 测试补充")
    class ConnectionRequestAdditionalTests {

        @Test
        @DisplayName("ConnectionRequest.toXml 应生成有效 XML")
        void testConnectionRequestToXml() {
            ConnectionRequest request = ConnectionRequest.builder()
                    .username("device-123")
                    .password("secret")
                    .build();

            String xml = request.toXml();
            assertTrue(xml.contains("<connectionRequest"));
            assertTrue(xml.contains("device-123"));
            assertTrue(xml.contains("secret"));
            assertTrue(xml.contains("</connectionRequest>"));
        }

        @Test
        @DisplayName("ConnectionRequest.toXml 应处理空属性")
        void testConnectionRequestToXmlEmpty() {
            ConnectionRequest request = ConnectionRequest.builder().build();

            String xml = request.toXml();
            assertTrue(xml.contains("<connectionRequest"));
            assertTrue(xml.contains("/>") || xml.contains("</connectionRequest>"));
        }
    }

    @Nested
    @DisplayName("Bind 测试补充")
    class BindAdditionalTests {

        @Test
        @DisplayName("Bind.toXml 应生成有效 XML")
        void testBindToXml() {
            Bind bind = Bind.builder()
                    .jid("user@example.com/resource")
                    .resource("mobile")
                    .build();

            String xml = bind.toXml();
            assertTrue(xml.contains("<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">"));
            assertTrue(xml.contains("<jid>user@example.com/resource</jid>"));
            assertTrue(xml.contains("<resource>mobile</resource>"));
            assertTrue(xml.contains("</bind>"));
        }

        @Test
        @DisplayName("Bind.toXml 应处理只有 jid")
        void testBindToXmlJidOnly() {
            Bind bind = Bind.builder()
                    .jid("user@example.com/resource")
                    .build();

            String xml = bind.toXml();
            assertTrue(xml.contains("<jid>user@example.com/resource</jid>"));
            assertFalse(xml.contains("<resource>"));
        }

        @Test
        @DisplayName("Bind.toXml 应处理只有 resource")
        void testBindToXmlResourceOnly() {
            Bind bind = Bind.builder()
                    .resource("mobile")
                    .build();

            String xml = bind.toXml();
            assertTrue(xml.contains("<resource>mobile</resource>"));
            assertFalse(xml.contains("<jid>"));
        }

        @Test
        @DisplayName("Bind.toXml 应处理空值")
        void testBindToXmlEmpty() {
            Bind bind = Bind.builder().build();

            String xml = bind.toXml();
            assertTrue(xml.contains("<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\""));
            assertTrue(xml.contains("/>") || xml.contains("</bind>"));
        }
    }
}
