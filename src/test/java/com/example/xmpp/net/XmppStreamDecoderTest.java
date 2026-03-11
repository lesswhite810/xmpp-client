package com.example.xmpp.net;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.model.sasl.Auth;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.stream.StreamError;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.protocol.model.stream.TlsElements;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link XmppStreamDecoder} 单元测试。
 */
class XmppStreamDecoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel(new XmppStreamDecoder());
    }

    @Test
    @DisplayName("应跳过 stream 头元素")
    void testParseStreamHeader() {
        sendStreamHeader();
        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("应正确解析 StreamFeatures")
    void testParseStreamFeatures() {
        sendStreamHeader();
        sendStreamFeatures(
                "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"
                        + "<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>"
                        + "<mechanism>PLAIN</mechanism>"
                        + "<mechanism>SCRAM-SHA-256</mechanism>"
                        + "</mechanisms>"
                        + "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(StreamFeatures.class, msg);

        StreamFeatures features = (StreamFeatures) msg;
        assertTrue(features.isStarttlsAvailable());
        assertTrue(features.isBindAvailable());
        assertEquals(2, features.getMechanisms().size());
        assertTrue(features.getMechanisms().contains("PLAIN"));
        assertTrue(features.getMechanisms().contains("SCRAM-SHA-256"));
    }

    @Test
    @DisplayName("应正确解析 StartTls")
    void testParseStartTls() {
        sendStreamHeader();
        sendXml("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertSame(TlsElements.StartTls.INSTANCE, msg);
    }

    @Test
    @DisplayName("应正确解析 SASL Auth")
    void testParseSaslAuth() {
        sendStreamHeader();
        sendXml("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>AHVzZXIAcGFzcw==</auth>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Auth.class, msg);

        Auth auth = (Auth) msg;
        assertEquals("PLAIN", auth.getMechanism());
        assertEquals("AHVzZXIAcGFzcw==", auth.getContent());
    }

    @Test
    @DisplayName("应正确解析 SASL Failure")
    void testParseSaslFailure() {
        sendStreamHeader();
        sendXml("<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><not-authorized/><text>Invalid credentials</text></failure>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(SaslFailure.class, msg);

        SaslFailure failure = (SaslFailure) msg;
        assertEquals("not-authorized", failure.getCondition());
        assertEquals("Invalid credentials", failure.getText());
    }

    @Test
    @DisplayName("应正确解析 StreamError")
    void testParseStreamError() {
        sendStreamHeader();
        sendXml("<error xmlns='http://etherx.jabber.org/streams'><not-authorized/><text>Authentication required</text></error>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(StreamError.class, msg);
        assertEquals(StreamError.Condition.NOT_AUTHORIZED, ((StreamError) msg).getCondition());
    }

    @Test
    @DisplayName("应忽略非 streams 命名空间的 error 顶层元素")
    void testIgnoreNonStreamNamespaceError() {
        sendStreamHeader();
        sendXml("<error xmlns='urn:example:custom'><custom-condition/><text>ignored</text></error>");
        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("应忽略非 SASL 命名空间的 failure 顶层元素")
    void testIgnoreNonSaslNamespaceFailure() {
        sendStreamHeader();
        sendXml("<failure xmlns='urn:example:custom'><not-authorized/><text>ignored</text></failure>");
        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("应忽略非 streams 命名空间的 features 顶层元素")
    void testIgnoreNonStreamNamespaceFeatures() {
        sendStreamHeader();
        sendXml("<features xmlns='urn:example:custom'><bind/></features>");
        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("应正确解析 IQ get")
    void testParseIqGet() {
        sendStreamHeader();
        sendXml("<iq type='get' id='ping1' from='user@example.com' to='example.com'><ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.GET, iq.getType());
        assertEquals("ping1", iq.getId());
        assertEquals("user@example.com", iq.getFrom());
        assertEquals("example.com", iq.getTo());
        assertNotNull(iq.getExtension(Ping.class).orElse(null));
    }

    @Test
    @DisplayName("应正确解析 IQ result with Bind")
    void testParseIqResultWithBind() {
        sendStreamHeader();
        sendXml("<iq type='result' id='bind1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><jid>user@example.com/resource</jid></bind></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.RESULT, iq.getType());
        Bind bind = iq.getExtension(Bind.class).orElse(null);
        assertNotNull(bind);
        assertEquals("user@example.com/resource", bind.getJid());
    }

    @Test
    @DisplayName("应正确解析 Message")
    void testParseMessage() {
        sendStreamHeader();
        sendXml("<message type='chat' id='msg1' from='alice@example.com' to='bob@example.com'><body>Hello, Bob!</body><subject>Greeting</subject></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals(Message.Type.CHAT, message.getType());
        assertEquals("msg1", message.getId());
        assertEquals("Hello, Bob!", message.getBody());
        assertEquals("Greeting", message.getSubject());
    }

    @Test
    @DisplayName("应正确解析 Presence")
    void testParsePresence() {
        sendStreamHeader();
        sendXml("<presence id='pres1' from='user@example.com/mobile'><show>chat</show><status>Ready to chat</status><priority>10</priority></presence>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Presence.class, msg);

        Presence presence = (Presence) msg;
        assertEquals(Presence.Type.AVAILABLE, presence.getType());
        assertEquals("pres1", presence.getId());
        assertEquals("chat", presence.getShow());
        assertEquals("Ready to chat", presence.getStatus());
        assertEquals(10, presence.getPriority());
    }

    @Test
    @DisplayName("应在同一缓冲区保留后续半包并等待补全")
    void testKeepTrailingPartialStanzaInBuffer() {
        sendStreamHeader();

        String firstIq = "<iq type='get' id='test1'><ping xmlns='urn:xmpp:ping'/></iq>";
        String secondIqPart = "<iq type='get' id='test2'><ping xmlns='urn:xmpp:ping'/>";
        sendXml(firstIq + secondIqPart);

        Object first = channel.readInbound();
        assertNotNull(first);
        assertInstanceOf(Iq.class, first);
        assertEquals("test1", ((Iq) first).getId());
        assertNull(channel.readInbound());

        sendXml("</iq>");

        Object second = channel.readInbound();
        assertNotNull(second);
        assertInstanceOf(Iq.class, second);
        assertEquals("test2", ((Iq) second).getId());
    }

    @Test
    @DisplayName("应在分片输入补全后再输出完整 stanza")
    void testSegmentedStanzaAcrossInboundWrites() {
        sendStreamHeader();

        sendXml("<message type='chat' id='split-1'><body>Hello");
        assertNull(channel.readInbound());

        sendXml(" world</body></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);
        assertEquals("split-1", ((Message) msg).getId());
        assertEquals("Hello world", ((Message) msg).getBody());
    }

    @Test
    @DisplayName("应处理消息体中的特殊字符")
    void testSpecialCharacters() {
        sendStreamHeader();
        sendXml("<message type='chat' id='msg-special'><body>Hello &amp; welcome! &lt;tag&gt; &quot;quoted&quot;</body></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);
        assertEquals("Hello & welcome! <tag> \"quoted\"", ((Message) msg).getBody());
    }

    private void sendXml(String xml) {
        channel.writeInbound(Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8));
    }

    private void sendStreamHeader() {
        sendXml("<?xml version='1.0'?><stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>");
    }

    private void sendStreamFeatures(String content) {
        sendXml("<stream:features xmlns:stream='http://etherx.jabber.org/streams'>" + content + "</stream:features>");
    }
}
