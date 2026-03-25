package com.example.xmpp.net.state;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.protocol.model.XmlSerializable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StateContext 行为测试。
 */
class StateContextTest {

    @Test
    void testOpenStreamWritesXmlDeclarationAndStreamHeader() {
        TestFixture fixture = new TestFixture();

        ChannelFuture future = fixture.context.openStream(fixture.channel.pipeline().lastContext());

        assertTrue(future.isSuccess() || !future.isDone());
        String outbound = fixture.readOutboundAsString();
        assertTrue(outbound.startsWith("<?xml version='1.0' encoding='UTF-8'?>"));
        assertTrue(outbound.contains("<stream:stream"));
        assertTrue(outbound.contains("to=\"example.com\""));

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testSendStanzaReturnsFailedFutureForEmptyAndBlankXml() {
        TestFixture fixture = new TestFixture();
        fixture.readOutboundAsString();

        ChannelFuture emptyFuture = fixture.context.sendStanza(fixture.channel.pipeline().lastContext(),
                (XmlSerializable) () -> "");
        ChannelFuture blankFuture = fixture.context.sendStanza(fixture.channel.pipeline().lastContext(),
                (XmlSerializable) () -> "   ");

        assertNotNull(emptyFuture);
        assertTrue(emptyFuture.isDone());
        assertFalse(emptyFuture.isSuccess());
        assertInstanceOf(XmppNetworkException.class, emptyFuture.cause());

        assertNotNull(blankFuture);
        assertTrue(blankFuture.isDone());
        assertFalse(blankFuture.isSuccess());
        assertInstanceOf(XmppNetworkException.class, blankFuture.cause());
        assertNull(fixture.channel.readOutbound());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testSendStanzaWritesSerializablePayload() {
        TestFixture fixture = new TestFixture();
        fixture.readOutboundAsString();

        ChannelFuture future = fixture.context.sendStanza(fixture.channel.pipeline().lastContext(),
                (XmlSerializable) () -> "<presence id='p1'/>");

        assertTrue(future.isSuccess() || !future.isDone());
        assertEquals("<presence id='p1'/>", fixture.readOutboundAsString());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testSendStanzaReturnsFailedFutureForUnknownPacketType() {
        TestFixture fixture = new TestFixture();
        fixture.readOutboundAsString();

        ChannelFuture future = fixture.context.sendStanza(fixture.channel.pipeline().lastContext(), new Object());

        assertNotNull(future);
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());
        assertInstanceOf(XmppNetworkException.class, future.cause());
        assertNull(fixture.channel.readOutbound());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testInvalidatePreventsFurtherTransitions() {
        TestFixture fixture = new TestFixture();

        fixture.context.invalidate();
        fixture.context.transitionTo(XmppHandlerState.AWAITING_FEATURES, fixture.channel.pipeline().lastContext());

        assertEquals("AWAITING_FEATURES", fixture.context.getCurrentStateName());
        assertFalse(fixture.context.isAuthenticated());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testCloseConnectionOnErrorFailsCurrentConnection() {
        TestFixture fixture = new TestFixture();
        fixture.connection.bindActiveChannel(fixture.channel);

        fixture.context.closeConnectionOnError(fixture.channel.pipeline().lastContext(), new XmppException("boom"));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> fixture.connection.getConnectionReadyFuture().join());
        assertTrue(exception.getCause().getMessage().contains("boom"));
        assertFalse(fixture.channel.isOpen());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testHandleMessageIgnoredAfterInvalidate() {
        TestFixture fixture = new TestFixture();

        fixture.context.invalidate();
        fixture.context.handleMessage(fixture.channel.pipeline().lastContext(), "ignored");

        assertEquals("AWAITING_FEATURES", fixture.context.getCurrentStateName());
        assertFalse(fixture.context.isAuthenticated());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testHandleUserEventIgnoredAfterInvalidate() {
        TestFixture fixture = new TestFixture(true);

        fixture.context.invalidate();
        fixture.context.handleUserEvent(fixture.channel.pipeline().lastContext(), SslHandshakeCompletionEvent.SUCCESS);

        assertEquals("CONNECTING", fixture.context.getCurrentStateName());
        assertFalse(fixture.context.isAuthenticated());
        assertNull(fixture.channel.readOutbound());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testTransitionToSameStateIsNoOp() {
        TestFixture fixture = new TestFixture(true);

        fixture.context.transitionTo(XmppHandlerState.CONNECTING, fixture.channel.pipeline().lastContext());

        assertEquals("CONNECTING", fixture.context.getCurrentStateName());
        assertFalse(fixture.context.isAuthenticated());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testHandleUserEventReopensStreamAndTransitionsToAwaitingFeaturesAfterTlsHandshake() {
        TestFixture fixture = new TestFixture(true);
        fixture.readOutboundAsString();

        fixture.context.handleUserEvent(fixture.channel.pipeline().lastContext(), SslHandshakeCompletionEvent.SUCCESS);

        String outbound = fixture.readOutboundAsString();
        assertTrue(outbound.startsWith("<?xml version='1.0' encoding='UTF-8'?>"));
        assertTrue(outbound.contains("<stream:stream"));
        assertEquals("AWAITING_FEATURES", fixture.context.getCurrentStateName());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testHandleUserEventDoesNotAdvanceAgainWhenStateChangesBeforeOpenStreamWriteCompletes() throws Exception {
        ControlledTlsFixture fixture = new ControlledTlsFixture();

        fixture.context.handleUserEvent(fixture.channel.pipeline().lastContext(), SslHandshakeCompletionEvent.SUCCESS);
        assertEquals("AWAITING_FEATURES", fixture.context.getCurrentStateName());
        setCurrentState(fixture.context, XmppHandlerState.SESSION_ACTIVE);

        fixture.openStreamPromise.setSuccess();

        assertEquals("SESSION_ACTIVE", fixture.context.getCurrentStateName());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testHandleUserEventTransitionsToAwaitingFeaturesBeforeOpenStreamWriteCompletesForStartTls()
            throws Exception {
        ControlledTlsFixture fixture = new ControlledTlsFixture();
        setCurrentState(fixture.context, XmppHandlerState.TLS_NEGOTIATING);

        fixture.context.handleUserEvent(fixture.channel.pipeline().lastContext(), SslHandshakeCompletionEvent.SUCCESS);

        assertEquals("AWAITING_FEATURES", fixture.context.getCurrentStateName());
        assertFalse(fixture.openStreamPromise.isDone());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testTransitionBeforeWriteTransitionsImmediatelyAndDoesNotAdvanceAgainAfterWriteSucceeds() throws Exception {
        ControlledTlsFixture fixture = new ControlledTlsFixture();
        setCurrentState(fixture.context, XmppHandlerState.AWAITING_FEATURES);
        fixture.context.clearTransitionRequests();

        fixture.context.transitionBeforeWrite(XmppHandlerState.SASL_AUTH,
                fixture.channel.pipeline().lastContext(),
                "send SASL auth stanza",
                () -> fixture.openStreamPromise);

        assertEquals("SASL_AUTH", fixture.context.getCurrentStateName());
        assertEquals(List.of(XmppHandlerState.SASL_AUTH), fixture.context.getTransitionRequests());

        fixture.openStreamPromise.setSuccess();

        assertEquals("SASL_AUTH", fixture.context.getCurrentStateName());
        assertEquals(List.of(XmppHandlerState.SASL_AUTH), fixture.context.getTransitionRequests());

        fixture.channel.finishAndReleaseAll();
    }

    @Test
    void testTransitionAfterSuccessfulWriteSkipsFollowUpWhenChannelInactive() {
        Logger logger = (Logger) LogManager.getLogger(StateContext.class);
        TestLogAppender appender = attachAppender("inactiveFollowUp", logger);
        TestFixture fixture = new TestFixture();
        ChannelPromise promise = fixture.captureHandler.context.newPromise();
        AtomicInteger onSuccessCount = new AtomicInteger();

        try {
            fixture.context.transitionAfterSuccessfulWrite(fixture.channel.pipeline().lastContext(),
                    promise,
                    "send initial presence",
                    onSuccessCount::incrementAndGet);

            fixture.channel.close().syncUninterruptibly();
            promise.setSuccess();

            assertEquals(0, onSuccessCount.get());
            assertTrue(appender.containsAtLevel(
                    "Skipping state follow-up for send initial presence because channel is inactive",
                    Level.DEBUG));
        } finally {
            detachAppender(appender, logger);
            fixture.channel.finishAndReleaseAll();
        }
    }

    @Test
    void testCloseConnectionOnErrorLogsWhenCloseFutureFails() {
        Logger logger = (Logger) LogManager.getLogger(StateContext.class);
        TestLogAppender appender = attachAppender("closeFailure", logger);
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        ContextCaptureHandler captureHandler = new ContextCaptureHandler();
        EmbeddedChannel channel = new EmbeddedChannel(new CloseFailingHandler(), captureHandler);
        StateContext context = new StateContext(config, connection, captureHandler.context);

        try {
            context.closeConnectionOnError(channel.pipeline().lastContext(), new XmppException("boom"));

            assertTrue(appender.containsAtLevel("Failed to close channel after error - ErrorType: IllegalStateException",
                    Level.ERROR));
        } finally {
            detachAppender(appender, logger);
            channel.finishAndReleaseAll();
        }
    }

    private static final class TestFixture {
        private final XmppClientConfig config;
        private final XmppTcpConnection connection;
        private final ContextCaptureHandler captureHandler;
        private final EmbeddedChannel channel;
        private final StateContext context;

        private TestFixture() {
            this(false);
        }

        private TestFixture(boolean directTls) {
            this.config = XmppClientConfig.builder()
                    .xmppServiceDomain("example.com")
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .usingDirectTLS(directTls)
                    .build();
            this.connection = new XmppTcpConnection(config);
            this.captureHandler = new ContextCaptureHandler();
            this.channel = new EmbeddedChannel(captureHandler);
            this.context = new StateContext(config, connection, captureHandler.context);
        }

        private String readOutboundAsString() {
            Object outbound = channel.readOutbound();
            if (outbound instanceof ByteBuf byteBuf) {
                return byteBuf.toString(StandardCharsets.UTF_8);
            }
            return outbound == null ? "" : outbound.toString();
        }
    }

    private static final class ControlledTlsFixture {
        private final XmppTcpConnection connection;
        private final ContextCaptureHandler captureHandler;
        private final EmbeddedChannel channel;
        private final io.netty.channel.ChannelPromise openStreamPromise;
        private final ControlledStateContext context;

        private ControlledTlsFixture() {
            XmppClientConfig config = XmppClientConfig.builder()
                    .xmppServiceDomain("example.com")
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .usingDirectTLS(true)
                    .build();
            this.connection = new XmppTcpConnection(config);
            this.captureHandler = new ContextCaptureHandler();
            this.channel = new EmbeddedChannel(captureHandler);
            this.openStreamPromise = captureHandler.context.newPromise();
            this.context = new ControlledStateContext(config, connection, captureHandler.context, openStreamPromise);
        }
    }

    private static final class ControlledStateContext extends StateContext {
        private final ChannelFuture openStreamFuture;
        private final List<XmppHandlerState> transitionRequests = new ArrayList<>();

        private ControlledStateContext(XmppClientConfig config, XmppTcpConnection connection, ChannelHandlerContext ctx,
                                       ChannelFuture openStreamFuture) {
            super(config, connection, ctx);
            this.openStreamFuture = openStreamFuture;
        }

        @Override
        public ChannelFuture openStream(ChannelHandlerContext ctx) {
            return openStreamFuture;
        }

        @Override
        public void transitionTo(XmppHandlerState newState, ChannelHandlerContext ctx) {
            if (transitionRequests != null) {
                transitionRequests.add(newState);
            }
            super.transitionTo(newState, ctx);
        }

        private void clearTransitionRequests() {
            transitionRequests.clear();
        }

        private List<XmppHandlerState> getTransitionRequests() {
            return List.copyOf(transitionRequests);
        }
    }

    private static final class ContextCaptureHandler extends ChannelInboundHandlerAdapter {
        private ChannelHandlerContext context;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.context = ctx;
        }
    }

    private static final class CloseFailingHandler extends ChannelDuplexHandler {
        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            promise.setFailure(new IllegalStateException("close failed"));
        }
    }

    private static void setCurrentState(StateContext context, XmppHandlerState state) throws Exception {
        Field field = StateContext.class.getDeclaredField("currentState");
        field.setAccessible(true);
        field.set(context, state);
    }

    private TestLogAppender attachAppender(String name, Logger... loggers) {
        TestLogAppender appender = new TestLogAppender(name);
        appender.start();
        for (Logger logger : loggers) {
            logger.addAppender(appender);
            logger.setAdditive(false);
            logger.setLevel(Level.ALL);
        }
        return appender;
    }

    private void detachAppender(Appender appender, Logger... loggers) {
        for (Logger logger : loggers) {
            logger.removeAppender(appender);
        }
        appender.stop();
    }

    private static final class TestLogAppender extends AbstractAppender {
        private final List<LogEvent> events = new ArrayList<>();

        private TestLogAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        private boolean containsAtLevel(String text, Level level) {
            for (LogEvent event : events) {
                if (event.getLevel() == level
                        && event.getMessage() != null
                        && event.getMessage().getFormattedMessage().contains(text)) {
                    return true;
                }
            }
            return false;
        }
    }
}
