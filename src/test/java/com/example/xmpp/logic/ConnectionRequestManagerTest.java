package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppProtocolException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConnectionRequestManager 单元测试。
 */
class ConnectionRequestManagerTest {

    @Mock
    private XmppConnection connection;

    private AutoCloseable mocks;

    private ConnectionRequestManager manager;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        manager = new ConnectionRequestManager(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        XmppEventBus.getInstance().unsubscribeAll(connection);
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("sendConnectionRequest 应发送正确的 IQ")
    void testSendConnectionRequest() {
        String cpeJid = "cpe@example.com";
        String username = "test-user";
        String password = "test-password";

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(XmppStanza.class)));

        manager.sendConnectionRequest(cpeJid, username, password);

        ArgumentCaptor<Iq> iqCaptor = ArgumentCaptor.forClass(Iq.class);
        verify(connection).sendIqPacketAsync(iqCaptor.capture(), eq(30000L), eq(TimeUnit.MILLISECONDS));

        Iq sentIq = iqCaptor.getValue();
        assertEquals(Iq.Type.SET, sentIq.getType());
        assertEquals(cpeJid, sentIq.getTo());
        assertNotNull(sentIq.getChildElement());
        assertTrue(sentIq.getChildElement().getElementName().equals("connectionRequest"));
    }

    @Test
    @DisplayName("sendConnectionRequest 应返回服务端成功响应")
    void testSendConnectionRequestReturnsResponse() {
        String cpeJid = "cpe@example.com";
        XmppStanza response = mock(XmppStanza.class);

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        XmppStanza result = manager.sendConnectionRequest(cpeJid, "test-user", "test-password").join();

        assertSame(response, result);
    }

    @Test
    @DisplayName("sendConnectionRequest null JID 应抛出异常")
    void testSendConnectionRequestWithNullJid() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.sendConnectionRequest(null, "user", "pass"));
    }

    @Test
    @DisplayName("sendConnectionRequest 空 JID 应抛出异常")
    void testSendConnectionRequestWithBlankJid() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.sendConnectionRequest("  ", "user", "pass"));
    }

    @Test
    @DisplayName("sendConnectionRequest null username 应抛出异常")
    void testSendConnectionRequestWithNullUsername() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.sendConnectionRequest("cpe@example.com", null, "pass"));
    }

    @Test
    @DisplayName("sendConnectionRequest null password 应抛出异常")
    void testSendConnectionRequestWithNullPassword() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.sendConnectionRequest("cpe@example.com", "user", null));
    }

    @Test
    @DisplayName("发送失败时应正确处理异常")
    void testSendConnectionRequestFailure() {
        String cpeJid = "cpe@example.com";
        CompletableFuture<XmppStanza> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Network error"));

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(failedFuture);

        manager.sendConnectionRequest(cpeJid, "user", "pass")
                .whenComplete((response, error) -> {
                    assertNotNull(error);
                    assertTrue(error.getMessage().contains("Network error"));
                });
    }

    @Test
    @DisplayName("未连接时应返回失败Future")
    void testSendConnectionRequestWhenDisconnected() {
        String cpeJid = "cpe@example.com";

        when(connection.isConnected()).thenReturn(false);

        manager.sendConnectionRequest(cpeJid, "user", "pass")
                .whenComplete((response, error) -> {
                    assertNotNull(error);
                    assertTrue(error.getCause() instanceof ConnectException);
                });

        verify(connection, never()).sendIqPacketAsync(any(), anyLong(), any());
    }

    @Test
    @DisplayName("错误响应缺少 error 元素时应抛出协议异常")
    void testSendConnectionRequestMissingErrorDetails() {
        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                .id("connreq-1")
                .build();

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(errorIq));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> manager.sendConnectionRequest("cpe@example.com", "user", "pass").join());

        assertInstanceOf(XmppProtocolException.class, exception.getCause());
    }

    @Test
    @DisplayName("错误响应 not-authorized 应映射为认证异常")
    void testSendConnectionRequestNotAuthorizedError() {
        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                .id("connreq-2")
                .error(new XmppError.Builder(XmppError.Condition.NOT_AUTHORIZED)
                        .type(XmppError.Type.AUTH)
                        .build())
                .build();

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(errorIq));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> manager.sendConnectionRequest("cpe@example.com", "user", "pass").join());

        assertInstanceOf(XmppAuthException.class, exception.getCause());
    }

    @Test
    @DisplayName("错误响应 recipient-unavailable 应映射为可重试的节异常")
    void testSendConnectionRequestRecipientUnavailableError() {
        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                .id("connreq-3")
                .error(new XmppError.Builder(XmppError.Condition.RECIPIENT_UNAVAILABLE)
                        .type(XmppError.Type.WAIT)
                        .build())
                .build();

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(errorIq));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> manager.sendConnectionRequest("cpe@example.com", "user", "pass").join());

        assertInstanceOf(XmppStanzaErrorException.class, exception.getCause());
        assertTrue(((XmppStanzaErrorException) exception.getCause()).getXmppError()
                .getCondition() == XmppError.Condition.RECIPIENT_UNAVAILABLE);
    }

    @Test
    @DisplayName("错误响应的其他 condition 应映射为通用节异常")
    void testSendConnectionRequestUnknownError() {
        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                .id("connreq-4")
                .error(new XmppError.Builder(XmppError.Condition.INTERNAL_SERVER_ERROR)
                        .type(XmppError.Type.CANCEL)
                        .build())
                .build();

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(errorIq));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> manager.sendConnectionRequest("cpe@example.com", "user", "pass").join());

        assertInstanceOf(XmppStanzaErrorException.class, exception.getCause());
    }

    @Test
    @DisplayName("sendConnectionRequestWithRetry 应在未连接时等待重连完成")
    void testSendConnectionRequestWithRetryWaitsForReconnect() {
        ConnectionRequestManager retryManager = new ConnectionRequestManager(connection, 5000, 10);
        XmppStanza response = mock(XmppStanza.class);
        when(connection.isConnected()).thenReturn(false, true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<XmppStanza> future = retryManager.sendConnectionRequestWithRetry(
                "cpe@example.com", "user", "pass", 1);

        XmppEventBus.getInstance().publish(connection, ConnectionEventType.CONNECTED, null);

        assertSame(response, future.join());
    }

    @Test
    @DisplayName("sendConnectionRequestWithRetry 已连接时不应注册 CONNECTED 监听")
    void testSendConnectionRequestWithRetryWhenAlreadyConnectedDoesNotSubscribeReconnectEvent() throws Exception {
        ConnectionRequestManager retryManager = new ConnectionRequestManager(connection, 5000, 50);
        XmppStanza response = mock(XmppStanza.class);
        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        assertSame(response, retryManager.sendConnectionRequestWithRetry(
                "cpe@example.com", "user", "pass", 2).join());

        assertEquals(0, listenerCount(ConnectionEventType.CONNECTED));
    }

    @Test
    @DisplayName("sendConnectionRequestWithRetry 重连成功后应取消 CONNECTED 订阅")
    void testSendConnectionRequestWithRetryUnsubscribesAfterReconnect() throws Exception {
        ConnectionRequestManager retryManager = new ConnectionRequestManager(connection, 5000, 50);
        XmppStanza response = mock(XmppStanza.class);
        when(connection.isConnected()).thenReturn(false, true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<XmppStanza> future = retryManager.sendConnectionRequestWithRetry(
                "cpe@example.com", "user", "pass", 1);
        waitForListenerCount(ConnectionEventType.CONNECTED, 1);

        XmppEventBus.getInstance().publish(connection, ConnectionEventType.CONNECTED, null);

        assertSame(response, future.join());
        waitForListenerCount(ConnectionEventType.CONNECTED, 0);
    }

    @Test
    @DisplayName("sendConnectionRequestWithRetry 在等待重连超时时应失败")
    void testSendConnectionRequestWithRetryTimesOut() {
        ConnectionRequestManager retryManager = new ConnectionRequestManager(connection, 5000, 10);
        when(connection.isConnected()).thenReturn(false);

        CompletableFuture<XmppStanza> future = retryManager.sendConnectionRequestWithRetry(
                "cpe@example.com", "user", "pass", 1);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(ConnectException.class, exception.getCause());
    }

    @Test
    @DisplayName("sendConnectionRequestWithRetry 超时后应清理 CONNECTED 监听")
    void testSendConnectionRequestWithRetryRemovesReconnectListenerAfterTimeout() throws Exception {
        ConnectionRequestManager retryManager = new ConnectionRequestManager(connection, 5000, 10);
        when(connection.isConnected()).thenReturn(false);

        CompletableFuture<XmppStanza> future = retryManager.sendConnectionRequestWithRetry(
                "cpe@example.com", "user", "pass", 1);

        CompletionException exception = assertThrows(CompletionException.class, future::join);

        assertInstanceOf(ConnectException.class, exception.getCause());
        waitForListenerCount(ConnectionEventType.CONNECTED, 0);
    }

    @Test
    @DisplayName("sendConnectionRequestWithRetry 在达到最大重试次数时应失败")
    void testSendConnectionRequestWithRetryStopsAtMaxRetries() {
        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                .id("connreq-5")
                .error(new XmppError.Builder(XmppError.Condition.RECIPIENT_UNAVAILABLE)
                        .type(XmppError.Type.WAIT)
                        .build())
                .build();
        ConnectionRequestManager retryManager = new ConnectionRequestManager(connection, 5000, 10);

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(errorIq));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> retryManager.sendConnectionRequestWithRetry(
                        "cpe@example.com", "user", "pass", 1).join());

        assertInstanceOf(XmppStanzaErrorException.class, exception.getCause());
    }

    @Test
    @DisplayName("sendConnectionRequestWithRetry 遇到不可重试异常时应立即失败")
    void testSendConnectionRequestWithRetryFailsFastOnNonRetryableError() {
        CompletableFuture<XmppStanza> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("boom"));
        ConnectionRequestManager retryManager = new ConnectionRequestManager(connection, 5000, 10);

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(failedFuture);

        CompletionException exception = assertThrows(CompletionException.class,
                () -> retryManager.sendConnectionRequestWithRetry(
                        "cpe@example.com", "user", "pass", 3).join());

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("sendConnectionRequestWithRetry 遇到可重试超时后应再次发送")
    void testSendConnectionRequestWithRetryRetriesAfterTimeoutFailure() {
        ConnectionRequestManager retryManager = new ConnectionRequestManager(connection, 5000, 10);
        CompletableFuture<XmppStanza> timeoutFuture = new CompletableFuture<>();
        timeoutFuture.completeExceptionally(new TimeoutException("timeout"));
        XmppStanza response = mock(XmppStanza.class);

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(timeoutFuture)
                .thenReturn(CompletableFuture.completedFuture(response));

        XmppStanza result = retryManager.sendConnectionRequestWithRetry(
                "cpe@example.com", "user", "pass", 2).join();

        assertSame(response, result);
        verify(connection, times(2)).sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("IQ ID 应以 connreq- 为前缀")
    void testIqIdPrefix() {
        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(XmppStanza.class)));

        manager.sendConnectionRequest("cpe@example.com", "user", "pass");

        ArgumentCaptor<Iq> iqCaptor = ArgumentCaptor.forClass(Iq.class);
        verify(connection).sendIqPacketAsync(iqCaptor.capture(), anyLong(), any(TimeUnit.class));

        String iqId = iqCaptor.getValue().getId();
        assertNotNull(iqId);
        assertTrue(iqId.startsWith("connreq-"), "IQ ID should start with 'connreq-'");
    }

    @Test
    @DisplayName("getTimeoutMs 应返回配置的超时时间")
    void testGetTimeoutMs() {
        ConnectionRequestManager customManager = new ConnectionRequestManager(connection, 10000);
        assertEquals(10000, customManager.getTimeoutMs());
    }

    @Test
    @DisplayName("getRetryDelayMs 应返回配置的重连等待时间")
    void testGetRetryDelayMs() {
        ConnectionRequestManager customManager = new ConnectionRequestManager(connection, 30000, 8000);
        assertEquals(8000, customManager.getRetryDelayMs());
    }

    private void waitForListenerCount(ConnectionEventType eventType, int expectedCount) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (listenerCount(eventType) == expectedCount) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expectedCount, listenerCount(eventType));
    }

    private int listenerCount(ConnectionEventType eventType) throws Exception {
        Field field = XmppEventBus.class.getDeclaredField("listeners");
        field.setAccessible(true);
        Object listenersObject = field.get(XmppEventBus.getInstance());
        if (!(listenersObject instanceof Map<?, ?> listeners)) {
            return 0;
        }
        Object connectionListeners = listeners.get(connection);
        if (!(connectionListeners instanceof Map<?, ?> eventListeners)) {
            return 0;
        }
        Object handlers = eventListeners.get(eventType);
        if (!(handlers instanceof List<?> handlerList)) {
            return 0;
        }
        return handlerList.size();
    }
}
