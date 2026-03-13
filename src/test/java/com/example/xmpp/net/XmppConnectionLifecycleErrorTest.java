package com.example.xmpp.net;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppSaslFailureException;
import com.example.xmpp.exception.XmppStreamErrorException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.net.state.StateContext;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.stream.StreamError;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连接生命周期错误传播测试。
 */
class XmppConnectionLifecycleErrorTest {

    @BeforeEach
    void setUp() {
        XmppEventBus.getInstance().clear();
    }

    @Test
    void testSaslFailureCompletesConnectionFutureExceptionally() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .enabledSaslMechanisms(Set.of("SCRAM-SHA-1"))
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new XmppNettyHandler(config, connection));

        channel.writeInbound(StreamFeatures.builder()
                .mechanisms(List.of("SCRAM-SHA-1"))
                .build());
        channel.writeInbound(SaslFailure.builder()
                .condition("not-authorized")
                .text("Invalid credentials")
                .build());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.getConnectionReadyFuture().join());
        XmppSaslFailureException cause = assertInstanceOf(XmppSaslFailureException.class, exception.getCause());
        assertEquals("not-authorized", cause.getSaslFailure().getCondition());
        assertEquals("Invalid credentials", cause.getSaslFailure().getText());
    }

    @Test
    void testStreamErrorCompletesConnectionFutureExceptionally() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new XmppNettyHandler(config, connection));

        channel.writeInbound(StreamError.builder()
                .condition(StreamError.Condition.NOT_AUTHORIZED)
                .text("Authentication required")
                .by("example.com")
                .build());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.getConnectionReadyFuture().join());
        XmppStreamErrorException cause = assertInstanceOf(XmppStreamErrorException.class, exception.getCause());
        assertEquals(StreamError.Condition.NOT_AUTHORIZED, cause.getStreamError().getCondition());
        assertEquals("Authentication required", cause.getStreamError().getText());
    }

    @Test
    void testStreamErrorPublishesOnlyErrorTerminalEvent() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new XmppNettyHandler(config, connection));
        AtomicInteger errorCount = new AtomicInteger();
        AtomicInteger closedCount = new AtomicInteger();

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.ERROR, event -> errorCount.incrementAndGet());
        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CLOSED, event -> closedCount.incrementAndGet());

        channel.writeInbound(StreamError.builder()
                .condition(StreamError.Condition.NOT_AUTHORIZED)
                .text("Authentication required")
                .by("example.com")
                .build());

        assertEquals(1, errorCount.get());
        assertEquals(0, closedCount.get());
    }

    @Test
    void testNormalClosePublishesClosedOnlyOnce() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new XmppNettyHandler(config, connection));
        AtomicInteger errorCount = new AtomicInteger();
        AtomicInteger closedCount = new AtomicInteger();

        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.ERROR, event -> errorCount.incrementAndGet());
        XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CLOSED, event -> closedCount.incrementAndGet());

        channel.close();
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();

        assertEquals(0, errorCount.get());
        assertEquals(1, closedCount.get());
    }

    @Test
    void testCloseConnectionOnErrorSanitizesSensitiveMessage() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new XmppNettyHandler(config, connection));
        StateContext stateContext = new StateContext(config, connection, channel.pipeline().lastContext());

        stateContext.closeConnectionOnError(channel.pipeline().lastContext(),
                new IllegalArgumentException("password=secret"));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.getConnectionReadyFuture().join());
        assertFalse(String.valueOf(exception.getCause().getMessage()).contains("secret"));
    }

    @Test
    void testSendIqPacketAsyncFailsImmediatelyWhenDisconnected() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("iq-1")
                .to("example.com")
                .build();

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.sendIqPacketAsync(iq).join());

        assertInstanceOf(XmppNetworkException.class, exception.getCause());
    }

    @Test
    void testDisconnectClearsEventBusSubscribers() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .pingEnabled(true)
                .reconnectionEnabled(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);

        assertEquals(0, XmppEventBus.getInstance().getTotalSubscriberCount(connection));

        connection.disconnect();

        assertEquals(0, XmppEventBus.getInstance().getTotalSubscriberCount(connection));
    }

    @Test
    void testConnectAsyncReusesExistingFutureWhileConnectionIsInProgress() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndHoldSocket(serverSocket, acceptedLatch));

            XmppClientConfig config = XmppClientConfig.builder()
                    .xmppServiceDomain("example.com")
                    .host("127.0.0.1")
                    .port(serverSocket.getLocalPort())
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .readTimeout(1000)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> firstFuture = connection.connectAsync();
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            CompletableFuture<Void> secondFuture = connection.connectAsync();

            assertSame(firstFuture, secondFuture);
            connection.disconnect();
        }
    }

    private void acceptAndHoldSocket(ServerSocket serverSocket, CountDownLatch acceptedLatch) {
        try (Socket socket = serverSocket.accept()) {
            acceptedLatch.countDown();
            socket.getInputStream().read();
        } catch (IOException ignored) {
        }
    }
}
