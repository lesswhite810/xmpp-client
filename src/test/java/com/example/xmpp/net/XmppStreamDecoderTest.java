package com.example.xmpp.net;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.ExtensionElementProvider;
import com.example.xmpp.protocol.ProviderRegistry;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.model.sasl.Auth;
import com.example.xmpp.protocol.model.sasl.SaslChallenge;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.sasl.SaslResponse;
import com.example.xmpp.protocol.model.sasl.SaslSuccess;
import com.example.xmpp.protocol.model.stream.StreamError;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.protocol.model.stream.TlsElements;
import com.example.xmpp.util.XmlStringBuilder;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("应正确解析 TLS proceed")
    void testParseTlsProceed() {
        sendStreamHeader();
        sendXml("<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertSame(TlsElements.TlsProceed.INSTANCE, msg);
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
        assertEquals("PLAIN", auth.mechanism());
        assertEquals("AHVzZXIAcGFzcw==", auth.content());
    }

    @Test
    @DisplayName("空 SASL auth 内容应解析为 null")
    void testParseSaslAuthWithEmptyContent() {
        sendStreamHeader();
        sendXml("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'></auth>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Auth.class, msg);
        assertNull(((Auth) msg).content());
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
        assertEquals("not-authorized", failure.condition());
        assertEquals("Invalid credentials", failure.text());
    }

    @Test
    @DisplayName("应为缺省 SASL failure 填充默认 condition")
    void testParseSaslFailureWithDefaultCondition() {
        sendStreamHeader();
        sendXml("<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><text>denied</text></failure>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(SaslFailure.class, msg);
        assertEquals("undefined-condition", ((SaslFailure) msg).condition());
        assertEquals("denied", ((SaslFailure) msg).text());
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
    @DisplayName("应解析带 by 的 StreamError")
    void testParseStreamErrorWithBy() {
        sendStreamHeader();
        sendXml("<error xmlns='http://etherx.jabber.org/streams'>"
                + "<see-other-host/><by>proxy.example.com</by><text>redirect</text></error>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(StreamError.class, msg);

        StreamError error = (StreamError) msg;
        assertEquals(StreamError.Condition.SEE_OTHER_HOST, error.getCondition());
        assertEquals("proxy.example.com", error.getBy());
        assertEquals("redirect", error.getText());
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
        sendXml("<message type='chat' id='msg1' from='alice@example.com' to='bob@example.com'>"
                + "<body>Hello, Bob!</body><subject>Greeting</subject><thread>thread-1</thread></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals(Message.Type.CHAT, message.getType());
        assertEquals("msg1", message.getId());
        assertEquals("Hello, Bob!", message.getBody());
        assertEquals("Greeting", message.getSubject());
        assertEquals("thread-1", message.getThread());
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

    @Test
    @DisplayName("应跳过处理指令和注释后再解析消息")
    void testSkipProcessingInstructionAndCommentBeforeMessage() {
        sendStreamHeader();
        sendXml("<?pi test?><message type='chat' id='msg-pi'><body>payload</body></message><!--tail-->");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);
        assertEquals("msg-pi", ((Message) msg).getId());
        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("应解析包含 CDATA 的消息")
    void testParseMessageWithCdata() {
        sendStreamHeader();
        sendXml("<message type='chat' id='msg-cdata'><body><![CDATA[<xml>&raw]]></body></message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);
        assertEquals("<xml>&raw", ((Message) msg).getBody());
    }

    @Test
    @DisplayName("应解析顶层 SASL challenge response success")
    void testParseTopLevelSaslChallengeResponseAndSuccess() {
        sendStreamHeader();
        sendXml("<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>Y2hhbGxlbmdl</challenge>");
        sendXml("<response xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>cmVzcG9uc2U=</response>");
        sendXml("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>c3VjY2Vzcw==</success>");

        Object challenge = channel.readInbound();
        assertNotNull(challenge);
        assertInstanceOf(SaslChallenge.class, challenge);
        assertEquals("Y2hhbGxlbmdl",
                ((SaslChallenge) challenge).content());

        Object response = channel.readInbound();
        assertNotNull(response);
        assertInstanceOf(SaslResponse.class, response);
        assertEquals("cmVzcG9uc2U=",
                ((SaslResponse) response).content());

        Object success = channel.readInbound();
        assertNotNull(success);
        assertInstanceOf(SaslSuccess.class, success);
        assertEquals("c3VjY2Vzcw==",
                ((SaslSuccess) success).content());
    }

    @Test
    @DisplayName("应忽略未知顶层 SASL 元素")
    void testIgnoreUnknownTopLevelSaslElement() {
        sendStreamHeader();
        sendXml("<abort xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>");

        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("Presence priority 非数字时应忽略")
    void testIgnoreInvalidPresencePriority() {
        sendStreamHeader();
        sendXml("<presence id='pres-invalid'><priority>invalid</priority></presence>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Presence.class, msg);
        assertNull(((Presence) msg).getPriority());
    }

    @Test
    @DisplayName("IQ error 的未知 type 应保留 condition 和 text")
    void testParseIqErrorWithUnknownType() {
        sendStreamHeader();
        sendXml("<iq type='error' id='iq-error'>"
                + "<error type='not-a-real-type'>"
                + "<feature-not-implemented xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>"
                + "<text xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>unsupported</text>"
                + "</error></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.ERROR, iq.getType());
        assertNotNull(iq.getError());
        assertEquals(XmppError.Condition.FEATURE_NOT_IMPLEMENTED, iq.getError().getCondition());
        assertEquals("unsupported", iq.getError().getText());
        assertEquals(XmppError.Type.CANCEL, iq.getError().getType());
    }

    @Test
    @DisplayName("未知扩展元素应使用通用解析器")
    void testFallbackToGenericExtensionParser() {
        sendStreamHeader();
        sendXml("<message type='chat' id='msg-generic'>"
                + "<x xmlns='urn:test:generic' foo='bar'><item>value</item></x>"
                + "</message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals(1, message.getExtensions().size());
        assertInstanceOf(GenericExtensionElement.class, message.getExtensions().getFirst());

        GenericExtensionElement extension = (GenericExtensionElement) message.getExtensions().getFirst();
        assertEquals("bar", extension.getAttributeValue("foo"));
        assertTrue(extension.getFirstChild("item").isPresent());
        assertEquals("value", extension.getFirstChild("item").orElseThrow().getText());
    }

    @Test
    @DisplayName("未知扩展 mixed content 应保留文本与子元素顺序")
    void testFallbackToGenericExtensionParserPreservesMixedContentOrder() {
        sendStreamHeader();
        sendXml("<message type='chat' id='msg-mixed-generic'>"
                + "<x xmlns='urn:test:generic'>before<item>value</item>after</x>"
                + "</message>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Message.class, msg);

        Message message = (Message) msg;
        assertEquals(1, message.getExtensions().size());
        assertInstanceOf(GenericExtensionElement.class, message.getExtensions().getFirst());

        GenericExtensionElement extension = (GenericExtensionElement) message.getExtensions().getFirst();
        assertEquals(3, extension.getContentNodes().size());
        assertEquals("beforeafter", extension.getText());
        assertEquals("<x xmlns=\"urn:test:generic\">before<item xmlns=\"urn:test:generic\">value</item>after</x>",
                extension.toXml());
    }

    @Test
    @DisplayName("Provider 解析失败时应回退到通用解析器并保留原扩展边界")
    void testIgnoreExtensionWhenProviderThrowsParseException() {
        ProviderRegistry registry = ProviderRegistry.getInstance();
        ThrowingExtensionProvider provider = new ThrowingExtensionProvider("broken", "urn:test:broken");
        registry.registerProvider(provider);

        try {
            sendStreamHeader();
            sendXml("<message type='chat' id='msg-broken'>"
                    + "<broken xmlns='urn:test:broken'><value>ignored</value></broken>"
                    + "</message>");

            Object msg = channel.readInbound();
            assertNotNull(msg);
            assertInstanceOf(Message.class, msg);
            Message message = (Message) msg;
            assertEquals(1, message.getExtensions().size());
            assertInstanceOf(GenericExtensionElement.class, message.getExtensions().getFirst());
            GenericExtensionElement extension = (GenericExtensionElement) message.getExtensions().getFirst();
            assertEquals("broken", extension.getElementName());
            assertEquals("urn:test:broken", extension.getNamespace());
            assertTrue(extension.getFirstChild("value").isPresent());
            assertEquals("ignored", extension.getFirstChild("value").orElseThrow().getText());
        } finally {
            registry.removeProvider("broken", "urn:test:broken");
        }
    }

    @Test
    @DisplayName("Provider 部分消费事件后解析失败时仍应回退到通用解析器")
    void testFallbackToGenericExtensionWhenProviderFailsAfterPartialConsumption() {
        ProviderRegistry registry = ProviderRegistry.getInstance();
        PartiallyConsumingExtensionProvider provider =
                new PartiallyConsumingExtensionProvider("broken-partial", "urn:test:broken");
        registry.registerProvider(provider);

        try {
            sendStreamHeader();
            sendXml("<message type='chat' id='msg-broken-partial'>"
                    + "<broken-partial xmlns='urn:test:broken'><value>ignored</value></broken-partial>"
                    + "</message>");

            Object msg = channel.readInbound();
            assertNotNull(msg);
            assertInstanceOf(Message.class, msg);

            Message message = (Message) msg;
            assertEquals(1, message.getExtensions().size());
            assertInstanceOf(GenericExtensionElement.class, message.getExtensions().getFirst());
            GenericExtensionElement extension = (GenericExtensionElement) message.getExtensions().getFirst();
            assertEquals("broken-partial", extension.getElementName());
            assertEquals("urn:test:broken", extension.getNamespace());
            assertTrue(extension.getFirstChild("value").isPresent());
            assertEquals("ignored", extension.getFirstChild("value").orElseThrow().getText());
        } finally {
            registry.removeProvider("broken-partial", "urn:test:broken");
        }
    }

    @Test
    @DisplayName("应跳过格式错误的 stanza 并继续解析后续消息")
    void testMalformedStanzaIsDroppedAndFollowingMessageStillParses() {
        sendStreamHeader();
        sendXml("<message type='chat' id='broken'><body>broken</body></iq>");
        assertNull(channel.readInbound());

        sendXml("<iq type='get' id='after-broken'><ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);
        assertEquals("after-broken", ((Iq) msg).getId());
        assertNotNull(((Iq) msg).getChildElement());
        assertEquals("ping", ((Iq) msg).getChildElement().getElementName());
        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("应跳过嵌套闭合标签错配的 stanza 并继续解析后续消息")
    void testNestedMismatchedClosingTagIsDroppedAndFollowingMessageStillParses() {
        sendStreamHeader();
        sendXml("<message type='chat' id='broken'><body>broken</message>");
        assertNull(channel.readInbound());

        sendXml("<iq type='get' id='after-nested-broken'><ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);
        assertEquals("after-nested-broken", ((Iq) msg).getId());
        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("parseFromByteBuf 应跳过嵌套闭合标签错配的 stanza")
    void testParseFromByteBufSkipsNestedMismatchedClosingTag() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        var buffer = Unpooled.copiedBuffer(
                "<message type='chat' id='broken'><body>broken</message>"
                        + "<iq type='get' id='after-buffer-broken'><ping xmlns='urn:xmpp:ping'/></iq>",
                StandardCharsets.UTF_8);

        List<Object> elements = decoder.parseFromByteBuf(buffer);

        assertEquals(1, elements.size());
        assertInstanceOf(Iq.class, elements.getFirst());
        assertEquals("after-buffer-broken", ((Iq) elements.getFirst()).getId());
    }

    @Test
    @DisplayName("应丢弃超过最大嵌套深度的 stanza 并继续解析后续消息")
    void testTooDeepStanzaIsDroppedAndFollowingMessageStillParses() {
        sendStreamHeader();
        sendXml(buildDeeplyNestedMessage("too-deep", 257));
        assertNull(channel.readInbound());

        sendXml("<iq type='get' id='after-too-deep'><ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertInstanceOf(Iq.class, msg);
        assertEquals("after-too-deep", ((Iq) msg).getId());
        assertNull(channel.readInbound());
    }

    @Test
    @DisplayName("parseFromByteBuf 应跳过超过最大嵌套深度的 stanza")
    void testParseFromByteBufSkipsTooDeepStanza() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        var buffer = Unpooled.copiedBuffer(
                buildDeeplyNestedMessage("too-deep-buffer", 257)
                        + "<iq type='get' id='after-too-deep-buffer'><ping xmlns='urn:xmpp:ping'/></iq>",
                StandardCharsets.UTF_8);

        List<Object> elements = decoder.parseFromByteBuf(buffer);

        assertEquals(1, elements.size());
        assertInstanceOf(Iq.class, elements.getFirst());
        assertEquals("after-too-deep-buffer", ((Iq) elements.getFirst()).getId());
    }

    @Test
    @DisplayName("parseFromByteBuf 应跳过前导噪音和结束标签")
    void testParseFromByteBufSkipsNoiseAndClosingTag() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        var buffer = Unpooled.copiedBuffer(
                "noise</ignored><message type='chat' id='msg-buffer'><body>buffer</body></message>",
                StandardCharsets.UTF_8);

        List<Object> elements = decoder.parseFromByteBuf(buffer);

        assertEquals(1, elements.size());
        assertInstanceOf(Message.class, elements.getFirst());
        assertEquals("msg-buffer", ((Message) elements.getFirst()).getId());
    }

    @Test
    @DisplayName("parseFromByteBuf 遇到未闭合注释时应等待更多数据")
    void testParseFromByteBufReturnsEmptyWhenCommentIncomplete() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        var buffer = Unpooled.copiedBuffer("<!-- unfinished", StandardCharsets.UTF_8);

        List<Object> elements = decoder.parseFromByteBuf(buffer);

        assertTrue(elements.isEmpty());
        assertFalse(buffer.readerIndex() > 0);
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

    private String buildDeeplyNestedMessage(String id, int nestedDepth) {
        StringBuilder xml = new StringBuilder("<message type='chat' id='")
                .append(id)
                .append("'>");
        for (int index = 0; index < nestedDepth; index++) {
            xml.append("<x>");
        }
        xml.append("payload");
        for (int index = 0; index < nestedDepth; index++) {
            xml.append("</x>");
        }
        xml.append("</message>");
        return xml.toString();
    }

    private static class ThrowingExtensionProvider implements ExtensionElementProvider<ExtensionElement> {

        private final String elementName;

        private final String namespace;

        private ThrowingExtensionProvider(String elementName, String namespace) {
            this.elementName = elementName;
            this.namespace = namespace;
        }

        @Override
        public ExtensionElement parse(XMLEventReader reader) throws XmppParseException {
            throw new XmppParseException("broken provider");
        }

        @Override
        public void serialize(ExtensionElement object, XmlStringBuilder xml) {
            throw new UnsupportedOperationException("Not needed in tests");
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

    private static final class PartiallyConsumingExtensionProvider extends ThrowingExtensionProvider {

        private PartiallyConsumingExtensionProvider(String elementName, String namespace) {
            super(elementName, namespace);
        }

        @Override
        public ExtensionElement parse(XMLEventReader reader) throws XmppParseException {
            try {
                while (reader.hasNext()) {
                    XMLEvent event = reader.nextEvent();
                    if (event.isEndElement() && "value".equals(event.asEndElement().getName().getLocalPart())) {
                        break;
                    }
                }
            } catch (XMLStreamException e) {
                throw new XmppParseException("broken provider", e);
            }
            throw new XmppParseException("broken provider after partial consumption");
        }
    }
}
