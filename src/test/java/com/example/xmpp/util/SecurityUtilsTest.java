package com.example.xmpp.util;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.model.sasl.Auth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityUtils 单元测试。
 */
class SecurityUtilsTest {

    @Test
    @DisplayName("clear 应正确清理字符数组")
    void testClearCharArray() {
        char[] chars = "password".toCharArray();
        
        SecurityUtils.clear(chars);
        
        for (char c : chars) {
            assertEquals('\0', c);
        }
    }

    @Test
    @DisplayName("clear 应正确清理字节数组")
    void testClearByteArray() {
        byte[] bytes = "secret".getBytes(StandardCharsets.UTF_8);
        
        SecurityUtils.clear(bytes);
        
        for (byte b : bytes) {
            assertEquals(0, b);
        }
    }

    @Test
    @DisplayName("toBytes 应正确转换 ASCII 字符")
    void testToBytesAscii() {
        char[] chars = "hello".toCharArray();
        
        byte[] bytes = SecurityUtils.toBytes(chars);
        
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    @DisplayName("toBytes 应正确转换 UTF-8 多字节字符")
    void testToBytesUtf8() {
        char[] chars = "你好".toCharArray();
        
        byte[] bytes = SecurityUtils.toBytes(chars);
        
        assertArrayEquals("你好".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    @DisplayName("toBytes 应正确处理 null")
    void testToBytesNull() {
        byte[] bytes = SecurityUtils.toBytes(null);

        assertNotNull(bytes);
        assertArrayEquals(new byte[0], bytes);
    }

    @Test
    @DisplayName("toBytes 应正确处理空数组")
    void testToBytesEmpty() {
        char[] chars = new char[0];
        
        byte[] bytes = SecurityUtils.toBytes(chars);
        
        assertArrayEquals(new byte[0], bytes);
    }

    @Test
    @DisplayName("summarizeStanza 应摘要 IQ 的结构信息")
    void testSummarizeStanzaIq() {
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("iq-1")
                .from("a@example.com")
                .to("b@example.com")
                .childElement(Ping.INSTANCE)
                .build();

        String summary = SecurityUtils.summarizeStanza(iq);

        assertEquals("iq type=get id=iq-1 from=a@example.com to=b@example.com child=ping childNs=urn:xmpp:ping",
                summary);
    }

    @Test
    @DisplayName("summarizeStanza 应摘要 Message 和 Presence")
    void testSummarizeStanzaMessageAndPresence() {
        Message message = new Message.Builder(Message.Type.CHAT)
                .id("m-1")
                .from("alice@example.com")
                .to("bob@example.com")
                .build();
        Presence presence = new Presence.Builder(Presence.Type.SUBSCRIBE)
                .id("p-1")
                .from("alice@example.com")
                .to("bob@example.com")
                .build();

        assertEquals("message type=chat id=m-1 from=alice@example.com to=bob@example.com",
                SecurityUtils.summarizeStanza(message));
        assertEquals("presence type=subscribe id=p-1 from=alice@example.com to=bob@example.com",
                SecurityUtils.summarizeStanza(presence));
    }

    @Test
    @DisplayName("summarizeExtensionElement 应摘要扩展元素结构")
    void testSummarizeExtensionElement() {
        Auth auth = new Auth("PLAIN", "c2VjcmV0");

        assertEquals("auth xmlns=urn:ietf:params:xml:ns:xmpp-sasl mechanism=PLAIN",
                SecurityUtils.summarizeExtensionElement(auth));
        assertNull(SecurityUtils.summarizeExtensionElement(null));
    }

    @Test
    @DisplayName("summarizeExtensionElement 对非 Auth 扩展只保留结构信息")
    void testSummarizeExtensionElementForGenericExtension() {
        ExtensionElement extension = new ExtensionElement() {
            @Override
            public String getElementName() {
                return "x";
            }

            @Override
            public String getNamespace() {
                return "";
            }

            @Override
            public String toXml() {
                return "<x/>";
            }
        };

        assertEquals("x", SecurityUtils.summarizeExtensionElement(extension));
    }

    @Test
    @DisplayName("summarizeStanza 应处理 null 和未知 stanza 实现")
    void testSummarizeStanzaNullAndUnknownType() {
        XmppStanza stanza = new XmppStanza() {
            @Override
            public String getId() {
                return null;
            }

            @Override
            public String getFrom() {
                return null;
            }

            @Override
            public String getTo() {
                return null;
            }
        };

        assertNull(SecurityUtils.summarizeStanza(null));
        assertEquals(stanza.getClass().getSimpleName(), SecurityUtils.summarizeStanza(stanza));
    }

    @Test
    @DisplayName("escapeXmlAttribute 应正确转义")
    void testEscapeXmlAttribute() {
        String escaped = SecurityUtils.escapeXmlAttribute("\"'<>&");

        assertTrue(escaped.contains("&quot;"));
        assertTrue(escaped.contains("&apos;"));
    }

    @Test
    @DisplayName("escapeXmlAttribute 应保留 null 空串和普通文本")
    void testEscapeXmlAttributeWithNullEmptyAndPlainText() {
        assertNull(SecurityUtils.escapeXmlAttribute(null));
        assertEquals("", SecurityUtils.escapeXmlAttribute(""));
        assertEquals("plain-text", SecurityUtils.escapeXmlAttribute("plain-text"));
    }
}
