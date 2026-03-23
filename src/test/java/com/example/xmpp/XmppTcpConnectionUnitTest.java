package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.exception.XmppProtocolException;
import com.example.xmpp.net.XmppNettyHandler;
import com.example.xmpp.protocol.model.XmlSerializable;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.util.ConnectionUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmppTcpConnectionUnitTest {

    @Test
    void testResolveConnectionTargetsPrefersHostAddress() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("invalid-host")
                .hostAddress(InetAddress.getByName("127.0.0.1"))
                .port(5223)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build());

        List<?> targets = invoke(connection, "resolveConnectionTargets");

        assertEquals(1, targets.size());
        assertEquals("127.0.0.1:5223", targets.get(0).toString());
    }

    @Test
    void testResolveConnectionTargetsFallsBackToServiceDomain() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("")
                .port(5222)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build());

        List<?> targets = invoke(connection, "resolveConnectionTargets");
        Object target = targets.get(0);
        InetSocketAddress socketAddress = invoke(target, "toSocketAddress");

        assertEquals(1, targets.size());
        assertEquals("example.com:5222", target.toString());
        assertTrue(socketAddress.isUnresolved());
        assertEquals("example.com", socketAddress.getHostString());
    }

    @Test
    void testFindReusableReadyFutureReturnsFutureForActiveChannel() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel();
        CompletableFuture<Void> future = new CompletableFuture<>();

        setField(connection, "channel", channel);
        setField(connection, "connectionReadyFuture", future);

        assertSame(future, invoke(connection, "findReusableReadyFuture"));

        channel.finishAndReleaseAll();
    }

    @Test
    void testFindReusableReadyFutureReturnsFutureForInProgressWorkerGroup() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        CompletableFuture<Void> future = new CompletableFuture<>();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);

        try {
            setField(connection, "connectionReadyFuture", future);
            setField(connection, "workerGroup", workerGroup);

            assertSame(future, invoke(connection, "findReusableReadyFuture"));
        } finally {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void testFindReusableReadyFutureReturnsNullForFailedFuture() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new XmppNetworkException("boom"));
        setField(connection, "connectionReadyFuture", future);

        assertNull(invoke(connection, "findReusableReadyFuture"));
    }

    @Test
    void testResetForNewConnectionAttemptClosesPreviousResources() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            setField(connection, "channel", channel);
            setField(connection, "workerGroup", workerGroup);
            setField(connection, "connectionReadyFuture", future);

            invoke(connection, "resetForNewConnectionAttempt");

            assertNull(getField(connection, "channel"));
            assertNull(getField(connection, "workerGroup"));
            assertFalse(channel.isOpen());
            assertTrue(workerGroup.isShuttingDown() || workerGroup.isShutdown());
            assertNotSame(future, getField(connection, "connectionReadyFuture"));
        } finally {
            channel.finishAndReleaseAll();
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void testBindActiveChannelRejectsNullAndClosedConnectionState() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel();

        try {
            assertFalse(connection.bindActiveChannel(null));
            Class<?> stateClass = Class.forName("com.example.xmpp.XmppTcpConnection$TerminalEventState");
            Object closedOnly = Enum.valueOf((Class<Enum>) stateClass.asSubclass(Enum.class), "CLOSED_ONLY");
            setField(connection, "terminalEventState", closedOnly);
            assertFalse(connection.bindActiveChannel(channel));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testBindActiveChannelAcceptsFirstAndSameChannel() {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel();

        try {
            assertTrue(connection.bindActiveChannel(channel));
            assertTrue(connection.bindActiveChannel(channel));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testFailConnectionIgnoresStaleChannel() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel currentChannel = new EmbeddedChannel();
        EmbeddedChannel staleChannel = new EmbeddedChannel();
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            setField(connection, "channel", currentChannel);
            setField(connection, "connectionReadyFuture", future);

            connection.failConnection(staleChannel, new XmppException("stale"));

            assertFalse(future.isDone());
            assertSame(currentChannel, getField(connection, "channel"));
        } finally {
            currentChannel.finishAndReleaseAll();
            staleChannel.finishAndReleaseAll();
        }
    }

    @Test
    void testUnwrapXmppExceptionReturnsWrappedXmppException() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        XmppNetworkException expected = new XmppNetworkException("boom");

        XmppException direct = invoke(connection, "unwrapXmppException", expected);
        XmppException wrapped = invoke(connection, "unwrapXmppException", new RuntimeException(expected));
        XmppException fallback = invoke(connection, "unwrapXmppException", new IllegalStateException("boom"));

        assertSame(expected, direct);
        assertSame(expected, wrapped);
        assertInstanceOf(XmppProtocolException.class, fallback);
    }

    @Test
    void testConnectionTargetFactorySkipsBlankHostWithoutAddress() throws Exception {
        Class<?> targetClass = Class.forName("com.example.xmpp.XmppTcpConnection$ConnectionTarget");
        Method ofMethod = targetClass.getDeclaredMethod("of", InetAddress.class, String.class, int.class);
        ofMethod.setAccessible(true);

        Optional<?> empty = (Optional<?>) ofMethod.invoke(null, null, " ", 5222);
        Optional<?> present = (Optional<?>) ofMethod.invoke(null, InetAddress.getByName("127.0.0.1"), "", 5222);

        assertTrue(empty.isEmpty());
        assertTrue(present.isPresent());
        assertEquals("127.0.0.1:5222", present.orElseThrow().toString());
    }

    @Test
    void testDispatchStanzaFailsWhenChannelInactive() {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.dispatchStanza(new TestStanza("<message id='m1'/>")).join());

        XmppNetworkException cause = assertInstanceOf(XmppNetworkException.class, exception.getCause());
        assertTrue(cause.getMessage().contains("Channel is not active"));
    }

    @Test
    void testSendStanzaLogsFailureAtErrorLevelWhenChannelInactive() {
        Logger logger = (Logger) LogManager.getLogger(XmppTcpConnection.class);
        TestLogAppender appender = attachAppender("sendStanzaInactive", logger);
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());

        try {
            connection.sendStanza(new TestStanza("<message id='m-log'/>"));
            assertTrue(appender.containsAtLevel("Failed to send stanza - ErrorType: XmppNetworkException", Level.ERROR));
        } finally {
            detachAppender(appender, logger);
        }
    }

    @Test
    void testDispatchStanzaFailsWhenSerializationReturnsBlankXml() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        XmppNettyHandler nettyHandler = new XmppNettyHandler(newConfig(), connection);
        EmbeddedChannel channel = new EmbeddedChannel(nettyHandler);

        try {
            setField(connection, "channel", channel);
            setField(connection, "nettyHandler", nettyHandler);

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.dispatchStanza(new TestStanza("   ")).join());

            XmppNetworkException cause = assertInstanceOf(XmppNetworkException.class, exception.getCause());
            assertTrue(cause.getMessage().contains("Failed to send stanza"));
            XmppNetworkException rootCause = assertInstanceOf(XmppNetworkException.class, cause.getCause());
            assertTrue(rootCause.getMessage().contains("Failed to serialize stanza for sending"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testDispatchStanzaCompletesWhenWriteSucceeds() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        RecordingNettyHandler nettyHandler = new RecordingNettyHandler(newConfig(), connection, channel.newSucceededFuture());

        try {
            setField(connection, "channel", channel);
            setField(connection, "nettyHandler", nettyHandler);

            assertDoesNotThrow(() -> connection.dispatchStanza(new TestStanza("<message id='m3'/>")).join());
            assertEquals("<message id='m3'/>", nettyHandler.lastStanza.toXml());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testDispatchStanzaFailsWhenWriteFails() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelFuture failedFuture = channel.newFailedFuture(new IllegalStateException("boom"));
        RecordingNettyHandler nettyHandler = new RecordingNettyHandler(newConfig(), connection, failedFuture);

        try {
            setField(connection, "channel", channel);
            setField(connection, "nettyHandler", nettyHandler);

            CompletionException exception = assertThrows(CompletionException.class,
                    () -> connection.dispatchStanza(new TestStanza("<message id='m4'/>")).join());

            XmppNetworkException cause = assertInstanceOf(XmppNetworkException.class, exception.getCause());
            assertTrue(cause.getMessage().contains("Failed to send stanza"));
            assertInstanceOf(IllegalStateException.class, cause.getCause());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testConnectAsyncPreservesDirectTlsInitializationFailureCauseWithoutLoggingSensitiveDetails() {
        Logger[] loggers = {
                (Logger) LogManager.getLogger(XmppTcpConnection.class),
                (Logger) LogManager.getLogger(ConnectionUtils.class),
                (Logger) LogManager.getLogger(io.netty.channel.ChannelInitializer.class)
        };
        TestLogAppender appender = attachAppender("connectAsyncDirectTls", loggers);
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("127.0.0.1")
                .port(5223)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .usingDirectTLS(true)
                .build());

        try {
            XmppNetworkException exception = assertThrows(XmppNetworkException.class, connection::connectAsync);

            assertTrue(containsMessage(exception, "TrustManager"));
            assertFalse(appender.contains("TrustManager"));
        } finally {
            detachAppender(appender, loggers);
        }
    }

    @Test
    void testFailConnectionPublishesTerminalEventsOnlyOnce() throws Exception {
        clearEventBusListeners();
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        AtomicInteger errorCount = new AtomicInteger();
        AtomicInteger closedCount = new AtomicInteger();

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.ERROR, event -> errorCount.incrementAndGet());
        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CLOSED, event -> closedCount.incrementAndGet());

        connection.failConnection(new XmppNetworkException("boom"));
        connection.failConnection(new XmppNetworkException("boom-again"));

        assertEquals(1, errorCount.get());
        assertEquals(1, closedCount.get());
    }

    @Test
    void testHandleChannelInactiveAfterDisconnectPublishesClosedOnlyOnce() throws Exception {
        clearEventBusListeners();
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel();
        AtomicInteger errorCount = new AtomicInteger();
        AtomicInteger closedCount = new AtomicInteger();

        try {
            setField(connection, "channel", channel);
            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.ERROR, event -> errorCount.incrementAndGet());
            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CLOSED, event -> closedCount.incrementAndGet());

            connection.disconnect();
            connection.handleChannelInactive(channel);

            assertEquals(0, errorCount.get());
            assertEquals(1, closedCount.get());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private XmppClientConfig newConfig() {
        return XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
    }

    private <T> T invoke(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }

    private Method findMethod(Class<?> type, String methodName, Class<?>[] parameterTypes) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != parameterTypes.length) {
                continue;
            }
            return method;
        }
        throw new NoSuchMethodException(methodName);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = XmppTcpConnection.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object getField(Object target, String fieldName) throws Exception {
        var field = XmppTcpConnection.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void clearEventBusListeners() {
        try {
            var field = XmppEventBus.class.getDeclaredField("listeners");
            field.setAccessible(true);
            Object listenersObject = field.get(XmppEventBus.getInstance());
            ((Map<?, ?>) listenersObject).clear();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to clear XmppEventBus listeners", e);
        }
    }

    private boolean containsMessage(Throwable throwable, String messagePart) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(messagePart)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private TestLogAppender attachAppender(String name, Logger... loggers) {
        TestLogAppender appender = new TestLogAppender(name);
        appender.start();
        for (Logger logger : loggers) {
            logger.addAppender(appender);
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

    private static final class TestStanza implements XmppStanza, XmlSerializable {

        private final String xml;

        private TestStanza(String xml) {
            this.xml = xml;
        }

        @Override
        public String getId() {
            return "test-id";
        }

        @Override
        public String getFrom() {
            return null;
        }

        @Override
        public String getTo() {
            return null;
        }

        @Override
        public String toXml() {
            return xml;
        }
    }

    private static final class RecordingNettyHandler extends XmppNettyHandler {

        private final ChannelFuture future;

        private ChannelHandlerContext lastContext;

        private XmlSerializable lastStanza;

        private RecordingNettyHandler(XmppClientConfig config, XmppTcpConnection connection, ChannelFuture future) {
            super(config, connection);
            this.future = future;
        }

        @Override
        public ChannelFuture sendStanza(ChannelHandlerContext ctx, Object packet) {
            lastContext = ctx;
            lastStanza = (XmlSerializable) packet;
            return future;
        }
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

        private boolean contains(String text) {
            for (LogEvent event : events) {
                if (event.getMessage() != null && event.getMessage().getFormattedMessage().contains(text)) {
                    return true;
                }
                if (containsMessage(event.getThrown(), text)) {
                    return true;
                }
            }
            return false;
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

        private boolean containsMessage(Throwable throwable, String text) {
            Throwable current = throwable;
            while (current != null) {
                if (current.getMessage() != null && current.getMessage().contains(text)) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }
    }
}
