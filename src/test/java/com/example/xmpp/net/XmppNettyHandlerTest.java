package com.example.xmpp.net;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.protocol.model.XmlSerializable;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.net.state.StateContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XmppNettyHandler 状态机测试。
 */
class XmppNettyHandlerTest {

    @Test
    void testChannelActiveBindsCurrentChannelBeforeFirstInboundEvent() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);

        EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertTrue(connection.isCurrentChannel(channel));

        channel.finishAndReleaseAll();
    }

    @Test
    void testChannelActiveIgnoredWhileDisconnectInProgress() {
        XmppEventBus.getInstance().clear();
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        AtomicInteger connectedCount = new AtomicInteger();

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CONNECTED,
                event -> connectedCount.incrementAndGet());

        connection.disconnect();
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertFalse(connection.isCurrentChannel(channel));
        assertEquals(0, connectedCount.get());

        channel.finishAndReleaseAll();
    }

    @Test
    void testBindSuccessPublishesAuthenticatedEventOnce() {
        XmppEventBus.getInstance().clear();
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);
        AtomicInteger authenticatedCount = new AtomicInteger();

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.AUTHENTICATED,
                event -> authenticatedCount.incrementAndGet());

        channel.writeInbound(StreamFeatures.builder()
                .bindAvailable(true)
                .build());
        channel.writeInbound(new Iq.Builder(Iq.Type.RESULT)
                .id("bind-1")
                .childElement(Bind.builder().jid("user@example.com/resource").build())
                .build());

        assertEquals(1, authenticatedCount.get());
        assertTrue(connection.getConnectionReadyFuture().isDone());

        channel.finishAndReleaseAll();
    }

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
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

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
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler, new FailingOutboundHandler());

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
            XmppNetworkException cause = assertInstanceOf(XmppNetworkException.class, exception.getCause());
            assertTrue(cause.getMessage().contains("Failed to send SASL auth stanza"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testStateDoesNotAdvanceToSaslAuthBeforeInitialAuthWriteSucceeds() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .enabledSaslMechanisms(Set.of("SCRAM-SHA-1"))
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        DelayedAuthWriteHandler delayedAuthWriteHandler = new DelayedAuthWriteHandler();
        EmbeddedChannel channel = newBoundChannel(connection, handler, delayedAuthWriteHandler);

        try {
            drainOutboundStrings(channel);
            channel.writeInbound(StreamFeatures.builder()
                    .mechanisms(List.of("SCRAM-SHA-1"))
                    .build());

            assertEquals("AWAITING_FEATURES", currentStateName(handler));

            delayedAuthWriteHandler.succeedPendingWrite();
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();

            assertEquals("SASL_AUTH", currentStateName(handler));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testClearedStateContextDoesNotAdvanceAfterDelayedSaslWriteSucceeds() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .enabledSaslMechanisms(Set.of("SCRAM-SHA-1"))
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        DelayedAuthWriteHandler delayedAuthWriteHandler = new DelayedAuthWriteHandler();
        EmbeddedChannel channel = newBoundChannel(connection, handler, delayedAuthWriteHandler);

        try {
            drainOutboundStrings(channel);
            channel.writeInbound(StreamFeatures.builder()
                    .mechanisms(List.of("SCRAM-SHA-1"))
                    .build());
            StateContext capturedStateContext = currentStateContext(handler);

            handler.invalidateStateContext();
            delayedAuthWriteHandler.succeedPendingWrite();
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();

            assertNull(currentStateName(handler));
            assertEquals("AWAITING_FEATURES", capturedStateContext.getCurrentStateName());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testClearedStateContextDoesNotAdvanceAfterDelayedTlsHandshakeReopenWriteSucceeds() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .usingDirectTLS(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        DelayedStreamWriteHandler delayedStreamWriteHandler = new DelayedStreamWriteHandler();
        EmbeddedChannel channel = newBoundChannel(connection, handler, delayedStreamWriteHandler);

        try {
            channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
            StateContext capturedStateContext = currentStateContext(handler);

            assertEquals("CONNECTING", capturedStateContext.getCurrentStateName());

            handler.invalidateStateContext();
            delayedStreamWriteHandler.succeedPendingWrite();
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();

            assertNull(currentStateName(handler));
            assertEquals("CONNECTING", capturedStateContext.getCurrentStateName());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testLateTlsHandshakeCompletionAfterStateClearedIsIgnored() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .usingDirectTLS(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

        try {
            handler.invalidateStateContext();

            assertDoesNotThrow(() -> channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS));
            assertNull(currentStateName(handler));
            assertFalse(connection.getConnectionReadyFuture().isDone());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testInitialStreamWriteFailureCompletesConnectionFutureImmediately() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler, new FailingStreamOpenHandler());

        try {
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.getConnectionReadyFuture().join());
            assertInstanceOf(XmppException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("Failed to open initial XMPP stream"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testBindWriteFailureCompletesConnectionFutureImmediately() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler, new FailingBindWriteHandler());

        try {
            drainOutboundStrings(channel);
            channel.writeInbound(StreamFeatures.builder()
                    .bindAvailable(true)
                    .build());
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.getConnectionReadyFuture().join());
            assertInstanceOf(XmppException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("Failed to send resource bind request"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testInitialPresenceWriteFailureDoesNotMarkSessionReady() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .sendPresence(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        AtomicInteger authenticatedCount = new AtomicInteger();
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler, new FailingPresenceWriteHandler());

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.AUTHENTICATED,
                event -> authenticatedCount.incrementAndGet());

        try {
            drainOutboundStrings(channel);
            channel.writeInbound(StreamFeatures.builder()
                    .bindAvailable(true)
                    .build());
            channel.writeInbound(new Iq.Builder(Iq.Type.RESULT)
                    .id("bind-1")
                    .childElement(Bind.builder().jid("user@example.com/resource").build())
                    .build());
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.getConnectionReadyFuture().join());
            assertInstanceOf(XmppException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("Failed to send initial presence"));
            assertEquals(0, authenticatedCount.get());
            assertFalse(connection.isAuthenticated());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testDelayedInitialPresenceDoesNotAuthenticateBeforeWriteSucceeds() {
        XmppEventBus.getInstance().clear();
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .sendPresence(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        DelayedPresenceWriteHandler delayedPresenceWriteHandler = new DelayedPresenceWriteHandler();
        EmbeddedChannel channel = newBoundChannel(connection, handler, delayedPresenceWriteHandler);
        AtomicInteger authenticatedCount = new AtomicInteger();

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.AUTHENTICATED,
                event -> authenticatedCount.incrementAndGet());

        try {
            drainOutboundStrings(channel);
            channel.writeInbound(StreamFeatures.builder()
                    .bindAvailable(true)
                    .build());
            channel.writeInbound(new Iq.Builder(Iq.Type.RESULT)
                    .id("bind-1")
                    .childElement(Bind.builder().jid("user@example.com/resource").build())
                    .build());

            assertEquals("BINDING", currentStateName(handler));
            assertFalse(connection.getConnectionReadyFuture().isDone());
            assertEquals(0, authenticatedCount.get());
            assertFalse(connection.isAuthenticated());

            delayedPresenceWriteHandler.succeedPendingWrite();
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();

            assertEquals("SESSION_ACTIVE", currentStateName(handler));
            assertTrue(connection.getConnectionReadyFuture().isDone());
            assertEquals(1, authenticatedCount.get());
            assertTrue(connection.isAuthenticated());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testClearedStateContextDoesNotMarkSessionReadyAfterDelayedPresenceWriteSucceeds() {
        XmppEventBus.getInstance().clear();
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .sendPresence(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        DelayedPresenceWriteHandler delayedPresenceWriteHandler = new DelayedPresenceWriteHandler();
        EmbeddedChannel channel = newBoundChannel(connection, handler, delayedPresenceWriteHandler);
        AtomicInteger authenticatedCount = new AtomicInteger();

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.AUTHENTICATED,
                event -> authenticatedCount.incrementAndGet());

        try {
            drainOutboundStrings(channel);
            channel.writeInbound(StreamFeatures.builder()
                    .bindAvailable(true)
                    .build());
            channel.writeInbound(new Iq.Builder(Iq.Type.RESULT)
                    .id("bind-1")
                    .childElement(Bind.builder().jid("user@example.com/resource").build())
                    .build());

            handler.invalidateStateContext();
            delayedPresenceWriteHandler.succeedPendingWrite();
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();

            assertNull(currentStateName(handler));
            assertFalse(connection.getConnectionReadyFuture().isDone());
            assertEquals(0, authenticatedCount.get());
            assertFalse(connection.isAuthenticated());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testExceptionCaughtPublishesErrorOnlyOnce() {
        XmppEventBus.getInstance().clear();
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);
        AtomicInteger errorCount = new AtomicInteger();
        AtomicInteger closedCount = new AtomicInteger();

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.ERROR, event -> errorCount.incrementAndGet());
        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CLOSED, event -> closedCount.incrementAndGet());

        channel.pipeline().fireExceptionCaught(new IllegalStateException("boom"));
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.getConnectionReadyFuture().join());
        assertInstanceOf(XmppException.class, exception.getCause());
        assertNull(exception.getCause().getCause());
        assertEquals(1, errorCount.get());
        assertEquals(1, closedCount.get());

        channel.finishAndReleaseAll();
    }

    @Test
    void testExceptionCaughtWithIoExceptionUsesNetworkException() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

        try {
            channel.pipeline().fireExceptionCaught(new IOException("broken pipe"));

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.getConnectionReadyFuture().join());
            XmppNetworkException cause = assertInstanceOf(XmppNetworkException.class, exception.getCause());
            assertEquals("I/O error", cause.getMessage());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testHandshakeFailureCompletesConnectionWithNetworkException() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .usingDirectTLS(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

        try {
            channel.pipeline().fireUserEventTriggered(
                    new SslHandshakeCompletionEvent(new IllegalStateException("tls boom")));

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.getConnectionReadyFuture().join());
            XmppNetworkException cause = assertInstanceOf(XmppNetworkException.class, exception.getCause());
            assertEquals("SSL handshake failed", cause.getMessage());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testHandshakeSuccessWithNullOpenStreamFutureFailsRecovery() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .usingDirectTLS(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

        try {
            setStateContext(handler, new NullOpenStreamStateContext(config, connection, channel.pipeline().lastContext()));
            channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.getConnectionReadyFuture().join());
            XmppException cause = assertInstanceOf(XmppException.class, exception.getCause());
            assertEquals("Failed to reopen stream after TLS handshake", cause.getMessage());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testSendStanzaReturnsNullForNullAndEmptyXml() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppNettyHandler handler = new XmppNettyHandler(config, new XmppTcpConnection(config));
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        try {
            ChannelHandlerContext context = channel.pipeline().lastContext();
            assertNull(handler.sendStanza(context, null));
            assertNull(handler.sendStanza(context, new EmptySerializable()));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testChannelReadAfterStateClearedDoesNotThrow() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

        handler.invalidateStateContext();

        assertDoesNotThrow(() -> channel.writeInbound(new Iq.Builder(Iq.Type.RESULT).id("iq-1").build()));
        assertNull(channel.readInbound());

        channel.finishAndReleaseAll();
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

    private String currentStateName(XmppNettyHandler handler) {
        StateContext stateContext = currentStateContext(handler);
        return stateContext != null ? stateContext.getCurrentStateName() : null;
    }

    private StateContext currentStateContext(XmppNettyHandler handler) {
        try {
            Field field = XmppNettyHandler.class.getDeclaredField("stateContext");
            field.setAccessible(true);
            return (StateContext) field.get(handler);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect handler state", e);
        }
    }

    private void setStateContext(XmppNettyHandler handler, StateContext stateContext) {
        try {
            Field field = XmppNettyHandler.class.getDeclaredField("stateContext");
            field.setAccessible(true);
            field.set(handler, stateContext);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to replace handler state", e);
        }
    }

    private EmbeddedChannel newBoundChannel(XmppTcpConnection connection,
                                            XmppNettyHandler handler,
                                            ChannelHandler... leadingHandlers) {
        ChannelHandler[] handlers = new ChannelHandler[leadingHandlers.length + 1];
        System.arraycopy(leadingHandlers, 0, handlers, 0, leadingHandlers.length);
        handlers[leadingHandlers.length] = handler;
        EmbeddedChannel channel = new EmbeddedChannel(handlers);
        bindNettyHandler(connection, handler);
        bindChannel(connection, channel);
        return channel;
    }

    private void bindNettyHandler(XmppTcpConnection connection, XmppNettyHandler handler) {
        try {
            Field field = XmppTcpConnection.class.getDeclaredField("nettyHandler");
            field.setAccessible(true);
            field.set(connection, handler);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to bind handler", e);
        }
    }

    private void bindChannel(XmppTcpConnection connection, io.netty.channel.Channel channel) {
        try {
            Field field = XmppTcpConnection.class.getDeclaredField("channel");
            field.setAccessible(true);
            field.set(connection, channel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to bind channel", e);
        }
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

    private static final class DelayedAuthWriteHandler extends ChannelDuplexHandler {

        private ChannelHandlerContext context;

        private ChannelPromise pendingPromise;

        private ByteBuf pendingBuffer;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.context = ctx;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf byteBuf && byteBuf.toString(StandardCharsets.UTF_8).contains("<auth ")) {
                pendingPromise = promise;
                pendingBuffer = byteBuf;
                return;
            }
            ctx.write(msg, promise);
        }

        private void succeedPendingWrite() {
            if (pendingPromise == null || pendingBuffer == null) {
                throw new AssertionError("No pending SASL auth write to complete");
            }
            ChannelPromise promise = pendingPromise;
            ByteBuf buffer = pendingBuffer;
            pendingPromise = null;
            pendingBuffer = null;
            context.writeAndFlush(buffer, promise);
        }
    }

    private static final class DelayedStreamWriteHandler extends ChannelDuplexHandler {

        private ChannelHandlerContext context;

        private ChannelPromise pendingPromise;

        private ByteBuf pendingBuffer;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.context = ctx;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf byteBuf
                    && byteBuf.toString(StandardCharsets.UTF_8).contains("<stream:stream")) {
                pendingPromise = promise;
                pendingBuffer = byteBuf;
                return;
            }
            ctx.write(msg, promise);
        }

        private void succeedPendingWrite() {
            if (pendingPromise == null || pendingBuffer == null) {
                throw new AssertionError("No pending stream write to complete");
            }
            ChannelPromise promise = pendingPromise;
            ByteBuf buffer = pendingBuffer;
            pendingPromise = null;
            pendingBuffer = null;
            context.writeAndFlush(buffer, promise);
        }
    }

    private static final class FailingStreamOpenHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf byteBuf
                    && byteBuf.toString(StandardCharsets.UTF_8).contains("<stream:stream")) {
                byteBuf.release();
                promise.setFailure(new IllegalStateException("simulated stream open write failure"));
                return;
            }
            ctx.write(msg, promise);
        }
    }

    private static final class FailingBindWriteHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf byteBuf
                    && byteBuf.toString(StandardCharsets.UTF_8).contains("urn:ietf:params:xml:ns:xmpp-bind")) {
                byteBuf.release();
                promise.setFailure(new IllegalStateException("simulated bind write failure"));
                return;
            }
            ctx.write(msg, promise);
        }
    }

    private static final class FailingPresenceWriteHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf byteBuf
                    && byteBuf.toString(StandardCharsets.UTF_8).contains("<presence")) {
                byteBuf.release();
                promise.setFailure(new IllegalStateException("simulated presence write failure"));
                return;
            }
            ctx.write(msg, promise);
        }
    }

    private static final class DelayedPresenceWriteHandler extends ChannelDuplexHandler {

        private ChannelHandlerContext context;

        private ChannelPromise pendingPromise;

        private ByteBuf pendingBuffer;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.context = ctx;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf byteBuf
                    && byteBuf.toString(StandardCharsets.UTF_8).contains("<presence")) {
                pendingPromise = promise;
                pendingBuffer = byteBuf;
                return;
            }
            ctx.write(msg, promise);
        }

        private void succeedPendingWrite() {
            if (pendingPromise == null || pendingBuffer == null) {
                throw new AssertionError("No pending presence write to complete");
            }
            ChannelPromise promise = pendingPromise;
            ByteBuf buffer = pendingBuffer;
            pendingPromise = null;
            pendingBuffer = null;
            context.writeAndFlush(buffer, promise);
        }
    }

    private static final class NullOpenStreamStateContext extends StateContext {

        private NullOpenStreamStateContext(XmppClientConfig config,
                                           XmppTcpConnection connection,
                                           ChannelHandlerContext ctx) {
            super(config, connection, ctx);
        }

        @Override
        public ChannelFuture openStream(ChannelHandlerContext ctx) {
            return null;
        }
    }

    private static final class EmptySerializable implements XmlSerializable {

        @Override
        public String toXml() {
            return "";
        }
    }
}
