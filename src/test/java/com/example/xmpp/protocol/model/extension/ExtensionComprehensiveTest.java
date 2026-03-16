package com.example.xmpp.protocol.model.extension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extension 模型类单元测试 - 补充测试。
 * 覆盖所有 extension 类的 toXml() 方法分支。
 */
class ExtensionComprehensiveTest {

    @Nested
    @DisplayName("EditUser 测试")
    class EditUserTests {

        @Test
        @DisplayName("EditUser 默认构造函数应设置 ACTION_SUBMIT")
        void testEditUserDefaultConstructor() {
            EditUser cmd = new EditUser();
            assertEquals(EditUser.ACTION_SUBMIT, cmd.getAction());
        }

        @Test
        @DisplayName("EditUser 单参数构造函数应设置 accountJid")
        void testEditUserSingleArgConstructor() {
            EditUser cmd = new EditUser("user@domain.com");
            assertEquals("user@domain.com", cmd.getAccountJid());
            assertEquals(EditUser.ACTION_SUBMIT, cmd.getAction());
        }

        @Test
        @DisplayName("EditUser 双参数构造函数应设置 accountJid 和 password")
        void testEditUserDoubleArgConstructor() {
            EditUser cmd = new EditUser("user@domain.com", "password");
            assertEquals("user@domain.com", cmd.getAccountJid());
            assertEquals("password", cmd.getPassword());
            assertEquals(EditUser.ACTION_SUBMIT, cmd.getAction());
        }

        @Test
        @DisplayName("EditUser 三参数构造函数应设置所有属性")
        void testEditUserTripleArgConstructor() {
            EditUser cmd = new EditUser("user@domain.com", "password", "test@example.com");
            assertEquals("user@domain.com", cmd.getAccountJid());
            assertEquals("password", cmd.getPassword());
            assertEquals("test@example.com", cmd.getEmail());
            assertEquals(EditUser.ACTION_SUBMIT, cmd.getAction());
        }

        @Test
        @DisplayName("EditUser createExecuteCommand 应返回 ACTION_EXECUTE")
        void testEditUserCreateExecuteCommand() {
            EditUser cmd = EditUser.createExecuteCommand();
            assertEquals(EditUser.ACTION_EXECUTE, cmd.getAction());
        }

        @Test
        @DisplayName("EditUser createSubmitForm 应正确设置所有属性")
        void testEditUserCreateSubmitForm() {
            EditUser cmd = EditUser.createSubmitForm("session-123", "user@domain.com", "password", "test@example.com");
            assertEquals("session-123", cmd.getSessionId());
            assertEquals("user@domain.com", cmd.getAccountJid());
            assertEquals("password", cmd.getPassword());
            assertEquals("test@example.com", cmd.getEmail());
            assertEquals(EditUser.ACTION_SUBMIT, cmd.getAction());
        }

        @Test
        @DisplayName("EditUser.toXml execute 动作应生成简单 XML")
        void testEditUserToXmlExecute() {
            EditUser cmd = EditUser.createExecuteCommand();
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"execute\""));
            assertTrue(xml.contains("</command>"));
            assertFalse(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
        }

        @Test
        @DisplayName("EditUser.toXml submit 动作应生成完整 XML")
        void testEditUserToXmlSubmit() {
            EditUser cmd = new EditUser("user@domain.com", "password", "test@example.com");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"submit\""));
            assertTrue(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
            assertTrue(xml.contains("var=\"accountjid\""));
            assertTrue(xml.contains("user@domain.com"));
            assertTrue(xml.contains("var=\"password\""));
            assertTrue(xml.contains("password"));
            assertTrue(xml.contains("var=\"email\""));
            assertTrue(xml.contains("test@example.com"));
        }

        @Test
        @DisplayName("EditUser.toXml null password 不应包含密码字段")
        void testEditUserToXmlNullPassword() {
            EditUser cmd = new EditUser("user@domain.com");
            String xml = cmd.toXml();

            assertTrue(xml.contains("var=\"accountjid\""));
            assertFalse(xml.contains("var=\"password\""));
        }

        @Test
        @DisplayName("EditUser.toXml null email 不应包含邮箱字段")
        void testEditUserToXmlNullEmail() {
            EditUser cmd = new EditUser("user@domain.com", "password");
            String xml = cmd.toXml();

            assertTrue(xml.contains("var=\"accountjid\""));
            assertTrue(xml.contains("user@domain.com"));
            assertTrue(xml.contains("var=\"password\""));
            assertTrue(xml.contains("password"));
            assertFalse(xml.contains("var=\"email\""));
        }

        @Test
        @DisplayName("EditUser.toXml 应包含 sessionid")
        void testEditUserToXmlWithSessionId() {
            EditUser cmd = EditUser.createSubmitForm("session-123", "user@domain.com", "password", null);
            String xml = cmd.toXml();

            assertTrue(xml.contains("sessionid=\"session-123\""));
        }

        @Test
        @DisplayName("EditUser.getElementName 应返回 command")
        void testEditUserGetElementName() {
            EditUser cmd = new EditUser();
            assertEquals("command", cmd.getElementName());
        }

        @Test
        @DisplayName("EditUser.getNamespace 应返回 Commands 命名空间")
        void testEditUserGetNamespace() {
            EditUser cmd = new EditUser();
            assertEquals(EditUser.NAMESPACE, cmd.getNamespace());
        }
    }

    @Nested
    @DisplayName("GetUser 测试")
    class GetUserTests {

        @Test
        @DisplayName("GetUser 默认构造函数应设置 ACTION_COMPLETE")
        void testGetUserDefaultConstructor() {
            GetUser cmd = new GetUser();
            assertEquals(GetUser.ACTION_COMPLETE, cmd.getAction());
        }

        @Test
        @DisplayName("GetUser 单参数构造函数应设置 accountJid")
        void testGetUserSingleArgConstructor() {
            GetUser cmd = new GetUser("user@domain.com");
            assertEquals("user@domain.com", cmd.getAccountJid());
            assertEquals(GetUser.ACTION_COMPLETE, cmd.getAction());
        }

        @Test
        @DisplayName("GetUser createExecuteCommand 应返回 ACTION_EXECUTE")
        void testGetUserCreateExecuteCommand() {
            GetUser cmd = GetUser.createExecuteCommand();
            assertEquals(GetUser.ACTION_EXECUTE, cmd.getAction());
        }

        @Test
        @DisplayName("GetUser createSubmitForm 应正确设置所有属性")
        void testGetUserCreateSubmitForm() {
            GetUser cmd = GetUser.createSubmitForm("session-123", "user@domain.com");
            assertEquals("session-123", cmd.getSessionId());
            assertEquals("user@domain.com", cmd.getAccountJid());
            assertEquals(GetUser.ACTION_COMPLETE, cmd.getAction());
        }

        @Test
        @DisplayName("GetUser.toXml execute 动作应生成简单 XML")
        void testGetUserToXmlExecute() {
            GetUser cmd = GetUser.createExecuteCommand();
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"execute\""));
            assertTrue(xml.contains("</command>"));
            assertFalse(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
        }

        @Test
        @DisplayName("GetUser.toXml complete 动作应生成完整 XML")
        void testGetUserToXmlComplete() {
            GetUser cmd = new GetUser("user@domain.com");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertTrue(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
            assertTrue(xml.contains("var=\"accountjid\""));
            assertTrue(xml.contains("user@domain.com"));
        }

        @Test
        @DisplayName("GetUser.toXml null accountJid 不应包含 accountjid 字段")
        void testGetUserToXmlNullAccountJid() {
            GetUser cmd = new GetUser();
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertFalse(xml.contains("<field var=\"accountjid\">"));
        }

        @Test
        @DisplayName("GetUser.toXml 应包含 sessionid")
        void testGetUserToXmlWithSessionId() {
            GetUser cmd = GetUser.createSubmitForm("session-123", "user@domain.com");
            String xml = cmd.toXml();

            assertTrue(xml.contains("sessionid=\"session-123\""));
        }

        @Test
        @DisplayName("GetUser.toXml null sessionId 不应包含 sessionid 属性")
        void testGetUserToXmlNullSessionId() {
            GetUser cmd = new GetUser("user@domain.com");
            String xml = cmd.toXml();

            assertFalse(xml.contains("sessionid="));
        }

        @Test
        @DisplayName("GetUser.getElementName 应返回 command")
        void testGetUserGetElementName() {
            GetUser cmd = new GetUser();
            assertEquals("command", cmd.getElementName());
        }

        @Test
        @DisplayName("GetUser.getNamespace 应返回 Commands 命名空间")
        void testGetUserGetNamespace() {
            GetUser cmd = new GetUser();
            assertEquals(GetUser.NAMESPACE, cmd.getNamespace());
        }
    }

    @Nested
    @DisplayName("ListUsers 测试")
    class ListUsersTests {

        @Test
        @DisplayName("ListUsers 默认构造函数应设置 execute 动作")
        void testListUsersDefaultConstructor() {
            ListUsers cmd = new ListUsers();
            assertEquals("execute", cmd.getAction());
        }

        @Test
        @DisplayName("ListUsers 带列表构造函数应设置 searchDomains")
        void testListUsersListArgConstructor() {
            List<String> domains = Arrays.asList("domain1.com", "domain2.com");
            ListUsers cmd = new ListUsers(domains);
            assertEquals(domains, cmd.getSearchDomains());
            assertEquals("execute", cmd.getAction());
        }

        @Test
        @DisplayName("ListUsers createExecuteCommand 应返回 execute 动作")
        void testListUsersCreateExecuteCommand() {
            ListUsers cmd = ListUsers.createExecuteCommand();
            assertEquals("execute", cmd.getAction());
        }

        @Test
        @DisplayName("ListUsers createSubmitForm 应正确设置所有属性")
        void testListUsersCreateSubmitForm() {
            List<String> domains = Arrays.asList("domain1.com");
            ListUsers cmd = ListUsers.createSubmitForm("session-123", domains);
            assertEquals("session-123", cmd.getSessionId());
            assertEquals(domains, cmd.getSearchDomains());
            assertEquals("complete", cmd.getAction());
        }

        @Test
        @DisplayName("ListUsers.toXml execute 动作应生成简单 XML")
        void testListUsersToXmlExecute() {
            ListUsers cmd = ListUsers.createExecuteCommand();
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"execute\""));
            assertTrue(xml.contains("</command>"));
            assertFalse(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
        }

        @Test
        @DisplayName("ListUsers.toXml complete 动作应生成完整 XML")
        void testListUsersToXmlComplete() {
            List<String> domains = Arrays.asList("domain1.com", "domain2.com");
            ListUsers cmd = ListUsers.createSubmitForm("session-123", domains);
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertTrue(xml.contains("<x xmlns=\"jabber:x:data\" type=\"submit\">"));
            assertTrue(xml.contains("sessionid=\"session-123\""));
            assertTrue(xml.contains("var=\"domain\""));
            assertTrue(xml.contains("domain1.com"));
            assertTrue(xml.contains("domain2.com"));
        }

        @Test
        @DisplayName("ListUsers.toXml null searchDomains 不应包含 domain 字段")
        void testListUsersToXmlNullSearchDomains() {
            ListUsers cmd = new ListUsers();
            cmd.setAction("complete");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertFalse(xml.contains("var=\"domain\""));
        }

        @Test
        @DisplayName("ListUsers.toXml 空 searchDomains 不应包含 domain 字段")
        void testListUsersToXmlEmptySearchDomains() {
            ListUsers cmd = new ListUsers(Collections.emptyList());
            cmd.setAction("complete");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertFalse(xml.contains("var=\"domain\""));
        }

        @Test
        @DisplayName("ListUsers.toXml null sessionId 不应包含 sessionid 属性")
        void testListUsersToXmlNullSessionId() {
            List<String> domains = Arrays.asList("domain1.com");
            ListUsers cmd = new ListUsers(domains);
            cmd.setAction("complete");
            String xml = cmd.toXml();

            assertFalse(xml.contains("sessionid="));
        }

        @Test
        @DisplayName("ListUsers.getElementName 应返回 command")
        void testListUsersGetElementName() {
            ListUsers cmd = new ListUsers();
            assertEquals("command", cmd.getElementName());
        }

        @Test
        @DisplayName("ListUsers.getNamespace 应返回 Commands 命名空间")
        void testListUsersGetNamespace() {
            ListUsers cmd = new ListUsers();
            assertEquals(ListUsers.NAMESPACE, cmd.getNamespace());
        }
    }

    @Nested
    @DisplayName("GetOnlineUsers 测试")
    class GetOnlineUsersTests {

        @Test
        @DisplayName("GetOnlineUsers 默认构造函数应设置 execute 动作")
        void testGetOnlineUsersDefaultConstructor() {
            GetOnlineUsers cmd = new GetOnlineUsers();
            assertEquals("execute", cmd.getAction());
        }

        @Test
        @DisplayName("GetOnlineUsers createExecuteCommand 应返回 execute 动作")
        void testGetOnlineUsersCreateExecuteCommand() {
            GetOnlineUsers cmd = GetOnlineUsers.createExecuteCommand();
            assertEquals("execute", cmd.getAction());
        }

        @Test
        @DisplayName("GetOnlineUsers createSubmitForm 应正确设置所有属性")
        void testGetOnlineUsersCreateSubmitForm() {
            GetOnlineUsers cmd = GetOnlineUsers.createSubmitForm("session-123");
            assertEquals("session-123", cmd.getSessionId());
            assertEquals("complete", cmd.getAction());
        }

        @Test
        @DisplayName("GetOnlineUsers.toXml execute 动作应生成简单 XML")
        void testGetOnlineUsersToXmlExecute() {
            GetOnlineUsers cmd = GetOnlineUsers.createExecuteCommand();
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"execute\""));
            assertTrue(xml.contains("</command>"));
            assertFalse(xml.contains("sessionid="));
        }

        @Test
        @DisplayName("GetOnlineUsers.toXml complete 动作应生成完整 XML")
        void testGetOnlineUsersToXmlComplete() {
            GetOnlineUsers cmd = GetOnlineUsers.createSubmitForm("session-123");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertTrue(xml.contains("sessionid=\"session-123\""));
        }

        @Test
        @DisplayName("GetOnlineUsers.toXml null sessionId complete 动作应不包含 sessionid")
        void testGetOnlineUsersToXmlCompleteNullSessionId() {
            GetOnlineUsers cmd = new GetOnlineUsers();
            cmd.setAction("complete");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertFalse(xml.contains("sessionid="));
        }

        @Test
        @DisplayName("GetOnlineUsers.getElementName 应返回 command")
        void testGetOnlineUsersGetElementName() {
            GetOnlineUsers cmd = new GetOnlineUsers();
            assertEquals("command", cmd.getElementName());
        }

        @Test
        @DisplayName("GetOnlineUsers.getNamespace 应返回 Commands 命名空间")
        void testGetOnlineUsersGetNamespace() {
            GetOnlineUsers cmd = new GetOnlineUsers();
            assertEquals(GetOnlineUsers.NAMESPACE, cmd.getNamespace());
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

            assertTrue(xml.contains("<command xmlns=\"http://jabber.org/protocol/commands\" node=\"http://jabber.org/protocol/admin#add-user\" action=\"execute\">"));
            assertTrue(xml.contains("</command>"));
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
            assertTrue(xml.contains("</connectionRequest>"));
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
            assertTrue(xml.contains("<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">"));
            assertTrue(xml.contains("</bind>"));
        }
    }
}
