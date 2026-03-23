package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import com.example.xmpp.util.XmlParserUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AbstractProvider 单元测试。
 *
 * @since 2026-03-10
 */
class AbstractProviderTest {

    @Test
    @DisplayName("parse() 应正确调用子类的 parseInstance()")
    void testParseCallsParseInstance() throws Exception {
        TestProvider provider = new TestProvider();
        String xml = "<test xmlns='urn:test'>content</test>";

        XMLEventReader reader = XmlParserUtils.createReader(xml.getBytes());
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
        Logger logger = (Logger) LogManager.getLogger(AbstractProvider.class);
        TestLogAppender appender = attachAppender("providerParseFailure", logger);
        ThrowingProvider provider = new ThrowingProvider();
        String xml = "<test xmlns='urn:test'>content</test>";

        try {
            XMLEventReader reader = XmlParserUtils.createReader(xml.getBytes());
            reader.next();

            assertThrows(XmppParseException.class, () -> {
                provider.parse(reader);
            });
            assertTrue(appender.containsAtLevel("Failed to parse <test xmlns=\"urn:test\"> - ErrorType: XMLStreamException",
                    Level.ERROR));
        } finally {
            detachAppender(appender, logger);
        }
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

        XMLEventReader reader = XmlParserUtils.createReader(xml.getBytes());
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

    @Test
    @DisplayName("isElementEnd 应匹配相同名称和命名空间")
    void testIsElementEndMatchesNameAndNamespace() throws Exception {
        TestProvider provider = new TestProvider();
        XMLEventReader reader = XmlParserUtils.createReader("<test xmlns='urn:test'/>".getBytes());
        reader.nextEvent();
        reader.nextEvent();
        XMLEvent endEvent = reader.nextEvent();

        assertTrue(provider.matchesEnd(endEvent));
    }

    @Test
    @DisplayName("isElementEnd 应将 null 和空命名空间视为等价")
    void testIsElementEndTreatsNullAndEmptyNamespaceAsEquivalent() throws Exception {
        NoNamespaceProvider provider = new NoNamespaceProvider();
        XMLEventReader reader = XmlParserUtils.createReader("<test/>".getBytes());
        reader.nextEvent();
        reader.nextEvent();
        XMLEvent endEvent = reader.nextEvent();

        assertTrue(provider.matchesEnd(endEvent));
    }

    @Test
    @DisplayName("isElementEnd 应拒绝不同名称或命名空间")
    void testIsElementEndRejectsDifferentNameOrNamespace() throws Exception {
        TestProvider provider = new TestProvider();
        XMLEventReader reader = XmlParserUtils.createReader("<other xmlns='urn:other'/>".getBytes());
        reader.nextEvent();
        reader.nextEvent();
        XMLEvent endEvent = reader.nextEvent();

        assertFalse(provider.matchesEnd(endEvent));
    }

    // 测试辅助类

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
            xml.escapeXml(object.getContent());
            xml.append("</test>");
        }

        boolean matchesEnd(XMLEvent event) {
            return isElementEnd(event);
        }
    }

    private static class NoNamespaceProvider extends TestProvider {
        @Override
        public String getNamespace() {
            return "";
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

    private TestLogAppender attachAppender(String name, Logger... loggers) {
        TestLogAppender appender = new TestLogAppender(name);
        appender.start();
        for (Logger logger : loggers) {
            logger.addAppender(appender);
            logger.setLevel(Level.ALL);
        }
        return appender;
    }

    private void detachAppender(Appender appender, Logger... loggers) {
        for (Logger logger : loggers) {
            logger.removeAppender(appender);
        }
        appender.stop();
    }

    private static final class TestLogAppender extends AbstractAppender {

        private final List<LogEvent> events = new ArrayList<>();

        private TestLogAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        private boolean containsAtLevel(String text, Level level) {
            for (LogEvent event : events) {
                if (event.getLevel() == level
                        && event.getMessage() != null
                        && event.getMessage().getFormattedMessage().contains(text)) {
                    return true;
                }
            }
            return false;
        }
    }
}
