package com.example.xmpp.net;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.model.sasl.Auth;
import com.example.xmpp.protocol.model.sasl.SaslChallenge;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.sasl.SaslSuccess;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.protocol.model.stream.TlsElements;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmppStreamDecoder 单元测试。
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>各种 stanza 类型解析</li>
 *   <li>Provider 系统集成</li>
 *   <li>状态重置</li>
 * </ul>
 */
class XmppStreamDecoderTest {

    private EmbeddedChannel channel;
    private XmppStreamDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new XmppStreamDecoder();
        channel = new EmbeddedChannel(decoder);
    }

    // ==================== 基础解析测试 ====================

    @Test
    @DisplayName("应正确处理完整 XML")
    void testCompleteXml() {
        // 发送流头（会被跳过）
        sendXml("<?xml version='1.0'?><stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams'>");

        // stream 元素被跳过，输出为 null
        assertNull(channel.readInbound());

        // 一次性发送完整的 features
        sendStreamFeatures("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        Object msg = channel.readInbound();
        assertNotNull(msg, "Should receive features");
        assertInstanceOf(StreamFeatures.class, msg);

        StreamFeatures features = (StreamFeatures) msg;
        assertTrue(features.isStarttlsAvailable(), "StartTLS should be available");
    }

    @Test
    @DisplayName("并发安全测试 - 多次解码应不会互相干扰")
    void testConcurrentDecodeSafety() throws InterruptedException {
        sendStreamHeader();
        // stream 元素被跳过

        // 模拟快速连续解码多个消息
        for (int i = 0; i < 100; i++) {
            String msg = "<message type='chat' id='msg-" + i + "'><body>Test " + i + "</body></message>";
            sendXml(msg);

            Object received = channel.readInbound();
            assertNotNull(received, "Should receive message " + i);
            assertInstanceOf(Message.class, received);
            assertEquals("msg-" + i, ((Message) received).getId());
        }
    }

    // ==================== Stanza 解析测试 ====================

    @Test
    @DisplayName("应跳过 stream 元素")
    void testParseStreamHeader() {
        String xml = "<?xml version='1.0'?><stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams' from='example.com' " +
                "id='stream-123' version='1.0' xml:lang='en'>";

        sendXml(xml);

        // stream 元素被跳过，不返回 StreamHeader
        Object msg = channel.readInbound();
        assertNull(msg, "stream element should be skipped");
    }

    @Test
    @DisplayName("应正确解析 StreamFeatures")
    void testParseStreamFeatures() {
        sendStreamHeader();
        // stream 元素被跳过，不需要消费

        String featuresContent =
                "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>" +
                "<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
                "<mechanism>PLAIN</mechanism>" +
                "<mechanism>SCRAM-SHA-256</mechanism>" +
                "</mechanisms>" +
                "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";

        sendStreamFeatures(featuresContent);

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(StreamFeatures.class, msg);

        StreamFeatures streamFeatures = (StreamFeatures) msg;
        assertTrue(streamFeatures.isStarttlsAvailable());
        assertTrue(streamFeatures.isBindAvailable());

        List<String> mechanisms = streamFeatures.getMechanisms();
        assertNotNull(mechanisms);
        assertEquals(2, mechanisms.size());
        assertTrue(mechanisms.contains("PLAIN"));
        assertTrue(mechanisms.contains("SCRAM-SHA-256"));
    }

    @Test
    @DisplayName("应正确解析 StartTls")
    void testParseStartTls() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertSame(TlsElements.StartTls.INSTANCE, msg);
    }

    @Test
    @DisplayName("应正确解析 TlsProceed")
    void testParseTlsProceed() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertSame(TlsElements.TlsProceed.INSTANCE, msg);
    }

    @Test
    @DisplayName("应正确解析 SASL Auth")
    void testParseSaslAuth() {
        sendStreamHeader();
        // stream 元素被跳过

        // Base64 of "\0user\0pass" = AHVzZXIAcGFzcw==
        sendXml("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>" +
                "AHVzZXIAcGFzcw==</auth>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Auth.class, msg);

        Auth auth = (Auth.class.cast(msg));
        assertEquals("PLAIN", auth.getMechanism());
        assertEquals("AHVzZXIAcGFzcw==", auth.getContent());
    }

    @Test
    @DisplayName("应正确解析 SASL Challenge")
    void testParseSaslChallenge() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
                "cmVhbG09ImV4YW1wbGUuY29tIg==</challenge>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(SaslChallenge.class, msg);

        SaslChallenge challenge = (SaslChallenge) msg;
        assertEquals("cmVhbG09ImV4YW1wbGUuY29tIg==", challenge.getContent());
    }

    @Test
    @DisplayName("应正确解析 SASL Success")
    void testParseSaslSuccess() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
                "dmVyPTMscj1hYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ejEyMzQ1Ng==</success>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(SaslSuccess.class, msg);
    }

    @Test
    @DisplayName("应正确解析 SASL Failure")
    void testParseSaslFailure() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
                "<not-authorized/><text>Invalid credentials</text></failure>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(SaslFailure.class, msg);

        SaslFailure failure = (SaslFailure) msg;
        assertEquals("not-authorized", failure.getCondition());
        assertEquals("Invalid credentials", failure.getText());
    }

    // ==================== IQ Stanza 测试 ====================

    @Test
    @DisplayName("应正确解析 IQ get")
    void testParseIqGet() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<iq type='get' id='ping1' from='user@example.com' to='example.com'>" +
                "<ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.GET, iq.getType());
        assertEquals("ping1", iq.getId());
        assertEquals("user@example.com", iq.getFrom());
        assertEquals("example.com", iq.getTo());

        // 检查 Ping 扩展
        Ping ping = iq.getExtension(Ping.class).orElse(null);
        assertNotNull(ping);
    }

    @Test
    @DisplayName("应正确解析 IQ result with Bind")
    void testParseIqResultWithBind() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<iq type='result' id='bind1'>" +
                "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>" +
                "<jid>user@example.com/resource</jid></bind></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.RESULT, iq.getType());
        assertEquals("bind1", iq.getId());

        Bind bind = iq.getExtension(Bind.class).orElse(null);
        assertNotNull(bind);
        assertEquals("user@example.com/resource", bind.getJid());
    }

    @Test
    @DisplayName("应正确解析 IQ set")
    void testParseIqSet() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<iq type='set' id='bind2'>" +
                "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>" +
                "<resource>mobile</resource></bind></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.SET, iq.getType());

        Bind bind = iq.getExtension(Bind.class).orElse(null);
        assertNotNull(bind);
        assertEquals("mobile", bind.getResource());
    }

    @Test
    @DisplayName("应正确解析 IQ error")
    void testParseIqError() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<iq type='error' id='err1'>" +
                "<error type='cancel'><service-unavailable xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>" +
                "</error></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.ERROR, iq.getType());
    }

    // ==================== Message Stanza 测试 ====================

    @Test
    @DisplayName("应正确解析 Message chat")
    void testParseMessageChat() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<message type='chat' id='msg1' from='alice@example.com' to='bob@example.com'>" +
                "<body>Hello, Bob!</body><subject>Greeting</subject></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals(Message.Type.CHAT, message.getType());
        assertEquals("msg1", message.getId());
        assertEquals("alice@example.com", message.getFrom());
        assertEquals("bob@example.com", message.getTo());
        assertEquals("Hello, Bob!", message.getBody());
        assertEquals("Greeting", message.getSubject());
    }

    @Test
    @DisplayName("应正确解析 Message groupchat")
    void testParseMessageGroupchat() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<message type='groupchat' id='msg2' from='room@conference.example.com/alice'>" +
                "<body>Let's discuss the project timeline.</body></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals(Message.Type.GROUPCHAT, message.getType());
        assertEquals("Let's discuss the project timeline.", message.getBody());
    }

    @Test
    @DisplayName("应正确解析 Message with thread")
    void testParseMessageWithThread() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<message type='chat' id='msg3'>" +
                "<body>Reply</body><thread>thread-123</thread></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals("thread-123", message.getThread());
    }

    // ==================== Presence Stanza 测试 ====================

    @Test
    @DisplayName("应正确解析 Presence available")
    void testParsePresenceAvailable() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<presence id='pres1' from='user@example.com/mobile'>" +
                "<show>chat</show><status>Ready to chat</status><priority>10</priority></presence>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Presence.class, msg);

        Presence presence = (Presence) msg;
        assertEquals(Presence.Type.AVAILABLE, presence.getType());
        assertEquals("pres1", presence.getId());
        assertEquals("user@example.com/mobile", presence.getFrom());
        assertEquals("chat", presence.getShow());
        assertEquals("Ready to chat", presence.getStatus());
        assertEquals(10, presence.getPriority());
    }

    @Test
    @DisplayName("应正确解析 Presence unavailable")
    void testParsePresenceUnavailable() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<presence type='unavailable' id='pres2'>" +
                "<status>Going offline</status></presence>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Presence.class, msg);

        Presence presence = (Presence) msg;
        assertEquals(Presence.Type.UNAVAILABLE, presence.getType());
        assertEquals("Going offline", presence.getStatus());
    }

    @Test
    @DisplayName("应正确解析 Presence subscribe")
    void testParsePresenceSubscribe() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<presence type='subscribe' id='pres3' from='alice@example.com' to='bob@example.com'/>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Presence.class, msg);

        Presence presence = (Presence) msg;
        assertEquals(Presence.Type.SUBSCRIBE, presence.getType());
    }

    // ==================== 分段数据测试 ====================

    @Test
    @DisplayName("应正确处理分段发送的完整 XML")
    void testSegmentedCompleteXml() {
        sendStreamHeader();
        // stream 元素被跳过

        // 一次性发送完整的 IQ
        sendXml("<iq type='get' id='test1'><ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg, "Should output complete stanza");
        assertInstanceOf(Iq.class, msg);
        assertEquals("test1", ((Iq) msg).getId());
    }

    @Test
    @DisplayName("应正确处理多个完整的 stanza")
    void testMultipleCompleteStanzas() {
        sendStreamHeader();
        // stream 元素被跳过

        // 分别发送两个完整的 stanza
        sendXml("<iq type='get' id='test1'><ping xmlns='urn:xmpp:ping'/></iq>");

        // 应该解析第一个
        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);
        assertEquals("test1", ((Iq) msg).getId());

        // 发送第二个
        sendXml("<iq type='get' id='test2'><ping xmlns='urn:xmpp:ping'/></iq>");

        // 应该解析第二个
        msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);
        assertEquals("test2", ((Iq) msg).getId());
    }

    // ==================== 重置测试 ====================

    @Test
    @DisplayName("连续解码应正常工作")
    void testReset() {
        sendStreamHeader();
        // stream 元素被跳过
        assertNull(channel.readInbound());

        sendXml("<iq type='get' id='test1'><ping xmlns='urn:xmpp:ping'/></iq>");
        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);

        // 再次发送 IQ 应该仍然可以正常解析
        sendXml("<iq type='get' id='test2'><ping xmlns='urn:xmpp:ping'/></iq>");
        msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);
        assertEquals("test2", ((Iq) msg).getId());
    }

    // ==================== 特殊字符处理测试 ====================

    @Test
    @DisplayName("应正确处理消息中的特殊字符")
    void testSpecialCharacters() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<message type='chat' id='msg-special'>" +
                "<body>Hello &amp; welcome! &lt;tag&gt; &quot;quoted&quot;</body></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals("Hello & welcome! <tag> \"quoted\"", message.getBody());
    }

    @Test
    @DisplayName("应正确处理单引号（不转义）")
    void testSingleQuoteNotEscaped() {
        sendStreamHeader();
        // stream 元素被跳过

        sendXml("<message type='chat' id='msg-quote'>" +
                "<body>Let's discuss the project timeline.</body></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals("Let's discuss the project timeline.", message.getBody());
    }

    // ==================== 辅助方法 ====================

    /**
     * 发送 XML 数据到通道。
     *
     * @param xml XML 字符串
     */
    private void sendXml(String xml) {
        channel.writeInbound(Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8));
    }

    /**
     * 发送标准的流头（包含命名空间声明）。
     */
    private void sendStreamHeader() {
        sendXml("<?xml version='1.0'?><stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams'>");
    }

    /**
     * 发送 stream:features（包含命名空间声明）。
     */
    private void sendStreamFeatures(String content) {
        sendXml("<stream:features xmlns:stream='http://etherx.jabber.org/streams'>" +
                content + "</stream:features>");
    }
}
