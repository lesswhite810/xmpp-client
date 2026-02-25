package com.example.xmpp.net;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmppNettyHandler 状态机测试。
 */
class XmppNettyHandlerTest {

    /**
     * 测试流头解析 - stream 元素被跳过。
     */
    @Test
    void testStreamHeaderParsing() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        sendXml(channel, "<stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams' from='server.com' id='test-123'>");

        Object msg = channel.readInbound();
        // stream 元素被跳过
        assertNull(msg, "stream element should be skipped");

        channel.finish();
    }

    /**
     * 测试流特性解析。
     */
    @Test
    void testStreamFeaturesParsing() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        sendXml(channel, "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>");
        // stream 元素被跳过，不需要消费

        sendXml(channel, "<stream:features xmlns:stream='http://etherx.jabber.org/streams'>" +
                "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>" +
                "<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>" +
                "<mechanism>PLAIN</mechanism>" +
                "<mechanism>SCRAM-SHA-1</mechanism>" +
                "</mechanisms>" +
                "</stream:features>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertTrue(msg instanceof StreamFeatures);

        StreamFeatures features = (StreamFeatures) msg;
        assertTrue(features.isStarttlsAvailable());
        assertNotNull(features.getMechanisms());
        assertEquals(2, features.getMechanisms().size());
        assertTrue(features.getMechanisms().contains("PLAIN"));
        assertTrue(features.getMechanisms().contains("SCRAM-SHA-1"));

        channel.finish();
    }

    /**
     * 测试 PING IQ 识别和处理。
     */
    @Test
    void testPingIqHandling() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // 建立流上下文 (stream 元素被跳过)
        sendXml(channel, "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>");
        // stream 元素被跳过，不需要消费

        // 发送 PING IQ
        sendXml(channel, "<iq type='get' id='ping-1' from='server.com'>" +
                "<ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertTrue(msg instanceof Iq);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.get, iq.getType());
        assertEquals("ping-1", iq.getId());
        assertEquals("server.com", iq.getFrom());
        assertTrue(iq.getChildElement() instanceof Ping);

        channel.finish();
    }

    private void sendXml(EmbeddedChannel channel, String xml) {
        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        channel.writeInbound(buf);
    }
}
