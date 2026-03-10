package com.example.xmpp.net;

import com.example.xmpp.protocol.model.stream.StreamError;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Stream error 解码测试。
 */
class XmppStreamErrorDecoderTest {

    @Test
    void testParseStreamError() {
        EmbeddedChannel channel = new EmbeddedChannel(new XmppStreamDecoder());

        channel.writeInbound(Unpooled.copiedBuffer(
                "<stream:error xmlns:stream='http://etherx.jabber.org/streams'>"
                        + "<not-authorized xmlns='urn:ietf:params:xml:ns:xmpp-streams'/>"
                        + "<text xmlns='urn:ietf:params:xml:ns:xmpp-streams'>Authentication required</text>"
                        + "<by xmlns='urn:ietf:params:xml:ns:xmpp-streams'>example.com</by>"
                        + "</stream:error>",
                StandardCharsets.UTF_8));

        Object inbound = channel.readInbound();
        assertNotNull(inbound);
        StreamError streamError = assertInstanceOf(StreamError.class, inbound);
        assertEquals(StreamError.Condition.NOT_AUTHORIZED, streamError.getCondition());
        assertEquals("Authentication required", streamError.getText());
        assertEquals("example.com", streamError.getBy());
    }
}