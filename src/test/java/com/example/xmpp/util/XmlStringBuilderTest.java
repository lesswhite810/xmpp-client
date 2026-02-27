package com.example.xmpp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmlStringBuilder 单元测试。
 */
class XmlStringBuilderTest {

    @Nested
    @DisplayName("基础 append 方法")
    class BasicAppendTests {

        @Test
        @DisplayName("应正确创建空的 XmlStringBuilder")
        void testEmptyBuilder() {
            XmlStringBuilder builder = new XmlStringBuilder();
            assertEquals(0, builder.length());
            assertTrue(builder.isEmpty());
        }

        @Test
        @DisplayName("应正确追加字符串")
        void testAppendString() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.append("<test>");
            assertEquals("<test>", builder.toString());
        }

        @Test
        @DisplayName("应正确追加字符")
        void testAppendChar() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.append('<').append('>').append('/');
            assertEquals("<>/", builder.toString());
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
        @DisplayName("element() 应添加开标签前缀")
        void testElement() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("iq").rightAngleBracket();
            assertEquals("<iq>", builder.toString());
        }

        @Test
        @DisplayName("element() 带命名空间")
        void testElementWithNamespace() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("bind", "urn:ietf:params:xml:ns:xmpp-bind").rightAngleBracket();
            String result = builder.toString();
            assertTrue(result.contains("<bind"));
            assertTrue(result.contains("xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\""));
            assertTrue(result.contains(">"));
        }

        @Test
        @DisplayName("element() 带前缀和命名空间")
        void testElementWithPrefix() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("stream", "stream", "http://etherx.jabber.org/streams").rightAngleBracket();
            String result = builder.toString();
            assertTrue(result.contains("<stream:stream"));
            assertTrue(result.contains("xmlns=\"http://etherx.jabber.org/streams\""));
        }

        @Test
        @DisplayName("openElement() 应添加完整开标签")
        void testOpenElement() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.openElement("body");
            assertEquals("<body>", builder.toString());
        }

        @Test
        @DisplayName("openElement() 带命名空间")
        void testOpenElementWithNamespace() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.openElement("ping", "urn:xmpp:ping");
            assertTrue(builder.toString().contains("<ping"));
            assertTrue(builder.toString().contains("xmlns=\"urn:xmpp:ping\""));
            assertTrue(builder.toString().contains(">"));
        }

        @Test
        @DisplayName("closeElement() 应添加闭标签")
        void testCloseElement() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.closeElement("iq");
            assertEquals("</iq>", builder.toString());
        }

        @Test
        @DisplayName("rightAngleBracket() 应添加右尖括号")
        void testRightAngleBracket() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("iq").rightAngleBracket();
            assertTrue(builder.toString().endsWith(">"));
        }
    }

    @Nested
    @DisplayName("属性方法")
    class AttributeTests {

        @Test
        @DisplayName("应正确追加属性")
        void testAttribute() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("iq").attribute("type", "get").attribute("id", "test1").rightAngleBracket();
            String result = builder.toString();
            assertTrue(result.contains("type=\"get\""));
            assertTrue(result.contains("id=\"test1\""));
        }

        @Test
        @DisplayName("应正确处理 null 属性值")
        void testNullAttribute() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("iq").attribute("type", (String) null).rightAngleBracket();
            assertFalse(builder.toString().contains("type="));
        }

        @Test
        @DisplayName("应正确追加枚举属性")
        void testEnumAttribute() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("iq").attribute("type", TestType.GET).rightAngleBracket();
            assertTrue(builder.toString().contains("type=\"get\""));
        }

        @Test
        @DisplayName("枚举属性为 null 时不添加")
        void testNullEnumAttribute() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("iq").attribute("type", (Enum<?>) null).rightAngleBracket();
            assertFalse(builder.toString().contains("type="));
        }

        enum TestType { GET, SET, RESULT, ERROR;
            @Override public String toString() { return name().toLowerCase(); }
        }
    }

    @Nested
    @DisplayName("文本元素方法")
    class TextElementTests {

        @Test
        @DisplayName("textElement() 应创建完整元素")
        void testTextElement() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.textElement("body", "Hello World");
            assertEquals("<body>Hello World</body>", builder.toString());
        }

        @Test
        @DisplayName("textElement() 应转义特殊字符")
        void testTextElementEscape() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.textElement("body", "<>&'\"");
            String result = builder.toString();
            assertTrue(result.contains("&lt;"));
            assertTrue(result.contains("&gt;"));
            assertTrue(result.contains("&amp;"));
        }

        @Test
        @DisplayName("optTextElement() 内容为 null 时不添加")
        void testOptTextElementNull() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.optTextElement("body", null);
            assertEquals("", builder.toString());
        }

        @Test
        @DisplayName("optTextElement() 内容非 null 时添加")
        void testOptTextElement() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.optTextElement("body", "content");
            assertEquals("<body>content</body>", builder.toString());
        }
    }

    @Nested
    @DisplayName("空元素方法")
    class EmptyElementTests {

        @Test
        @DisplayName("emptyElement() 应创建自闭合标签")
        void testEmptyElement() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.emptyElement("ping");
            assertEquals("<ping/>", builder.toString());
        }

        @Test
        @DisplayName("emptyElement() 带命名空间")
        void testEmptyElementWithNamespace() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.emptyElement("ping", "urn:xmpp:ping");
            String result = builder.toString();
            assertTrue(result.contains("<ping"));
            assertTrue(result.contains("xmlns=\"urn:xmpp:ping\""));
            assertTrue(result.contains("/>"));
        }

        @Test
        @DisplayName("emptyElement() 命名空间为 null 时忽略")
        void testEmptyElementNullNamespace() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.emptyElement("ping", null);
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
            builder.escapedContent("<>&'\"");
            String result = builder.toString();
            assertTrue(result.contains("&lt;"));
            assertTrue(result.contains("&gt;"));
            assertTrue(result.contains("&amp;"));
            assertTrue(result.contains("&quot;"));
            assertTrue(result.contains("&apos;"));
        }

        @Test
        @DisplayName("escapedContent() 为 null 时不添加")
        void testEscapedContentNull() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.escapedContent(null);
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
            xml.element("iq")
               .attribute("type", "get")
               .attribute("id", "123")
               .attribute("from", "user@example.com")
               .attribute("to", "server.example.com")
               .rightAngleBracket()
               .textElement("body", "Hello")
               .closeElement("iq");

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
            xml.element("message")
               .attribute("type", "chat")
               .attribute("to", "friend@example.com")
               .rightAngleBracket()
               .optTextElement("subject", "Hello")
               .optTextElement("body", "How are you?")
               .optTextElement("thread", null)
               .closeElement("message");

            String result = xml.toString();
            assertTrue(result.contains("<subject>Hello</subject>"));
            assertTrue(result.contains("<body>How are you?</body>"));
            assertFalse(result.contains("<thread>"));
        }

        @Test
        @DisplayName("应正确构建流头")
        void testBuildStreamHeader() {
            XmlStringBuilder xml = new XmlStringBuilder();
            xml.element("stream", "stream", "http://etherx.jabber.org/streams")
               .attribute("from", "user@example.com")
               .attribute("to", "server.example.com")
               .attribute("version", "1.0")
               .rightAngleBracket();

            String result = xml.toString();
            assertTrue(result.contains("<stream:stream"));
            assertTrue(result.contains("from=\"user@example.com\""));
            assertTrue(result.contains("to=\"server.example.com\""));
        }

        @Test
        @DisplayName("length 应返回正确长度")
        void testLength() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.append("test");
            assertEquals(4, builder.length());
        }

        @Test
        @DisplayName("应正确处理 toString")
        void testToString() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.append("<iq type='get'/>");
            assertEquals("<iq type='get'/>", builder.toString());
        }

        @Test
        @DisplayName("应正确处理命名空间")
        void testNamespace() {
            XmlStringBuilder builder = new XmlStringBuilder();
            builder.element("iq").attribute("xmlns", "jabber:client").rightAngleBracket();
            assertTrue(builder.toString().contains("xmlns=\"jabber:client\""));
        }
    }
}
