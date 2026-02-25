package com.example.xmpp.net;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test features parsing.
 */
class FeaturesParseTest {

    private static final Logger log = LoggerFactory.getLogger(FeaturesParseTest.class);

    @Test
    void testBufferCompactionDebug() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // Step 1: Send stream header (skipped)
        log.debug("=== Step 1: Send stream header ===");
        sendXml(channel, "<?xml version='1.0'?><stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams'>");

        Object msg1 = channel.readInbound();
        log.debug("First readInbound: {}", msg1 != null ? msg1.getClass().getSimpleName() : "null");
        assertNull(msg1, "stream element should be skipped");

        // Step 2: Send features
        log.debug("=== Step 2: Send features ===");
        sendXml(channel, "<stream:features xmlns:stream='http://etherx.jabber.org/streams'>" +
                "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>");

        Object msg2 = channel.readInbound();
        log.debug("Second readInbound: {}", msg2 != null ? msg2.getClass().getSimpleName() : "null");
        assertNotNull(msg2, "Should receive Features");
        assertInstanceOf(StreamFeatures.class, msg2);

        // Step 3: Send IQ
        log.debug("=== Step 3: Send IQ ===");
        sendXml(channel, "<iq type='get' id='test1'><ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg3 = channel.readInbound();
        log.debug("Third readInbound: {}", msg3 != null ? msg3.getClass().getSimpleName() : "null");
        assertNotNull(msg3, "Should receive IQ");
        assertInstanceOf(Iq.class, msg3);
    }

    private void sendXml(EmbeddedChannel channel, String xml) {
        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        channel.writeInbound(buf);
    }
}
