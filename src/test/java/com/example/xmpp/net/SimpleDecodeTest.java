package com.example.xmpp.net;

import com.example.xmpp.protocol.model.Iq;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify decoder message flow.
 */
class SimpleDecodeTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleDecodeTest.class);

    @Test
    void testMessageFlow() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // Step 1: Send stream header (被跳过)
        log.debug("=== Step 1: Send stream header ===");
        String streamHeader = "<?xml version='1.0'?><stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams'>";
        ByteBuf buf1 = Unpooled.copiedBuffer(streamHeader, StandardCharsets.UTF_8);
        boolean writeResult1 = channel.writeInbound(buf1);
        log.debug("writeInbound result: {}", writeResult1);

        Object msg1 = channel.readInbound();
        log.debug("First readInbound: {}", msg1 != null ? msg1.getClass().getSimpleName() : "null");

        // stream 元素被跳过
        assertNull(msg1, "stream element should be skipped");

        // Step 2: Send IQ
        log.debug("=== Step 2: Send IQ ===");
        String iqXml = "<iq type='get' id='ping1'><ping xmlns='urn:xmpp:ping'/></iq>";
        ByteBuf buf2 = Unpooled.copiedBuffer(iqXml, StandardCharsets.UTF_8);
        boolean writeResult2 = channel.writeInbound(buf2);
        log.debug("writeInbound result: {}", writeResult2);

        Object msg2 = channel.readInbound();
        log.debug("Second readInbound: {}", msg2 != null ? msg2.getClass().getSimpleName() : "null");

        // Verify IQ
        assertNotNull(msg2, "Should receive IQ");
        assertInstanceOf(Iq.class, msg2, "Second message should be Iq");
        assertEquals("ping1", ((Iq) msg2).getId());
    }
}
