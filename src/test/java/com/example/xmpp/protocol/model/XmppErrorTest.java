package com.example.xmpp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmppError 全面测试。
 */
class XmppErrorTest {

    @Nested
    @DisplayName("XmppError 构造器测试")
    class XmppErrorConstructionTests {

        @Test
        @DisplayName("XmppError 应正确创建")
        void testXmppErrorCreation() {
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST)
                    .text("Bad request")
                    .type(XmppError.Type.MODIFY)
                    .build();

            assertEquals(XmppError.Condition.BAD_REQUEST, error.getCondition());
            assertEquals("Bad request", error.getText());
            assertEquals(XmppError.Type.MODIFY, error.getType());
        }

        @Test
        @DisplayName("XmppError 应自动推断类型")
        void testXmppErrorAutoType() {
            // BAD_REQUEST 的默认类型是 MODIFY
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST).build();

            assertEquals(XmppError.Condition.BAD_REQUEST, error.getCondition());
            assertEquals(XmppError.Type.MODIFY, error.getType());
            assertNull(error.getText());
            assertNull(error.getExtension());
        }

        @Test
        @DisplayName("XmppError 应支持只有 condition")
        void testXmppErrorConditionOnly() {
            XmppError error = new XmppError.Builder(XmppError.Condition.FORBIDDEN).build();

            assertEquals(XmppError.Condition.FORBIDDEN, error.getCondition());
            assertEquals(XmppError.Type.AUTH, error.getType()); // FORBIDDEN 默认类型是 AUTH
            assertNull(error.getExtension());
        }
    }

    @Nested
    @DisplayName("XmppError toXml 测试")
    class XmppErrorToXmlTests {

        @Test
        @DisplayName("XmppError.toXml 应生成完整 XML")
        void testXmppErrorToXmlFull() {
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST)
                    .text("Bad request")
                    .type(XmppError.Type.MODIFY)
                    .build();

            String xml = error.toXml();
            assertTrue(xml.contains("<error type=\"modify\">"));
            assertTrue(xml.contains("<bad-request xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>"));
            assertTrue(xml.contains("<text xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\">Bad request</text>"));
            assertTrue(xml.contains("</error>"));
        }

        @Test
        @DisplayName("XmppError.toXml 应处理只有 condition")
        void testXmppErrorToXmlConditionOnly() {
            XmppError error = new XmppError.Builder(XmppError.Condition.FORBIDDEN).build();

            String xml = error.toXml();
            assertTrue(xml.contains("<error type=\"auth\">"));
            assertTrue(xml.contains("<forbidden xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>"));
            assertFalse(xml.contains("<text"));
            assertTrue(xml.contains("</error>"));
        }

        @Test
        @DisplayName("XmppError.toXml 应处理没有 type 的情况")
        void testXmppErrorToXmlNoType() {
            // 当没有type也没有condition时
            XmppError error = new XmppError.Builder(null).build();

            String xml = error.toXml();
            assertTrue(xml.contains("<error>"));
            assertFalse(xml.contains("type="));
            assertFalse(xml.contains("<bad-request"));
            assertTrue(xml.contains("</error>"));
        }

        @Test
        @DisplayName("XmppError.toXml 应处理没有 text 的情况")
        void testXmppErrorToXmlNoText() {
            XmppError error = new XmppError.Builder(XmppError.Condition.ITEM_NOT_FOUND)
                    .build();

            String xml = error.toXml();
            assertTrue(xml.contains("<error"));
            assertTrue(xml.contains("<item-not-found xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>"));
            assertFalse(xml.contains("<text"));
        }
    }

    @Nested
    @DisplayName("Condition 枚举测试")
    class ConditionEnumTests {

        @Test
        @DisplayName("Condition 枚举应包含所有标准条件")
        void testConditionEnumCount() {
            // XMPP 定义了22个错误条件
            assertEquals(22, XmppError.Condition.values().length);
        }

        @Test
        @DisplayName("Condition.getElementName 应返回正确的元素名")
        void testConditionGetElementName() {
            assertEquals("bad-request", XmppError.Condition.BAD_REQUEST.getElementName());
            assertEquals("conflict", XmppError.Condition.CONFLICT.getElementName());
            assertEquals("not-authorized", XmppError.Condition.NOT_AUTHORIZED.getElementName());
        }

        @Test
        @DisplayName("Condition.getDefaultType 应返回正确的默认类型")
        void testConditionGetDefaultType() {
            assertEquals(XmppError.Type.MODIFY, XmppError.Condition.BAD_REQUEST.getDefaultType());
            assertEquals(XmppError.Type.CANCEL, XmppError.Condition.CONFLICT.getDefaultType());
            assertEquals(XmppError.Type.AUTH, XmppError.Condition.FORBIDDEN.getDefaultType());
            assertEquals(XmppError.Type.WAIT, XmppError.Condition.INTERNAL_SERVER_ERROR.getDefaultType());
        }

        @Test
        @DisplayName("Condition.fromString 应正确解析")
        void testConditionFromString() {
            assertEquals(XmppError.Condition.BAD_REQUEST, XmppError.Condition.fromString("bad-request"));
            assertEquals(XmppError.Condition.BAD_REQUEST, XmppError.Condition.fromString("BAD_REQUEST"));
            assertEquals(XmppError.Condition.BAD_REQUEST, XmppError.Condition.fromString("bad_request"));
        }

        @Test
        @DisplayName("Condition.fromString 应处理无效输入")
        void testConditionFromStringInvalid() {
            assertEquals(XmppError.Condition.UNDEFINED_CONDITION, XmppError.Condition.fromString("invalid-condition"));
            assertEquals(XmppError.Condition.UNDEFINED_CONDITION, XmppError.Condition.fromString(""));
        }
    }

    @Nested
    @DisplayName("Type 枚举测试")
    class TypeEnumTests {

        @Test
        @DisplayName("Type 枚举应包含所有标准类型")
        void testTypeEnumCount() {
            assertEquals(5, XmppError.Type.values().length);
        }

        @Test
        @DisplayName("Type.toString 应返回小写带连字符")
        void testTypeToString() {
            assertEquals("auth", XmppError.Type.AUTH.toString());
            assertEquals("cancel", XmppError.Type.CANCEL.toString());
            assertEquals("continue-", XmppError.Type.CONTINUE_.toString());
            assertEquals("modify", XmppError.Type.MODIFY.toString());
            assertEquals("wait", XmppError.Type.WAIT.toString());
        }
    }

    @Nested
    @DisplayName("XmppError 属性测试")
    class XmppErrorPropertiesTests {

        @Test
        @DisplayName("XmppError.getElementName 应返回 error")
        void testGetElementName() {
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST).build();
            assertEquals(XmppError.ELEMENT, error.getElementName());
        }

        @Test
        @DisplayName("XmppError.getNamespace 应返回 XMPP stanza 命名空间")
        void testGetNamespace() {
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST).build();
            assertEquals("urn:ietf:params:xml:ns:xmpp-stanzas", error.getNamespace());
        }
    }
}
