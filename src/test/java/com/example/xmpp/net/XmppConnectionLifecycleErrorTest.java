package com.example.xmpp.net;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppSaslFailureException;
import com.example.xmpp.exception.XmppStreamErrorException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.protocol.AsyncStanzaCollector;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.net.state.StateContext;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.stream.StreamError;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

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
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

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
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);
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
        assertEquals(1, closedCount.get());
    }

    @Test
    void testUnexpectedChannelClosePublishesErrorOnlyOnce() {
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

        channel.close();
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();

        assertEquals(1, errorCount.get());
        assertEquals(1, closedCount.get());
    }

    @Test
    void testManualDisconnectPublishesClosedOnlyOnce() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            CountDownLatch releaseLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndHoldSocketUntilRelease(serverSocket, acceptedLatch, releaseLatch));

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
            AtomicInteger errorCount = new AtomicInteger();
            AtomicInteger closedCount = new AtomicInteger();

            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.ERROR, event -> errorCount.incrementAndGet());
            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CLOSED, event -> closedCount.incrementAndGet());

            startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            connection.disconnect();
            Thread.sleep(200);
            releaseLatch.countDown();

            assertEquals(0, errorCount.get());
            assertEquals(1, closedCount.get());
        }
    }

    @Test
    void testFailConnectionClosesActiveChannelAndReleasesWorkerGroup() throws Exception {
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

            startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(connection.getChannel().isActive());
            assertTrue(getWorkerGroup(connection) != null);

            connection.failConnection(new XmppNetworkException("boom"));
            waitFor(() -> connection.getChannel() == null, 2000, "failConnection 应关闭当前通道");
            waitFor(() -> getWorkerGroup(connection) == null, 2000, "failConnection 应释放 workerGroup");
            assertFalse(connection.isConnected());
        }
    }

    @Test
    void testConnectPublishesConnectedEventOnce() throws Exception {
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
            AtomicInteger connectedCount = new AtomicInteger();

            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CONNECTED,
                    event -> connectedCount.incrementAndGet());

            CompletableFuture<Void> future = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            Thread.sleep(200);

            assertEquals(1, connectedCount.get());

            connection.disconnect();
            assertThrows(CompletionException.class, future::join);
        }
    }

    @Test
    void testConnectAsyncReuseDoesNotRepublishConnectedEvent() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            CountDownLatch releaseLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndHoldSocketUntilRelease(serverSocket, acceptedLatch, releaseLatch));

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
            AtomicInteger connectedCount = new AtomicInteger();

            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CONNECTED,
                    event -> connectedCount.incrementAndGet());

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            connection.markConnectionReady();
            firstFuture.join();

            CompletableFuture<Void> secondFuture = startConnect(connection);
            assertSame(firstFuture, secondFuture);

            Thread.sleep(200);

            assertEquals(1, connectedCount.get());

            connection.disconnect();
            releaseLatch.countDown();
        }
    }

    @Test
    void testRemoteCloseFailsInProgressReadyFutureAndClearsChannel() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndCloseSocket(serverSocket, acceptedLatch));

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

            CompletableFuture<Void> readyFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            CompletionException exception = assertThrows(CompletionException.class, readyFuture::join);
            assertInstanceOf(XmppException.class, exception.getCause());
            waitUntilDisconnected(connection);
            assertNull(connection.getChannel());
            assertFalse(connection.isConnected());
        }
    }

    @Test
    void testUnexpectedRemoteClosePublishesOnlyErrorTerminalEvent() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndCloseSocket(serverSocket, acceptedLatch));

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
            AtomicInteger errorCount = new AtomicInteger();
            AtomicInteger closedCount = new AtomicInteger();

            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.ERROR,
                    event -> errorCount.incrementAndGet());
            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CLOSED,
                    event -> closedCount.incrementAndGet());

            CompletableFuture<Void> readyFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            assertThrows(CompletionException.class, readyFuture::join);
            waitUntilDisconnected(connection);

            assertEquals(1, errorCount.get());
            assertEquals(1, closedCount.get());
        }
    }

    @Test
    void testCloseConnectionOnErrorUsesExplicitSafeMessage() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);
        StateContext stateContext = new StateContext(config, connection, channel.pipeline().lastContext());

        stateContext.closeConnectionOnError(channel.pipeline().lastContext(),
                "Invalid SASL authentication data");

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.getConnectionReadyFuture().join());
        assertEquals("Invalid SASL authentication data", exception.getCause().getMessage());
        assertNull(exception.getCause().getCause());
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

        assertEquals(0, getTotalSubscriberCount(connection));

        connection.disconnect();

        assertEquals(0, getTotalSubscriberCount(connection));
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

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            CompletableFuture<Void> secondFuture = startConnect(connection);

            assertSame(firstFuture, secondFuture);
            connection.disconnect();
        }
    }

    @Test
    void testConnectAsyncFailureReleasesLifecycleManagerSubscriptions() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("127.0.0.1")
                .port(1)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .connectTimeout(200)
                .pingEnabled(true)
                .reconnectionEnabled(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);

        assertThrows(Exception.class, connection::connect);
        assertEquals(0, getTotalSubscriberCount(connection));
    }

    @Test
    void testDisconnectClearsAuthenticatedStateImmediately() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler handler = new XmppNettyHandler(config, connection);
        EmbeddedChannel channel = newBoundChannel(connection, handler);

        channel.writeInbound(StreamFeatures.builder()
                .bindAvailable(true)
                .build());
        channel.writeInbound(new Iq.Builder(Iq.Type.RESULT)
                .id("bind-1")
                .childElement(com.example.xmpp.protocol.model.extension.Bind.builder()
                        .jid("user@example.com/resource")
                        .build())
                .build());

        assertTrue(connection.isAuthenticated());

        connection.disconnect();

        assertFalse(connection.isAuthenticated());
        channel.finishAndReleaseAll();
    }

    @Test
    void testStaleChannelExceptionDoesNotFailCurrentLifecycle() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler staleHandler = new XmppNettyHandler(config, connection);
        EmbeddedChannel staleChannel = new EmbeddedChannel(staleHandler);
        XmppNettyHandler currentHandler = new XmppNettyHandler(config, connection);
        EmbeddedChannel currentChannel = new EmbeddedChannel(currentHandler);

        bindNettyHandler(connection, currentHandler);
        bindChannel(connection, currentChannel);

        staleChannel.pipeline().fireExceptionCaught(new IllegalStateException("stale boom"));
        staleChannel.runPendingTasks();
        staleChannel.runScheduledPendingTasks();

        assertSame(currentChannel, connection.getChannel());
        assertFalse(connection.getConnectionReadyFuture().isDone());

        staleChannel.finishAndReleaseAll();
        currentChannel.finishAndReleaseAll();
    }

    @Test
    void testStaleChannelInactiveDoesNotClearCurrentLifecycle() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler staleHandler = new XmppNettyHandler(config, connection);
        EmbeddedChannel staleChannel = new EmbeddedChannel(staleHandler);
        XmppNettyHandler currentHandler = new XmppNettyHandler(config, connection);
        EmbeddedChannel currentChannel = new EmbeddedChannel(currentHandler);

        bindNettyHandler(connection, currentHandler);
        bindChannel(connection, currentChannel);

        staleChannel.close();
        staleChannel.runPendingTasks();
        staleChannel.runScheduledPendingTasks();

        assertSame(currentChannel, connection.getChannel());
        assertFalse(connection.getConnectionReadyFuture().isDone());

        staleChannel.finishAndReleaseAll();
        currentChannel.finishAndReleaseAll();
    }

    @Test
    void testStaleStreamErrorDoesNotFailCurrentLifecycle() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler staleHandler = new XmppNettyHandler(config, connection);
        EmbeddedChannel staleChannel = new EmbeddedChannel(staleHandler);
        XmppNettyHandler currentHandler = new XmppNettyHandler(config, connection);
        EmbeddedChannel currentChannel = new EmbeddedChannel(currentHandler);

        bindNettyHandler(connection, currentHandler);
        bindChannel(connection, currentChannel);

        staleChannel.writeInbound(StreamError.builder()
                .condition(StreamError.Condition.NOT_AUTHORIZED)
                .text("stale stream error")
                .build());

        assertSame(currentChannel, connection.getChannel());
        assertFalse(connection.getConnectionReadyFuture().isDone());

        staleChannel.finishAndReleaseAll();
        currentChannel.finishAndReleaseAll();
    }

    @Test
    void testStaleStateContextCloseConnectionOnErrorDoesNotFailCurrentLifecycle() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        XmppNettyHandler staleHandler = new XmppNettyHandler(config, connection);
        EmbeddedChannel staleChannel = new EmbeddedChannel(staleHandler);
        XmppNettyHandler currentHandler = new XmppNettyHandler(config, connection);
        EmbeddedChannel currentChannel = new EmbeddedChannel(currentHandler);
        StateContext staleStateContext = new StateContext(config, connection, staleChannel.pipeline().lastContext());

        bindNettyHandler(connection, currentHandler);
        bindChannel(connection, currentChannel);

        staleStateContext.closeConnectionOnError(staleChannel.pipeline().lastContext(),
                "stale state failure");

        assertSame(currentChannel, connection.getChannel());
        assertFalse(connection.getConnectionReadyFuture().isDone());

        staleChannel.finishAndReleaseAll();
        currentChannel.finishAndReleaseAll();
    }

    @Test
    void testConnectAsyncWithNoTargetsReleasesLifecycleManagerSubscriptions() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("")
                .host("")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .pingEnabled(true)
                .reconnectionEnabled(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);

        assertThrows(Exception.class, connection::connect);
        assertEquals(0, getTotalSubscriberCount(connection));
    }

    @Test
    void testConnectAsyncUsesServiceDomainWhenHostIsBlank() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndHoldSocket(serverSocket, acceptedLatch));

            XmppClientConfig config = XmppClientConfig.builder()
                    .xmppServiceDomain("127.0.0.1")
                    .host("")
                    .port(serverSocket.getLocalPort())
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .readTimeout(1000)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> readyFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            connection.disconnect();

            assertThrows(CompletionException.class, readyFuture::join);
            waitUntilDisconnected(connection);
            assertFalse(connection.isConnected());
        }
    }

    @Test
    void testConnectAsyncPrefersHostAddressWhenConfigured() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndHoldSocket(serverSocket, acceptedLatch));

            XmppClientConfig config = XmppClientConfig.builder()
                    .xmppServiceDomain("example.com")
                    .host("invalid-host.example")
                    .hostAddress(InetAddress.getByName("127.0.0.1"))
                    .port(serverSocket.getLocalPort())
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .readTimeout(1000)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> readyFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            connection.disconnect();

            assertThrows(CompletionException.class, readyFuture::join);
            waitUntilDisconnected(connection);
            assertFalse(connection.isConnected());
        }
    }

    @Test
    void testRepeatedConnectFailuresDoNotAccumulateLifecycleManagerSubscriptions() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("127.0.0.1")
                .port(1)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .connectTimeout(200)
                .pingEnabled(true)
                .reconnectionEnabled(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);

        assertThrows(Exception.class, connection::connect);
        assertEquals(0, getTotalSubscriberCount(connection));

        assertThrows(Exception.class, connection::connect);
        assertEquals(0, getTotalSubscriberCount(connection));
    }

    @Test
    void testConnectAsyncCreatesNewReadyFutureAfterFailure() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("127.0.0.1")
                .port(1)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .connectTimeout(200)
                .pingEnabled(true)
                .reconnectionEnabled(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);

        CompletableFuture<Void> initialFuture = connection.getConnectionReadyFuture();
        assertThrows(Exception.class, connection::connect);
        CompletableFuture<Void> failedFuture = connection.getConnectionReadyFuture();

        assertNotSame(initialFuture, failedFuture);
        assertTrue(failedFuture.isCompletedExceptionally());

        assertThrows(Exception.class, connection::connect);
        CompletableFuture<Void> retriedFuture = connection.getConnectionReadyFuture();

        assertNotSame(failedFuture, retriedFuture);
        assertTrue(retriedFuture.isCompletedExceptionally());
        assertEquals(0, getTotalSubscriberCount(connection));
    }

    @Test
    void testDisconnectDuringInProgressConnectFailsReadyFutureAndClearsSubscriptions() throws Exception {
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
                    .pingEnabled(true)
                    .reconnectionEnabled(true)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> readyFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            connection.disconnect();

            CompletionException exception = assertThrows(CompletionException.class, readyFuture::join);
            assertInstanceOf(XmppNetworkException.class, exception.getCause());
            assertEquals(0, getTotalSubscriberCount(connection));
            waitUntilDisconnected(connection);
            assertFalse(connection.isConnected());
        }
    }

    @Test
    void testDisconnectIsIdempotentAfterConnectFailure() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("127.0.0.1")
                .port(1)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .connectTimeout(200)
                .pingEnabled(true)
                .reconnectionEnabled(true)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);

        assertThrows(Exception.class, connection::connect);

        connection.disconnect();
        connection.disconnect();

        assertEquals(0, getTotalSubscriberCount(connection));
        assertFalse(connection.isConnected());
    }

    @Test
    void testConnectAsyncCanRetryAfterDisconnectingInProgressConnection() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch firstAcceptedLatch = new CountDownLatch(1);
            CountDownLatch secondAcceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndHoldSockets(serverSocket, firstAcceptedLatch, secondAcceptedLatch));

            XmppClientConfig config = XmppClientConfig.builder()
                    .xmppServiceDomain("example.com")
                    .host("127.0.0.1")
                    .port(serverSocket.getLocalPort())
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .readTimeout(1000)
                    .pingEnabled(true)
                    .reconnectionEnabled(true)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(firstAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            connection.disconnect();
            assertThrows(CompletionException.class, firstFuture::join);

            CompletableFuture<Void> secondFuture = startConnect(connection);
            assertTrue(secondAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            assertNotSame(firstFuture, secondFuture);
            connection.disconnect();
            assertThrows(CompletionException.class, secondFuture::join);
            assertEquals(0, getTotalSubscriberCount(connection));
        }
    }

    @Test
    void testConcurrentConnectAndDisconnectLeavesConnectionClosed() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
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
                    .pingEnabled(true)
                    .reconnectionEnabled(true)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> connectInvocation = startConnect(connection, executor);

            CompletableFuture.runAsync(connection::disconnect, executor).join();

            assertThrows(CompletionException.class, connectInvocation::join);
            waitUntilDisconnected(connection);
            assertFalse(connection.isConnected());
            assertNull(connection.getChannel());
            assertEquals(0, getTotalSubscriberCount(connection));
        }
    }

    @Test
    void testConnectAsyncReusesCompletedReadyFutureWhenConnectionIsAlreadyActive() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            AtomicReference<Socket> acceptedSocketRef = new AtomicReference<>();
            executor.submit(() -> {
                try {
                    Socket socket = serverSocket.accept();
                    acceptedSocketRef.set(socket);
                    acceptedLatch.countDown();
                    socket.getInputStream().read();
                } catch (IOException ignored) {
                } finally {
                    Socket socket = acceptedSocketRef.get();
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            });

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

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            connection.markConnectionReady();
            firstFuture.join();

            CompletableFuture<Void> secondFuture = startConnect(connection);

            assertSame(firstFuture, secondFuture);
            assertTrue(secondFuture.isDone());

            connection.disconnect();
        }
    }

    @Test
    void testRemoteCloseAllowsNewReadyFutureOnReconnect() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch firstAcceptedLatch = new CountDownLatch(1);
            CountDownLatch closeFirstLatch = new CountDownLatch(1);
            CountDownLatch secondAcceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptCloseThenHoldSocket(serverSocket, firstAcceptedLatch, closeFirstLatch, secondAcceptedLatch));

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

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(firstAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            connection.markConnectionReady();
            firstFuture.join();

            closeFirstLatch.countDown();
            waitUntilDisconnected(connection);

            CompletableFuture<Void> secondFuture = startConnect(connection);
            assertTrue(secondAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            assertNotSame(firstFuture, secondFuture);
            connection.disconnect();
        }
    }

    @Test
    void testUnexpectedRemoteCloseTriggersReconnectAttemptWhenEnabled() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch firstAcceptedLatch = new CountDownLatch(1);
            CountDownLatch secondAcceptedLatch = new CountDownLatch(1);
            executor.submit(() -> {
                acceptAndCloseSocket(serverSocket, firstAcceptedLatch);
                acceptAndHoldSocket(serverSocket, secondAcceptedLatch);
            });

            XmppClientConfig config = XmppClientConfig.builder()
                    .xmppServiceDomain("example.com")
                    .host("127.0.0.1")
                    .port(serverSocket.getLocalPort())
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .readTimeout(1000)
                    .connectTimeout(500)
                    .reconnectionEnabled(true)
                    .pingEnabled(false)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(firstAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(CompletionException.class, firstFuture::join);

            assertTrue(secondAcceptedLatch.await(6, java.util.concurrent.TimeUnit.SECONDS),
                    "意外断链后应触发至少一次自动重连");

            connection.disconnect();
        }
    }

    @Test
    void testManualDisconnectDoesNotTriggerReconnectWhenEnabled() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch firstAcceptedLatch = new CountDownLatch(1);
            CountDownLatch secondAcceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndHoldThenTimeoutSecondSocket(serverSocket, firstAcceptedLatch, secondAcceptedLatch));

            XmppClientConfig config = XmppClientConfig.builder()
                    .xmppServiceDomain("example.com")
                    .host("127.0.0.1")
                    .port(serverSocket.getLocalPort())
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .readTimeout(1000)
                    .connectTimeout(500)
                    .reconnectionEnabled(true)
                    .pingEnabled(false)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(firstAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            connection.disconnect();
            assertThrows(CompletionException.class, firstFuture::join);

            Thread.sleep(3500);
            assertEquals(1L, secondAcceptedLatch.getCount(), "手动断开后不应触发自动重连");
        }
    }

    @Test
    void testReconnectPublishesConnectedEventPerSuccessfulTcpConnect() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch firstAcceptedLatch = new CountDownLatch(1);
            CountDownLatch closeFirstLatch = new CountDownLatch(1);
            CountDownLatch secondAcceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptCloseThenHoldSocket(serverSocket, firstAcceptedLatch, closeFirstLatch, secondAcceptedLatch));

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
            AtomicInteger connectedCount = new AtomicInteger();

            XmppEventBus.getInstance().subscribe(connection, ConnectionEventType.CONNECTED,
                    event -> connectedCount.incrementAndGet());

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(firstAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            connection.markConnectionReady();
            firstFuture.join();

            closeFirstLatch.countDown();
            waitUntilDisconnected(connection);

            CompletableFuture<Void> secondFuture = startConnect(connection);
            assertTrue(secondAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            Thread.sleep(200);

            assertEquals(2, connectedCount.get());

            connection.disconnect();
            assertThrows(CompletionException.class, secondFuture::join);
        }
    }

    @Test
    void testRemoteCloseFailsPendingCollectorImmediately() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            CountDownLatch closeLatch = new CountDownLatch(1);
            executor.submit(() -> acceptHoldThenCloseSocket(serverSocket, acceptedLatch, closeLatch));

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

            CompletableFuture<Void> readyFuture = startConnect(connection);
            assertTrue(acceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            connection.markConnectionReady();
            readyFuture.join();

            AsyncStanzaCollector collector = connection.createStanzaCollector(stanza -> false);
            closeLatch.countDown();

            CompletionException exception = assertThrows(CompletionException.class, () -> collector.getFuture().join());
            assertInstanceOf(XmppException.class, exception.getCause());
            waitUntilDisconnected(connection);
            assertFalse(connection.removeStanzaCollector(collector));
        }
    }

    @Test
    void testConnectAsyncCleansUpFailedActiveConnectionBeforeRetrying() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch firstAcceptedLatch = new CountDownLatch(1);
            CountDownLatch secondAcceptedLatch = new CountDownLatch(1);
            executor.submit(() -> acceptAndHoldSockets(serverSocket, firstAcceptedLatch, secondAcceptedLatch));

            XmppClientConfig config = XmppClientConfig.builder()
                    .xmppServiceDomain("example.com")
                    .host("127.0.0.1")
                    .port(serverSocket.getLocalPort())
                    .username("user")
                    .password("pass".toCharArray())
                    .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                    .readTimeout(1000)
                    .pingEnabled(true)
                    .reconnectionEnabled(true)
                    .build();
            XmppTcpConnection connection = new XmppTcpConnection(config);

            CompletableFuture<Void> firstFuture = startConnect(connection);
            assertTrue(firstAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var firstChannel = connection.getChannel();

            connection.failConnection(new XmppNetworkException("boom"));
            assertThrows(CompletionException.class, firstFuture::join);

            CompletableFuture<Void> secondFuture = startConnect(connection);
            assertTrue(secondAcceptedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));

            assertNotSame(firstFuture, secondFuture);
            assertNotSame(firstChannel, connection.getChannel());
            assertFalse(firstChannel.isActive(), "重试前应先关闭旧的失败连接");

            connection.disconnect();
            assertThrows(CompletionException.class, secondFuture::join);
        }
    }

    private void acceptAndHoldSocket(ServerSocket serverSocket, CountDownLatch acceptedLatch) {
        try (Socket socket = serverSocket.accept()) {
            acceptedLatch.countDown();
            socket.getInputStream().read();
        } catch (IOException ignored) {
        }
    }

    private void acceptAndHoldSocketUntilRelease(ServerSocket serverSocket,
                                                 CountDownLatch acceptedLatch,
                                                 CountDownLatch releaseLatch) {
        try (Socket socket = serverSocket.accept()) {
            acceptedLatch.countDown();
            releaseLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
        }
    }

    private void acceptAndCloseSocket(ServerSocket serverSocket, CountDownLatch acceptedLatch) {
        try (Socket socket = serverSocket.accept()) {
            acceptedLatch.countDown();
        } catch (IOException ignored) {
        }
    }

    private void acceptAndHoldSockets(ServerSocket serverSocket,
                                      CountDownLatch firstAcceptedLatch,
                                      CountDownLatch secondAcceptedLatch) {
        acceptAndHoldSocket(serverSocket, firstAcceptedLatch);
        acceptAndHoldSocket(serverSocket, secondAcceptedLatch);
    }

    private void acceptCloseThenHoldSocket(ServerSocket serverSocket,
                                           CountDownLatch firstAcceptedLatch,
                                           CountDownLatch closeFirstLatch,
                                           CountDownLatch secondAcceptedLatch) {
        try (Socket firstSocket = serverSocket.accept()) {
            firstAcceptedLatch.countDown();
            closeFirstLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
        }
        acceptAndHoldSocket(serverSocket, secondAcceptedLatch);
    }

    private void acceptHoldThenCloseSocket(ServerSocket serverSocket,
                                           CountDownLatch acceptedLatch,
                                           CountDownLatch closeLatch) {
        try (Socket socket = serverSocket.accept()) {
            acceptedLatch.countDown();
            closeLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
        }
    }

    private Object getWorkerGroup(XmppTcpConnection connection) throws Exception {
        Field field = XmppTcpConnection.class.getDeclaredField("workerGroup");
        field.setAccessible(true);
        return field.get(connection);
    }

    private void waitFor(Check check, long timeoutMs, String message) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (check.evaluate()) {
                return;
            }
            Thread.sleep(50);
        }
        assertTrue(check.evaluate(), message);
    }

    @FunctionalInterface
    private interface Check {
        boolean evaluate() throws Exception;
    }

    private void acceptAndHoldThenTimeoutSecondSocket(ServerSocket serverSocket,
                                                      CountDownLatch firstAcceptedLatch,
                                                      CountDownLatch secondAcceptedLatch) {
        acceptAndHoldSocket(serverSocket, firstAcceptedLatch);
        try {
            serverSocket.setSoTimeout(3000);
            try (Socket ignored = serverSocket.accept()) {
                secondAcceptedLatch.countDown();
            }
        } catch (SocketTimeoutException ignored) {
        } catch (IOException ignored) {
        }
    }

    private void waitUntilDisconnected(XmppTcpConnection connection) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (!connection.isConnected() && connection.getChannel() == null) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("连接未在预期时间内进入断开状态");
    }

    private void bindNettyHandler(XmppTcpConnection connection, XmppNettyHandler handler) {
        try {
            Field field = XmppTcpConnection.class.getDeclaredField("nettyHandler");
            field.setAccessible(true);
            field.set(connection, handler);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法绑定测试用 nettyHandler", e);
        }
    }

    private EmbeddedChannel newBoundChannel(XmppTcpConnection connection, XmppNettyHandler handler) {
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        bindNettyHandler(connection, handler);
        bindChannel(connection, channel);
        return channel;
    }

    private void bindChannel(XmppTcpConnection connection, io.netty.channel.Channel channel) {
        try {
            Field field = XmppTcpConnection.class.getDeclaredField("channel");
            field.setAccessible(true);
            field.set(connection, channel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法绑定测试用 channel", e);
        }
    }

    private CompletableFuture<Void> startConnect(XmppTcpConnection connection) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.connect();
            } catch (XmppException e) {
                throw new CompletionException(e);
            }
        });
    }

    private CompletableFuture<Void> startConnect(XmppTcpConnection connection, ExecutorService executor) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.connect();
            } catch (XmppException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private int getTotalSubscriberCount(XmppTcpConnection connection) {
        try {
            Field field = XmppEventBus.class.getDeclaredField("listeners");
            field.setAccessible(true);
            Object listenersObject = field.get(XmppEventBus.getInstance());
            Map<XmppTcpConnection, Map<?, List<?>>> listeners =
                    (Map<XmppTcpConnection, Map<?, List<?>>>) listenersObject;
            Map<?, List<?>> connectionListeners = listeners.get(connection);
            if (connectionListeners == null) {
                return 0;
            }
            return connectionListeners.values().stream()
                    .mapToInt(List::size)
                    .sum();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法读取 XmppEventBus 订阅状态", e);
        }
    }
}
