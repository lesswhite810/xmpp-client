package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import com.example.xmpp.util.XmppEventReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AbstractProvider 单元测试。
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>模板方法模式</li>
 *   <li>异常处理</li>
 *   <li>空值处理</li>
 * </ul>
 */
class AbstractProviderTest {

    @Test
    @DisplayName("parse() 应正确调用子类的 parseInstance()")
    void testParseCallsParseInstance() throws Exception {
        TestProvider provider = new TestProvider();
        String xml = "<test xmlns='urn:test'>content</test>";

        XMLEventReader reader = XmppEventReader.createReader(xml.getBytes());
        reader.next(); // 跳到 START_ELEMENT

        TestElement element = provider.parse(reader);

        assertNotNull(element);
        assertEquals("content", element.getContent());
    }

    @Test
    @DisplayName("parse(null) 应抛出 XmppParseException")
    void testParseNull() {
        TestProvider provider = new TestProvider();

        assertThrows(XmppParseException.class, () -> {
            provider.parse(null);
        });
    }

    @Test
    @DisplayName("parse() 应将 XMLStreamException 转换为 XmppParseException")
    void testParseConvertsException() throws Exception {
        ThrowingProvider provider = new ThrowingProvider();
        String xml = "<test xmlns='urn:test'>content</test>";

        XMLEventReader reader = XmppEventReader.createReader(xml.getBytes());
        reader.next();

        assertThrows(XmppParseException.class, () -> {
            provider.parse(reader);
        });
    }

    @Test
    @DisplayName("serialize() 应正确调用子类的 serializeInstance()")
    void testSerializeCallsSerializeInstance() {
        TestProvider provider = new TestProvider();
        TestElement element = new TestElement("test-content");
        XmlStringBuilder xml = new XmlStringBuilder();

        provider.serialize(element, xml);

        String result = xml.toString();
        assertTrue(result.contains("test"));
        assertTrue(result.contains("test-content"));
    }

    @Test
    @DisplayName("serialize(null, xml) 应安全跳过")
    void testSerializeNull() {
        TestProvider provider = new TestProvider();
        XmlStringBuilder xml = new XmlStringBuilder();

        // 不应抛出异常
        assertDoesNotThrow(() -> {
            provider.serialize(null, xml);
        });
    }

    @Test
    @DisplayName("serialize(element, null) 应安全跳过")
    void testSerializeNullXml() {
        TestProvider provider = new TestProvider();
        TestElement element = new TestElement("test");

        // 不应抛出异常
        assertDoesNotThrow(() -> {
            provider.serialize(element, null);
        });
    }

    @Test
    @DisplayName("getElementText 应正确获取文本内容")
    void testGetElementText() throws Exception {
        TestProvider provider = new TestProvider();
        String xml = "<test xmlns='urn:test'>Hello World</test>";

        XMLEventReader reader = XmppEventReader.createReader(xml.getBytes());
        reader.next(); // 跳到 START_ELEMENT

        String text = provider.getElementText(reader);

        assertEquals("Hello World", text);
    }

    @Test
    @DisplayName("createParseException 应创建正确的异常")
    void testCreateParseException() {
        TestProvider provider = new TestProvider();

        XmppParseException ex = provider.createParseException("Test error");

        assertNotNull(ex);
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("createParseException with cause 应包含原因")
    void testCreateParseExceptionWithCause() {
        TestProvider provider = new TestProvider();
        Exception cause = new RuntimeException("Original error");

        XmppParseException ex = provider.createParseException("Test error", cause);

        assertNotNull(ex);
        assertEquals("Test error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ==================== 测试辅助类 ====================

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
            return "<test xmlns=\"urn:test\">" + content + "</test>";
        }
    }

    private static class TestProvider extends AbstractProvider<TestElement> {
        @Override
        public String getElementName() {
            return "test";
        }

        @Override
        public String getNamespace() {
            return "urn:test";
        }

        @Override
        protected TestElement parseInstance(XMLEventReader reader) throws XMLStreamException {
            String content = getElementText(reader);
            return new TestElement(content);
        }

        @Override
        protected void serializeInstance(TestElement object, XmlStringBuilder xml) {
            xml.append("<test xmlns=\"urn:test\">");
            xml.escapedContent(object.getContent());
            xml.append("</test>");
        }
    }

    private static class ThrowingProvider extends AbstractProvider<TestElement> {
        @Override
        public String getElementName() {
            return "test";
        }

        @Override
        public String getNamespace() {
            return "urn:test";
        }

        @Override
        protected TestElement parseInstance(XMLEventReader reader) throws XMLStreamException {
            throw new XMLStreamException("Test exception");
        }

        @Override
        protected void serializeInstance(TestElement object, XmlStringBuilder xml) {
            // no-op
        }
    }
}
