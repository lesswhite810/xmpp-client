package com.example.xmpp.net;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XmppNettyHandler 状态机测试。
 */
class XmppNettyHandlerTest {

    @Test
    void testStreamHeaderParsing() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        sendXml(channel, "<stream:stream xmlns='jabber:client' "
                + "xmlns:stream='http://etherx.jabber.org/streams' from='server.com' id='test-123'>");

        Object msg = channel.readInbound();
        assertNull(msg, "stream element should be skipped");

        channel.finish();
    }

    @Test
    void testStreamFeaturesParsing() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        sendXml(channel, "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>");
        sendXml(channel, "<stream:features xmlns:stream='http://etherx.jabber.org/streams'>"
                + "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"
                + "<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>"
                + "<mechanism>PLAIN</mechanism>"
                + "<mechanism>SCRAM-SHA-1</mechanism>"
                + "</mechanisms>"
                + "</stream:features>");

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

    @Test
    void testPingIqHandling() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        sendXml(channel, "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>");
        sendXml(channel, "<iq type='get' id='ping-1' from='server.com'>"
                + "<ping xmlns='urn:xmpp:ping'/></iq>");

        Object msg = channel.readInbound();
        assertNotNull(msg);
        assertTrue(msg instanceof Iq);

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.GET, iq.getType());
        assertEquals("ping-1", iq.getId());
        assertEquals("server.com", iq.getFrom());
        assertTrue(iq.getChildElement() instanceof Ping);

        channel.finish();
    }

    @Test
    void testFeaturesWithMechanismsAndBindStartSaslBeforeBind() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .enabledSaslMechanisms(Set.of("SCRAM-SHA-1"))
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new XmppNettyHandler(config, connection));

        try {
            drainOutboundStrings(channel);
            channel.writeInbound(StreamFeatures.builder()
                    .mechanisms(List.of("SCRAM-SHA-1"))
                    .bindAvailable(true)
                    .build());

            String outbound = drainOutboundStrings(channel);
            assertTrue(outbound.contains("<auth "), outbound);
            assertTrue(outbound.contains("mechanism=\"SCRAM-SHA-1\""), outbound);
            assertTrue(!outbound.contains("<iq "), outbound);
            assertTrue(!connection.getConnectionReadyFuture().isDone());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testSaslWriteFailureCompletesConnectionFutureImmediately() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .enabledSaslMechanisms(Set.of("SCRAM-SHA-1"))
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new FailingOutboundHandler(), new XmppNettyHandler(config, connection));

        try {
            drainOutboundStrings(channel);
            channel.writeInbound(StreamFeatures.builder()
                    .mechanisms(List.of("SCRAM-SHA-1"))
                    .build());
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
            assertTrue(connection.getConnectionReadyFuture().isCompletedExceptionally());

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.getConnectionReadyFuture().join());
            XmppAuthException cause = assertInstanceOf(XmppAuthException.class, exception.getCause());
            assertTrue(cause.getMessage().contains("Failed to send SASL stanza"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private void sendXml(EmbeddedChannel channel, String xml) {
        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        channel.writeInbound(buf);
    }

    private String drainOutboundStrings(EmbeddedChannel channel) {
        StringBuilder builder = new StringBuilder();
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            if (outbound instanceof ByteBuf byteBuf) {
                builder.append(byteBuf.toString(StandardCharsets.UTF_8));
                byteBuf.release();
            }
        }
        return builder.toString();
    }

    private static final class FailingOutboundHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf byteBuf && byteBuf.toString(StandardCharsets.UTF_8).contains("<auth ")) {
                byteBuf.release();
                promise.setFailure(new IllegalStateException("simulated sasl write failure"));
                return;
            }
            ctx.write(msg, promise);
        }
    }
}
