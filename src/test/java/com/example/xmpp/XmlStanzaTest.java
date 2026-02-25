package com.example.xmpp;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Stanza;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.ProviderRegistry;
import com.example.xmpp.net.XmppStreamDecoder;
import com.example.xmpp.util.XmlStringBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XML 节处理单元测试
 * 
 * 测试 XML 节的创建、解析、序列化、反序列化、Provider 机制等功能。
 */
public class XmlStanzaTest {

    @BeforeEach
    public void setUp() {
        ProviderRegistry.getInstance();
    }

    /**
     * 测试 IQ 节的创建和序列化
     */
    @Test
    public void testIqCreationAndSerialization() {
        Iq iq = new Iq.Builder("get")
                .id("test-123")
                .from("user@example.com/resource")
                .to("server.example.com")
                .build();

        assertEquals(Iq.Type.get, iq.getType());
        assertEquals("test-123", iq.getId());
        assertEquals("user@example.com/resource", iq.getFrom());
        assertEquals("server.example.com", iq.getTo());

        String xmlString = iq.toXml();
        assertNotNull(xmlString);
        assertTrue(xmlString.contains("<iq"));
        assertTrue(xmlString.contains("type=\"get\""));
        assertTrue(xmlString.contains("id=\"test-123\""));
        assertTrue(xmlString.contains("from=\"user@example.com/resource\""));
        assertTrue(xmlString.contains("to=\"server.example.com\""));
    }

    /**
     * 测试 Message 节的创建和序列化
     */
    @Test
    public void testMessageCreationAndSerialization() {
        Message message = new Message.Builder()
                .type("chat")
                .id("msg-456")
                .from("alice@example.com/desktop")
                .to("bob@example.com/mobile")
                .subject("Test Subject")
                .body("Hello Bob! This is a test message.")
                .build();

        assertEquals(Message.Type.chat, message.getType());
        assertEquals("msg-456", message.getId());
        assertEquals("alice@example.com/desktop", message.getFrom());
        assertEquals("bob@example.com/mobile", message.getTo());
        assertEquals("Test Subject", message.getSubject());
        assertEquals("Hello Bob! This is a test message.", message.getBody());

        String xmlString = message.toXml();
        assertNotNull(xmlString);
        assertTrue(xmlString.contains("<message"));
        assertTrue(xmlString.contains("type=\"chat\""));
        assertTrue(xmlString.contains("subject>Test Subject</subject"));
        assertTrue(xmlString.contains("body>Hello Bob! This is a test message.</body"));
    }

    /**
     * 测试 Presence 节的创建和序列化
     */
    @Test
    public void testPresenceCreationAndSerialization() {
        Presence presence = new Presence.Builder()
                .type("available")
                .id("pres-789")
                .from("user@example.com/laptop")
                .to("friend@example.com")
                .show("chat")
                .status("Online and ready to chat")
                .priority(5)
                .build();

        assertEquals(Presence.Type.available, presence.getType());
        assertEquals("pres-789", presence.getId());
        assertEquals("user@example.com/laptop", presence.getFrom());
        assertEquals("friend@example.com", presence.getTo());
        assertEquals("chat", presence.getShow());
        assertEquals("Online and ready to chat", presence.getStatus());
        assertEquals(5, presence.getPriority());

        String xmlString = presence.toXml();
        assertNotNull(xmlString);
        assertTrue(xmlString.contains("<presence"));
        // RFC 6120: available 类型不输出 type 属性
        assertFalse(xmlString.contains("type=\"available\""));
        assertTrue(xmlString.contains("show>chat</show"));
        assertTrue(xmlString.contains("status>Online and ready to chat</status"));
        assertTrue(xmlString.contains("priority>5</priority"));

        // 测试 unavailable 类型应该有 type 属性
        Presence unavailable = new Presence.Builder()
                .type(Presence.Type.unavailable)
                .id("pres-unavail")
                .build();
        String xmlString2 = unavailable.toXml();
        assertTrue(xmlString2.contains("type=\"unavailable\""));
    }

    /**
     * 测试 IQ 节的解析
     */
    @Test
    public void testIqParsing() {
        String xml = "<iq type=\"result\" id=\"bind-234\" to=\"user@example.com/resource\">" +
                     "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">" +
                     "<jid>user@example.com/resource</jid>" +
                     "</bind>" +
                     "</iq>";

        Iq iq = XmppStreamDecoder.parseIq(xml);
        assertNotNull(iq);
        assertEquals(Iq.Type.result, iq.getType());
        assertEquals("bind-234", iq.getId());
        assertEquals("user@example.com/resource", iq.getTo());
        assertNotNull(iq.getChildElement());
    }

    /**
     * 测试 Message 节的解析
     */
    @Test
    public void testMessageParsing() {
        String xml = "<message type=\"groupchat\" id=\"msg-567\" from=\"room@conference.example.com/alice\" to=\"room@conference.example.com\">" +
                     "<subject>Meeting Agenda</subject>" +
                     "<body>Let's discuss the project timeline.</body>" +
                     "</message>";

        Message message = XmppStreamDecoder.parseMessage(xml);
        assertNotNull(message);
        assertEquals(Message.Type.groupchat, message.getType());
        assertEquals("msg-567", message.getId());
        assertEquals("room@conference.example.com/alice", message.getFrom());
        assertEquals("room@conference.example.com", message.getTo());
        assertEquals("Meeting Agenda", message.getSubject());
        assertEquals("Let's discuss the project timeline.", message.getBody());
    }

    /**
     * 测试 Presence 节的解析
     */
    @Test
    public void testPresenceParsing() {
        String xml = "<presence type=\"unavailable\" from=\"user@example.com/desktop\" to=\"contact@example.com\">" +
                     "<status>Going offline</status>" +
                     "</presence>";

        Presence presence = XmppStreamDecoder.parsePresence(xml);
        assertNotNull(presence);
        assertEquals(Presence.Type.unavailable, presence.getType());
        assertEquals("user@example.com/desktop", presence.getFrom());
        assertEquals("contact@example.com", presence.getTo());
        assertEquals("Going offline", presence.getStatus());
    }

    /**
     * 测试 Provider 机制解析 Bind 扩展
     */
    @Test
    public void testProviderMechanismForBind() {
        String xml = "<iq type=\"result\" id=\"bind-123\">" +
                     "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">" +
                     "<jid>test@example.com/resource</jid>" +
                     "</bind>" +
                     "</iq>";

        Iq iq = XmppStreamDecoder.parseIq(xml);
        assertNotNull(iq);
        assertNotNull(iq.getChildElement());
        assertTrue(iq.getChildElement() instanceof Bind);
        Bind bind = (Bind) iq.getChildElement();
        assertEquals("test@example.com/resource", bind.getJid());
    }

    /**
     * 测试 Provider 机制解析 Ping 扩展
     */
    @Test
    public void testProviderMechanismForPing() {
        String xml = "<iq type=\"get\" id=\"ping-456\">" +
                     "<ping xmlns=\"urn:xmpp:ping\"/>" +
                     "</iq>";

        Iq iq = XmppStreamDecoder.parseIq(xml);
        assertNotNull(iq);
        assertNotNull(iq.getChildElement());
        assertTrue(iq.getChildElement() instanceof Ping);
    }

    /**
     * 测试错误处理 - 无效的 XML
     */
    @Test
    public void testErrorHandlingInvalidXml() {
        String invalidXml = "<iq type=\"get\" id=\"test\">" +
                           "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">" +
                           "<jid>user@example.com</jid>" +
                           "</iq>";

        assertThrows(IllegalArgumentException.class, () -> {
            XmppStreamDecoder.parseIq(invalidXml);
        });
    }

    /**
     * 测试错误处理 - 缺少必要字段
     */
    @Test
    public void testErrorHandlingMissingFields() {
        // 当 XML 中缺少 type 属性时，使用默认值 get
        String xmlWithoutType = "<iq id=\"test\">" +
                               "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/>" +
                               "</iq>";

        Iq iq = XmppStreamDecoder.parseIq(xmlWithoutType);
        assertNotNull(iq);
        // type 缺失时使用默认值
        assertEquals(Iq.Type.get, iq.getType());
    }

    /**
     * 测试通用 Stanza 解析
     */
    @Test
    public void testGenericStanzaParsing() {
        String iqXml = "<iq type=\"get\" id=\"test-123\">" +
                      "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/>" +
                      "</iq>";

        Optional<Object> stanza = XmppStreamDecoder.parseStanza(iqXml);
        assertTrue(stanza.isPresent());
        assertTrue(stanza.get() instanceof Iq);

        String messageXml = "<message type=\"chat\" id=\"msg-456\">" +
                           "<body>Test message</body>" +
                           "</message>";

        stanza = XmppStreamDecoder.parseStanza(messageXml);
        assertTrue(stanza.isPresent());
        assertTrue(stanza.get() instanceof Message);

        String presenceXml = "<presence type=\"available\"/>";

        stanza = XmppStreamDecoder.parseStanza(presenceXml);
        assertTrue(stanza.isPresent());
        assertTrue(stanza.get() instanceof Presence);
    }
}
