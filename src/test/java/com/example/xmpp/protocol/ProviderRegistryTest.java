package com.example.xmpp.protocol;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.provider.BindProvider;
import com.example.xmpp.protocol.provider.PingProvider;
import com.example.xmpp.util.XmlStringBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProviderRegistry 单元测试。
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>Provider 注册和查找</li>
 *   <li>Provider 覆盖</li>
 *   <li>Provider 移除</li>
 *   <li>默认 Provider</li>
 * </ul>
 */
class ProviderRegistryTest {

    private ProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ProviderRegistry.getInstance();
    }

    @Test
    @DisplayName("应包含默认的 BindProvider")
    void testDefaultBindProvider() {
        Provider<?> provider = registry.getProvider("bind", "urn:ietf:params:xml:ns:xmpp-bind").orElse(null);

        assertNotNull(provider, "Should have default BindProvider");
        assertInstanceOf(BindProvider.class, provider);
    }

    @Test
    @DisplayName("应包含默认的 PingProvider")
    void testDefaultPingProvider() {
        Provider<?> provider = registry.getProvider("ping", "urn:xmpp:ping").orElse(null);

        assertNotNull(provider, "Should have default PingProvider");
        assertInstanceOf(PingProvider.class, provider);
    }

    @Test
    @DisplayName("应能注册自定义 Provider")
    void testRegisterCustomProvider() {
        // 创建自定义 Provider
        Provider<TestElement> customProvider = new TestProvider();

        // 注册
        registry.registerProvider(customProvider);

        // 查找
        Provider<?> found = registry.getProvider("test", "urn:test").orElse(null);

        assertNotNull(found, "Should find registered provider");
        assertSame(customProvider, found);

        // 清理
        registry.removeProvider("test", "urn:test");
    }

    @Test
    @DisplayName("应能覆盖已存在的 Provider")
    void testOverrideProvider() {
        // 注册第一个
        Provider<TestElement> provider1 = new TestProvider("test-override", "urn:test");
        registry.registerProvider(provider1);

        // 注册第二个（相同 key）
        Provider<TestElement> provider2 = new TestProvider("test-override", "urn:test");
        registry.registerProvider(provider2);

        // 查找应该返回第二个
        Provider<?> found = registry.getProvider("test-override", "urn:test").orElse(null);
        assertSame(provider2, found);

        // 清理
        registry.removeProvider("test-override", "urn:test");
    }

    @Test
    @DisplayName("应能移除 Provider")
    void testRemoveProvider() {
        // 注册
        Provider<TestElement> provider = new TestProvider();
        registry.registerProvider(provider);

        // 确认存在
        assertTrue(registry.getProvider("test", "urn:test").isPresent());

        // 移除
        Provider<?> removed = registry.removeProvider("test", "urn:test").orElse(null);

        assertSame(provider, removed);
        assertFalse(registry.getProvider("test", "urn:test").isPresent());
    }

    @Test
    @DisplayName("不存在的 Provider 应返回 null")
    void testNonExistentProvider() {
        Provider<?> provider = registry.getProvider("nonexistent", "urn:nonexistent").orElse(null);
        assertNull(provider);
    }

    @Test
    @DisplayName("null 命名空间应正常工作")
    void testNullNamespace() {
        // 注册无命名空间的 Provider
        Provider<TestElement> provider = new TestProvider("test-no-ns", null);
        registry.registerProvider(provider);

        // 查找
        Provider<?> found = registry.getProvider("test-no-ns", null).orElse(null);
        assertNotNull(found);

        // 清理
        registry.removeProvider("test-no-ns", null);
    }

    @Test
    @DisplayName("空命名空间应正常工作")
    void testEmptyNamespace() {
        // 注册空命名空间的 Provider
        Provider<TestElement> provider = new TestProvider("test-empty-ns", "");
        registry.registerProvider(provider);

        // 用空字符串查找
        Provider<?> found = registry.getProvider("test-empty-ns", "").orElse(null);
        assertNotNull(found);

        // 清理
        registry.removeProvider("test-empty-ns", "");
    }

    @Test
    @DisplayName("hasProvider 应正确返回")
    void testHasProvider() {
        assertTrue(registry.hasProvider("bind", "urn:ietf:params:xml:ns:xmpp-bind"));
        assertTrue(registry.hasProvider("ping", "urn:xmpp:ping"));
        assertFalse(registry.hasProvider("nonexistent", "urn:nonexistent"));
    }

    @Test
    @DisplayName("size 应返回正确的 Provider 数量")
    void testSize() {
        int initialSize = registry.size();

        // 注册
        Provider<TestElement> provider = new TestProvider();
        registry.registerProvider(provider);

        assertEquals(initialSize + 1, registry.size());

        // 清理
        registry.removeProvider("test", "urn:test");
        assertEquals(initialSize, registry.size());
    }

    @Test
    @DisplayName("getRegisteredKeys 应返回所有键")
    void testGetRegisteredKeys() {
        var keys = registry.getRegisteredKeys();

        assertNotNull(keys);
        assertTrue(keys.size() >= 2); // 至少有 bind 和 ping
    }

    @Test
    @DisplayName("注册 null Provider 应抛出异常")
    void testRegisterNullProvider() {
        assertThrows(NullPointerException.class, () -> {
            registry.registerProvider(null);
        });
    }

    // ==================== 测试辅助类 ====================

    /**
     * 测试用元素。
     */
    private static class TestElement implements ExtensionElement {
        private final String content;

        TestElement(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        @Override
        public String getElementName() {
            return "test";
        }

        @Override
        public String getNamespace() {
            return "urn:test";
        }

        @Override
        public String toXml() {
            StringBuilder sb = new StringBuilder();
            sb.append("<test xmlns=\"urn:test\">");
            if (content != null) {
                sb.append(content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
            }
            sb.append("</test>");
            return sb.toString();
        }
    }

    /**
     * 测试用 Provider。
     */
    private static class TestProvider implements Provider<TestElement> {
        private final String elementName;
        private final String namespace;

        TestProvider() {
            this("test", "urn:test");
        }

        TestProvider(String elementName, String namespace) {
            this.elementName = elementName;
            this.namespace = namespace;
        }

        @Override
        public TestElement parse(javax.xml.stream.XMLEventReader reader) throws XmppParseException {
            return new TestElement("test-content");
        }

        @Override
        public void serialize(TestElement object, XmlStringBuilder xml) {
            xml.append(object.toXml());
        }

        @Override
        public String getElementName() {
            return elementName;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }
    }
}
