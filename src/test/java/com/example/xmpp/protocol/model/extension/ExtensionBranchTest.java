package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.GenericExtensionElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extension 模型类全面测试补充 - 覆盖更多分支。
 */
class ExtensionBranchTest {

    @Nested
    @DisplayName("GenericExtensionElement 测试补充")
    class GenericExtensionElementBranchTests {

        @Test
        @DisplayName("GenericExtensionElement.toXml 应生成完整 XML")
        void testToXmlFull() {
            GenericExtensionElement ext = GenericExtensionElement.builder("test", "test-ns")
                    .text("test-value")
                    .build();

            String xml = ext.toXml();
            assertTrue(xml.contains("test"));
            assertTrue(xml.contains("test-ns"));
            assertTrue(xml.contains("test-value"));
        }

        @Test
        @DisplayName("GenericExtensionElement.toXml 应处理空值")
        void testToXmlEmpty() {
            GenericExtensionElement ext = GenericExtensionElement.builder("test", "test-ns").build();

            String xml = ext.toXml();
            assertTrue(xml.contains("test"));
            assertTrue(xml.contains("test-ns"));
        }

        @Test
        @DisplayName("GenericExtensionElement.toXml 应处理带属性的元素")
        void testToXmlWithAttributes() {
            GenericExtensionElement ext = GenericExtensionElement.builder("test", "test-ns")
                    .addAttribute("attr1", "value1")
                    .addAttribute("attr2", "value2")
                    .build();

            String xml = ext.toXml();
            assertTrue(xml.contains("attr1=\"value1\""));
            assertTrue(xml.contains("attr2=\"value2\""));
        }

        @Test
        @DisplayName("GenericExtensionElement.toXml 应处理嵌套元素")
        void testToXmlWithChildren() {
            GenericExtensionElement child = GenericExtensionElement.builder("child", "child-ns")
                    .text("child-value")
                    .build();
            GenericExtensionElement parent = GenericExtensionElement.builder("parent", "parent-ns")
                    .addChild(child)
                    .build();

            String xml = parent.toXml();
            assertTrue(xml.contains("<parent"));
            assertTrue(xml.contains("<child"));
            assertTrue(xml.contains("child-value"));
        }

        @Test
        @DisplayName("GenericExtensionElement.getElementName 应返回元素名")
        void testGetElementName() {
            GenericExtensionElement ext = GenericExtensionElement.builder("myelement", "my-ns").build();
            assertEquals("myelement", ext.getElementName());
        }

        @Test
        @DisplayName("GenericExtensionElement.getNamespace 应返回命名空间")
        void testGetNamespace() {
            GenericExtensionElement ext = GenericExtensionElement.builder("myelement", "my-ns").build();
            assertEquals("my-ns", ext.getNamespace());
        }
    }

    @Nested
    @DisplayName("Bind 测试补充分支")
    class BindBranchTests {

        @Test
        @DisplayName("Bind.toXml 应处理 null jid")
        void testBindToXmlNullJid() {
            Bind bind = Bind.builder()
                    .resource("mobile")
                    .build();

            String xml = bind.toXml();
            assertTrue(xml.contains("<bind"));
            assertTrue(xml.contains("<resource>mobile</resource>"));
            assertFalse(xml.contains("<jid>"));
        }

        @Test
        @DisplayName("Bind.toXml 应处理 null resource")
        void testBindToXmlNullResource() {
            Bind bind = Bind.builder()
                    .jid("user@example.com/resource")
                    .build();

            String xml = bind.toXml();
            assertTrue(xml.contains("<bind"));
            assertTrue(xml.contains("<jid>user@example.com/resource</jid>"));
            assertFalse(xml.contains("<resource>"));
        }

        @Test
        @DisplayName("Bind.builder 应支持链式调用")
        void testBindBuilderChain() {
            Bind bind = Bind.builder()
                    .jid("user@example.com/resource")
                    .resource("mobile")
                    .build();

            assertNotNull(bind);
            assertEquals("user@example.com/resource", bind.getJid());
            assertEquals("mobile", bind.getResource());
        }
    }

    @Nested
    @DisplayName("ConnectionRequest 分支测试")
    class ConnectionRequestBranchTests {

        @Test
        @DisplayName("ConnectionRequest.builder 应支持所有属性")
        void testBuilderAllProperties() {
            ConnectionRequest req = ConnectionRequest.builder()
                    .username("testuser")
                    .password("testpass")
                    .build();

            String xml = req.toXml();
            assertTrue(xml.contains("username"));
            assertTrue(xml.contains("testuser"));
            assertTrue(xml.contains("password"));
            assertTrue(xml.contains("testpass"));
        }

        @Test
        @DisplayName("ConnectionRequest.builder 在缺少 password 时应抛出异常")
        void testBuilderShouldRejectMissingPassword() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> ConnectionRequest.builder()
                            .username("testuser")
                            .build());

            assertEquals("password must not be null or blank", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Ping 分支测试")
    class PingBranchTests {

        @Test
        @DisplayName("Ping.toXml 应生成有效 XML")
        void testPingToXml() {
            Ping ping = new Ping();
            String xml = ping.toXml();

            assertTrue(xml.contains("<ping xmlns=\"urn:xmpp:ping\"/>"));
        }

        @Test
        @DisplayName("Ping.getElementName 应返回 ping")
        void testPingGetElementName() {
            Ping ping = new Ping();
            assertEquals("ping", ping.getElementName());
        }

        @Test
        @DisplayName("Ping.getNamespace 应返回 ping 命名空间")
        void testPingGetNamespace() {
            Ping ping = new Ping();
            assertEquals("urn:xmpp:ping", ping.getNamespace());
        }
    }

    @Nested
    @DisplayName("AddUser 分支测试")
    class AddUserBranchTests {

        @Test
        @DisplayName("AddUser.toXml 应处理 null sessionId")
        void testAddUserToXmlNullSessionId() {
            AddUser addUser = new AddUser("testuser", "password");
            String xml = addUser.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertFalse(xml.contains("sessionid="));
        }

        @Test
        @DisplayName("AddUser.toXml 应处理 null email")
        void testAddUserToXmlNullEmail() {
            AddUser addUser = new AddUser("testuser", "password", null);
            String xml = addUser.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertFalse(xml.contains("email"));
        }

        @Test
        @DisplayName("AddUser 构造函数在 username 为 null 时应抛出异常")
        void testAddUserConstructorShouldRejectNullUsername() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new AddUser(null, "password"));

            assertEquals("username must not be null or blank", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("ChangeUserPassword 分支测试")
    class ChangeUserPasswordBranchTests {

        @Test
        @DisplayName("ChangeUserPassword.toXml 应处理 null sessionId")
        void testChangeUserPasswordToXmlNullSessionId() {
            ChangeUserPassword cmd = new ChangeUserPassword("user@domain.com", "newpass");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertFalse(xml.contains("sessionid="));
        }

        @Test
        @DisplayName("ChangeUserPassword 构造函数在 accountJid 为 null 时应抛出异常")
        void testChangeUserPasswordConstructorShouldRejectNullAccountJid() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new ChangeUserPassword(null, "newpass"));

            assertEquals("accountJid must not be null or blank", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("DeleteUser 分支测试")
    class DeleteUserBranchTests {

        @Test
        @DisplayName("DeleteUser.toXml 应处理 null sessionId")
        void testDeleteUserToXmlNullSessionId() {
            DeleteUser cmd = new DeleteUser("testuser@domain.com");
            String xml = cmd.toXml();

            assertTrue(xml.contains("action=\"complete\""));
            assertFalse(xml.contains("sessionid="));
        }

        @Test
        @DisplayName("DeleteUser 构造函数在 accountJid 为 null 时应抛出异常")
        void testDeleteUserConstructorShouldRejectNullAccountJid() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new DeleteUser(null));

            assertEquals("accountJid must not be null or blank", exception.getMessage());
        }
    }
}
