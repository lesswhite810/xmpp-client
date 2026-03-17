package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IQ 协议错误传播测试。
 */
class XmppIqErrorPropagationTest {

    private TestXmppConnection connection;

    @BeforeEach
    void setUp() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .build();
        connection = new TestXmppConnection(config);
    }

    @Test
    @DisplayName("sendIqPacketAsync 收到 iq error 时应异常完成")
    void testSendIqPacketAsyncCompletesExceptionallyOnIqError() {
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id("iq-error-1")
                .to("server@example.com")
                .build();

        CompletableFuture<XmppStanza> future = connection.sendIqPacketAsync(request);

        Iq errorResponse = Iq.createErrorResponse(
                request,
                new XmppError.Builder(XmppError.Condition.SERVICE_UNAVAILABLE)
                        .text("service unavailable")
                        .build()
        );
        connection.notifyStanzaReceived(errorResponse);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        XmppStanzaErrorException cause = assertInstanceOf(XmppStanzaErrorException.class, exception.getCause());
        assertEquals("iq-error-1", cause.getErrorIq().getId());
        assertEquals(Iq.Type.ERROR, cause.getErrorIq().getType());
        assertNotNull(cause.getXmppError());
        assertEquals(XmppError.Condition.SERVICE_UNAVAILABLE, cause.getXmppError().getCondition());
    }

    @Test
    @DisplayName("sendIqPacketAsync 底层发送失败时应立即异常完成并清理 collector")
    void testSendIqPacketAsyncFailsImmediatelyWhenDispatchFails() {
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id("iq-send-fail-1")
                .to("server@example.com")
                .build();
        connection = new FailingDispatchXmppConnection(connection.getConfig());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.sendIqPacketAsync(request).join());

        assertInstanceOf(XmppNetworkException.class, exception.getCause());
        assertTrue(connection.getCollectors().isEmpty());
    }

    @Test
    @DisplayName("sendIqPacketAsync 传入 null IQ 时应拒绝")
    void testSendIqPacketAsyncRejectsNullIq() {
        assertThrows(IllegalArgumentException.class, () -> connection.sendIqPacketAsync(null));
    }

    @Test
    @DisplayName("sendIqPacketAsync 传入空 ID 时应拒绝")
    void testSendIqPacketAsyncRejectsBlankIqId() {
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id(" ")
                .to("server@example.com")
                .build();

        assertThrows(IllegalArgumentException.class, () -> connection.sendIqPacketAsync(request));
    }

    @Test
    @DisplayName("sendIqPacketAsync 传入非正超时时间时应拒绝")
    void testSendIqPacketAsyncRejectsNonPositiveTimeout() {
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id("iq-timeout-1")
                .to("server@example.com")
                .build();

        assertThrows(IllegalArgumentException.class, () -> connection.sendIqPacketAsync(request, 0, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("sendIqPacketAsync 传入空时间单位时应拒绝")
    void testSendIqPacketAsyncRejectsNullTimeUnit() {
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id("iq-unit-1")
                .to("server@example.com")
                .build();

        assertThrows(IllegalArgumentException.class, () -> connection.sendIqPacketAsync(request, 1, null));
    }

    @Test
    @DisplayName("连接关闭时 pending IQ 应立即异常完成并清理 collector")
    void testPendingIqFailsImmediatelyWhenConnectionCloses() {
        DisconnectableTestXmppConnection disconnectableConnection =
                new DisconnectableTestXmppConnection(connection.getConfig());
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id("iq-disconnect-1")
                .to("server@example.com")
                .build();

        CompletableFuture<XmppStanza> future = disconnectableConnection.sendIqPacketAsync(request);
        disconnectableConnection.closeWithError(new XmppNetworkException("Connection closed"));

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(XmppNetworkException.class, exception.getCause());
        assertTrue(disconnectableConnection.getCollectors().isEmpty());
    }

    @Test
    @DisplayName("sendIqPacketAsync 超时后应清理 collector")
    void testSendIqPacketAsyncTimeoutCleansCollector() {
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id("iq-timeout-cleanup-1")
                .to("server@example.com")
                .build();

        CompletableFuture<XmppStanza> future = connection.sendIqPacketAsync(request, 50, TimeUnit.MILLISECONDS);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(TimeoutException.class, exception.getCause());
        assertTrue(connection.getCollectors().isEmpty());
    }

    @Test
    @DisplayName("底层发送未完成时连接关闭也应立即结束 pending IQ")
    void testPendingIqFailsImmediatelyWhenConnectionClosesBeforeDispatchCompletes() {
        PendingDispatchXmppConnection pendingConnection =
                new PendingDispatchXmppConnection(connection.getConfig());
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id("iq-disconnect-pending-dispatch-1")
                .to("server@example.com")
                .build();

        CompletableFuture<XmppStanza> future = pendingConnection.sendIqPacketAsync(request);
        pendingConnection.closeWithError(new XmppNetworkException("Connection closed"));

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(XmppNetworkException.class, exception.getCause());
        assertTrue(pendingConnection.getCollectors().isEmpty());
        assertFalse(pendingConnection.getDispatchFuture().isDone());
    }

    private static class TestXmppConnection extends AbstractXmppConnection {

        private final XmppClientConfig config;

        private TestXmppConnection(XmppClientConfig config) {
            this.config = config;
        }

        @Override
        public void connect() {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public void sendStanza(XmppStanza stanza) {
        }

        @Override
        protected CompletableFuture<Void> dispatchStanza(XmppStanza stanza) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public XmppClientConfig getConfig() {
            return config;
        }

        protected java.util.Queue<?> getCollectors() {
            return collectors;
        }
    }

    private static class DisconnectableTestXmppConnection extends TestXmppConnection {

        private DisconnectableTestXmppConnection(XmppClientConfig config) {
            super(config);
        }

        protected void closeWithError(Exception exception) {
            failPendingCollectors(exception);
            cleanupCollectors();
        }
    }

    private static final class FailingDispatchXmppConnection extends TestXmppConnection {

        private FailingDispatchXmppConnection(XmppClientConfig config) {
            super(config);
        }

        @Override
        protected CompletableFuture<Void> dispatchStanza(XmppStanza stanza) {
            return CompletableFuture.failedFuture(new XmppNetworkException("Channel is not active"));
        }
    }

    private static final class PendingDispatchXmppConnection extends DisconnectableTestXmppConnection {

        private final CompletableFuture<Void> dispatchFuture = new CompletableFuture<>();

        private PendingDispatchXmppConnection(XmppClientConfig config) {
            super(config);
        }

        @Override
        protected CompletableFuture<Void> dispatchStanza(XmppStanza stanza) {
            return dispatchFuture;
        }

        private CompletableFuture<Void> getDispatchFuture() {
            return dispatchFuture;
        }
    }
}
