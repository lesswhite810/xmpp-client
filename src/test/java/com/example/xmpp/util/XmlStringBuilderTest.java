package com.example.xmpp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XmlStringBuilder 单元测试。
 */
class XmlStringBuilderTest {

    @Nested
    @DisplayName("基础 append 方法")
    class BasicAppendTests {

        @Test
        @DisplayName("应正确追加字符串")
        void testAppendString() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.append("<test>");
            assertEquals("<test>", builder.toString());
        }

        @Test
        @DisplayName("应正确追加多个内容")
        void testMultipleAppends() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.append("<iq>").append("<ping/>").append("</iq>");
            assertEquals("<iq><ping/></iq>", builder.toString());
        }

        @Test
        @DisplayName("应正确处理 null 字符串")
        void testAppendNullString() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.append("start").append((String) null).append("end");
            assertEquals("startend", builder.toString());
        }
    }

    @Nested
    @DisplayName("元素构建方法")
    class ElementTests {

        @Test
        @DisplayName("openElement() 带前缀和命名空间")
        void testOpenElementWithPrefix() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.openElement("stream", "stream", "http://etherx.jabber.org/streams", null);
            String result = builder.toString();
            assertTrue(result.contains("<stream:stream"));
            assertTrue(result.contains("xmlns=\"http://etherx.jabber.org/streams\""));
        }

        @Test
        @DisplayName("openElement() 应支持属性")
        void testOpenElementWithAttributes() {
            XmlStringBuilder builder = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("type", "get");
            attributes.put("id", "test1");
            builder.openElement("iq", null, attributes);
            assertEquals("<iq type=\"get\" id=\"test1\">", builder.toString());
        }
    }

    @Nested
    @DisplayName("属性方法")
    class AttributeTests {

        @Test
        @DisplayName("openElement() 应正确处理 null 属性值")
        void testNullAttribute() {
            XmlStringBuilder builder = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("type", null);
            builder.openElement("iq", null, attributes);
            assertFalse(builder.toString().contains("type="));
        }

        @Test
        @DisplayName("openElement() 应正确追加枚举属性")
        void testEnumAttribute() {
            XmlStringBuilder builder = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("type", TestType.GET);
            builder.openElement("iq", null, attributes);
            assertTrue(builder.toString().contains("type=\"get\""));
        }

        enum TestType { GET, SET, RESULT, ERROR;
            @Override public String toString() { return name().toLowerCase(); }
        }
    }

    @Nested
    @DisplayName("闭合标签方法")
    class WrappedElementTests {

        @Test
        @DisplayName("wrapElement() 应创建完整元素")
        void testWrapElement() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("body", "Hello World");
            assertEquals("<body>Hello World</body>", builder.toString());
        }

        @Test
        @DisplayName("wrapElement() 应转义特殊字符")
        void testWrapElementEscape() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("body", "<>&'\"");
            String result = builder.toString();
            assertTrue(result.contains("&lt;"));
            assertTrue(result.contains("&gt;"));
            assertTrue(result.contains("&amp;"));
        }

        @Test
        @DisplayName("wrapElement() 带命名空间应包装内容")
        void testWrapElementWithNamespace() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("challenge", "urn:ietf:params:xml:ns:xmpp-sasl", "dGVzdA==");
            assertEquals("<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">dGVzdA==</challenge>",
                    builder.toString());
        }

        @Test
        @DisplayName("wrapElement() 内容为 null 时应生成自闭合标签")
        void testWrapElementNullContent() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("response", (String) null);
            assertEquals("<response/>", builder.toString());
        }

        @Test
        @DisplayName("wrapElement() Consumer 版本应包装带命名空间的构建内容")
        void testWrapElementWithNamespaceConsumer() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("bind", "urn:ietf:params:xml:ns:xmpp-bind",
                    (Consumer<XmlStringBuilder>) xml -> {
                        xml.wrapElement("resource", "mobile");
                        xml.wrapElement("jid", "user@example.com/mobile");
                    });
            assertEquals(
                    "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"><resource>mobile</resource>"
                            + "<jid>user@example.com/mobile</jid></bind>",
                    builder.toString());
        }

        @Test
        @DisplayName("wrapElement() Consumer 版本应支持转义内容")
        void testWrapElementConsumerEscapedContent() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("success", "urn:ietf:params:xml:ns:xmpp-sasl",
                    (Consumer<XmlStringBuilder>) xml -> xml.escapeXml("<ok>&\""));
            assertEquals("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"
                    + "&lt;ok&gt;&amp;&quot;</success>", builder.toString());
        }

        @Test
        @DisplayName("wrapElement() 应支持带属性的转义内容")
        void testWrapElementWithAttributesAndStringContent() {
            XmlStringBuilder builder = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("id", "iq-1");
            attributes.put("type", "get");
            builder.wrapElement("iq", "jabber:client", attributes, "<ping/>");
            assertEquals("<iq xmlns=\"jabber:client\" id=\"iq-1\" type=\"get\">&lt;ping/&gt;</iq>", builder.toString());
        }

        @Test
        @DisplayName("wrapElement() 应支持带属性的子元素构建")
        void testWrapElementWithAttributesAndConsumerContent() {
            XmlStringBuilder builder = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("from", "a@example.com");
            attributes.put("to", "b@example.com");
            builder.wrapElement("message", "jabber:client", attributes, xml -> xml.wrapElement("body", "hello"));
            assertEquals("<message xmlns=\"jabber:client\" from=\"a@example.com\" to=\"b@example.com\">"
                    + "<body>hello</body></message>", builder.toString());
        }

        @Test
        @DisplayName("wrapElement() 空内容时应生成自闭合标签")
        void testWrapElementWithNullAttributeValue() {
            XmlStringBuilder builder = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("id", "p1");
            attributes.put("lang", null);
            builder.wrapElement("presence", "jabber:client", attributes, "");
            assertEquals("<presence xmlns=\"jabber:client\" id=\"p1\"/>", builder.toString());
        }
    }

    @Nested
    @DisplayName("空内容标签")
    class EmptyContentElementTests {

        @Test
        @DisplayName("wrapElement() 应创建空内容自闭合标签")
        void testWrapElementEmptyContent() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("ping", "");
            assertEquals("<ping/>", builder.toString());
        }

        @Test
        @DisplayName("wrapElement() 带命名空间时应创建空内容自闭合标签")
        void testWrapElementEmptyContentWithNamespace() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("ping", "urn:xmpp:ping", "");
            String result = builder.toString();
            assertTrue(result.contains("<ping"));
            assertTrue(result.contains("xmlns=\"urn:xmpp:ping\""));
            assertTrue(result.contains("/>"));
        }

        @Test
        @DisplayName("wrapElement() 命名空间为 null 时忽略")
        void testWrapElementEmptyContentNullNamespace() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.wrapElement("ping", (String) null, "");
            assertEquals("<ping/>", builder.toString());
        }
    }

    @Nested
    @DisplayName("转义功能")
    class EscapeTests {

        @Test
        @DisplayName("应正确转义特殊字符")
        void testEscape() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.escapeXml("<>&'\"");
            String result = builder.toString();
            assertTrue(result.contains("&lt;"));
            assertTrue(result.contains("&gt;"));
            assertTrue(result.contains("&amp;"));
            assertTrue(result.contains("&quot;"));
            assertTrue(result.contains("&apos;"));
        }

        @Test
        @DisplayName("escapeXml() 为 null 时不添加")
        void testEscapeXmlNull() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.escapeXml(null);
            assertEquals("", builder.toString());
        }
    }

    @Nested
    @DisplayName("综合场景")
    class IntegrationTests {

        @Test
        @DisplayName("应正确构建 IQ 节")
        void testBuildIq() {
            XmlStringBuilder xml = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("type", "get");
            attributes.put("id", "123");
            attributes.put("from", "user@example.com");
            attributes.put("to", "server.example.com");
            xml.openElement("iq", null, attributes)
               .wrapElement("body", "Hello")
               .append("</iq>");

            String result = xml.toString();
            assertTrue(result.startsWith("<iq "));
            assertTrue(result.contains("type=\"get\""));
            assertTrue(result.contains("id=\"123\""));
            assertTrue(result.contains("<body>Hello</body>"));
            assertTrue(result.endsWith("</iq>"));
        }

        @Test
        @DisplayName("应正确构建消息节")
        void testBuildMessage() {
            XmlStringBuilder xml = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("type", "chat");
            attributes.put("to", "friend@example.com");
            xml.openElement("message", null, attributes);
            xml.wrapElement("subject", "Hello")
               .wrapElement("body", "How are you?")
               .append("</message>");

            String result = xml.toString();
            assertTrue(result.contains("<subject>Hello</subject>"));
            assertTrue(result.contains("<body>How are you?</body>"));
            assertFalse(result.contains("<thread>"));
        }

        @Test
        @DisplayName("应正确构建流头")
        void testBuildStreamHeader() {
            XmlStringBuilder xml = new XmlStringBuilder();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("from", "user@example.com");
            attributes.put("to", "server.example.com");
            attributes.put("version", "1.0");
            xml.openElement("stream", "stream", "http://etherx.jabber.org/streams", attributes);

            String result = xml.toString();
            assertTrue(result.contains("<stream:stream"));
            assertTrue(result.contains("from=\"user@example.com\""));
            assertTrue(result.contains("to=\"server.example.com\""));
        }
    }
}
