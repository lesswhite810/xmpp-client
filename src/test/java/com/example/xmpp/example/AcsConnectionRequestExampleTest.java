package com.example.xmpp.example;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.logic.ConnectionRequestManager;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.net.ConnectException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * ACS ConnectionRequest 示例行为测试。
 */
class AcsConnectionRequestExampleTest {

    private final XmppEventBus eventBus = XmppEventBus.getInstance();

    private final XmppConnection connection = mock(XmppConnection.class);

    @AfterEach
    void tearDown() {
        eventBus.unsubscribeAll(connection);
    }

    @Test
    @DisplayName("注册事件监听后应处理认证/错误事件，shutdown 后应断开连接")
    void testRegisterListenersAndShutdown() throws Exception {
        AcsConnectionRequestExample example = new AcsConnectionRequestExample(connection);
        example.registerEventListeners();

        Field retryCountField = AcsConnectionRequestExample.class.getDeclaredField("retryCount");
        retryCountField.setAccessible(true);
        ((AtomicInteger) retryCountField.get(example)).set(2);

        eventBus.publish(connection, ConnectionEventType.ERROR, new XmppAuthException("bad auth"));
        eventBus.publish(connection, ConnectionEventType.ERROR, new ConnectException("refused"));
        eventBus.publish(connection, ConnectionEventType.ERROR, new RuntimeException("boom"));
        eventBus.publish(connection, ConnectionEventType.AUTHENTICATED);
        assertEquals(0, ((AtomicInteger) retryCountField.get(example)).get());

        example.shutdown();

        verify(connection).disconnect();
        eventBus.publish(connection, ConnectionEventType.CLOSED);
    }

    @Test
    @DisplayName("shutdown 后再次发布事件不应继续影响示例状态")
    void testShutdownRemovesEventListeners() throws Exception {
        AcsConnectionRequestExample example = new AcsConnectionRequestExample(connection);
        example.registerEventListeners();

        Field retryCountField = AcsConnectionRequestExample.class.getDeclaredField("retryCount");
        retryCountField.setAccessible(true);
        AtomicInteger retryCount = (AtomicInteger) retryCountField.get(example);
        retryCount.set(5);

        example.shutdown();
        eventBus.publish(connection, ConnectionEventType.AUTHENTICATED);

        assertEquals(5, retryCount.get());
        verify(connection).disconnect();
    }

    @Test
    @DisplayName("发送方法应委托给 ConnectionRequestManager")
    void testSendMethodsDelegateToRequestManager() throws Exception {
        AcsConnectionRequestExample example = new AcsConnectionRequestExample(connection);
        ConnectionRequestManager manager = mock(ConnectionRequestManager.class);
        XmppStanza stanza = mock(XmppStanza.class);
        CompletableFuture<XmppStanza> responseFuture = CompletableFuture.completedFuture(stanza);

        Field managerField = AcsConnectionRequestExample.class.getDeclaredField("requestManager");
        managerField.setAccessible(true);
        managerField.set(example, manager);

        when(manager.sendConnectionRequest("cpe@example.com", "user", "password")).thenReturn(responseFuture);
        when(manager.sendConnectionRequestWithRetry("cpe@example.com", "user", "password", 3))
                .thenReturn(responseFuture);

        assertSame(responseFuture, example.sendConnectionRequest("cpe@example.com", "user", "password"));
        assertSame(responseFuture, example.sendConnectionRequestWithRetry("cpe@example.com", "user", "password", 3));

        verify(manager).sendConnectionRequest("cpe@example.com", "user", "password");
        verify(manager).sendConnectionRequestWithRetry("cpe@example.com", "user", "password", 3);
    }

    @Test
    @DisplayName("构造函数应创建默认 ConnectionRequestManager")
    void testConstructorInitializesRequestManager() throws Exception {
        AcsConnectionRequestExample example = new AcsConnectionRequestExample(connection);

        Field managerField = AcsConnectionRequestExample.class.getDeclaredField("requestManager");
        managerField.setAccessible(true);

        assertNotNull(managerField.get(example));
    }

    @Test
    @DisplayName("main 成功路径应发送普通请求和带重试请求")
    void testMainSuccessPath() throws Exception {
        XmppStanza stanza = mock(XmppStanza.class);
        CompletableFuture<XmppStanza> responseFuture = CompletableFuture.completedFuture(stanza);

        try (MockedConstruction<XmppTcpConnection> connectionMocked = mockConstruction(XmppTcpConnection.class);
                MockedConstruction<ConnectionRequestManager> managerMocked = mockConstruction(
                        ConnectionRequestManager.class,
                        (mock, context) -> {
                            when(mock.sendConnectionRequest(anyString(), anyString(), anyString()))
                                    .thenReturn(responseFuture);
                            when(mock.sendConnectionRequestWithRetry(anyString(), anyString(), anyString(), anyInt()))
                                    .thenReturn(responseFuture);
                        })) {
            assertDoesNotThrow(() -> AcsConnectionRequestExample.main(new String[0]));

            verify(connectionMocked.constructed().getFirst()).connect();
            ConnectionRequestManager manager = managerMocked.constructed().getFirst();
            verify(manager).sendConnectionRequest("cpe001@example.com", "cpe-username", "cpe-password");
            verify(manager).sendConnectionRequestWithRetry("cpe001@example.com", "cpe-username", "cpe-password", 3);
        }
    }

    @Test
    @DisplayName("main 遇到 ACS 认证失败时不应向外抛异常")
    void testMainHandlesAuthFailure() throws Exception {
        try (MockedConstruction<XmppTcpConnection> connectionMocked = mockConstruction(
                XmppTcpConnection.class,
                (mock, context) -> doThrow(new XmppAuthException("bad auth")).when(mock).connect());
                MockedConstruction<ConnectionRequestManager> ignored = mockConstruction(ConnectionRequestManager.class)) {
            assertDoesNotThrow(() -> AcsConnectionRequestExample.main(new String[0]));
            verify(connectionMocked.constructed().getFirst()).connect();
        }
    }

    @Test
    @DisplayName("main 遇到 CPE 未授权错误时不应向外抛异常")
    void testMainHandlesNotAuthorizedCpeFailure() throws Exception {
        XmppError error = new XmppError.Builder(XmppError.Condition.NOT_AUTHORIZED).build();
        Iq errorIq = Iq.createErrorResponse(
                new Iq.Builder(Iq.Type.GET).id("iq-1").from("acs@example.com").to("cpe@example.com").build(),
                error);
        XmppStanzaErrorException stanzaError = new XmppStanzaErrorException("not authorized", errorIq);
        CompletableFuture<XmppStanza> failedRequest = new CompletableFuture<>();
        failedRequest.completeExceptionally(new CompletionException(stanzaError));

        try (MockedConstruction<XmppTcpConnection> connectionMocked = mockConstruction(XmppTcpConnection.class);
                MockedConstruction<ConnectionRequestManager> managerMocked = mockConstruction(
                        ConnectionRequestManager.class,
                        (mock, context) -> {
                            when(mock.sendConnectionRequest(anyString(), anyString(), anyString()))
                                    .thenReturn(failedRequest);
                            when(mock.sendConnectionRequestWithRetry(anyString(), anyString(), anyString(), anyInt()))
                                    .thenReturn(CompletableFuture.completedFuture(mock(XmppStanza.class)));
                        })) {
            assertDoesNotThrow(() -> AcsConnectionRequestExample.main(new String[0]));
            verify(connectionMocked.constructed().getFirst()).connect();
            verify(managerMocked.constructed().getFirst())
                    .sendConnectionRequest("cpe001@example.com", "cpe-username", "cpe-password");
        }
    }

    @Test
    @DisplayName("main 遇到普通异常时应进入通用错误处理")
    void testMainHandlesGenericException() throws Exception {
        CompletableFuture<XmppStanza> failedRetry = new CompletableFuture<>();
        failedRetry.completeExceptionally(new IllegalStateException("boom"));

        try (MockedConstruction<XmppTcpConnection> connectionMocked = mockConstruction(XmppTcpConnection.class);
                MockedConstruction<ConnectionRequestManager> managerMocked = mockConstruction(
                        ConnectionRequestManager.class,
                        (mock, context) -> {
                            when(mock.sendConnectionRequest(anyString(), anyString(), anyString()))
                                    .thenReturn(CompletableFuture.completedFuture(mock(XmppStanza.class)));
                            when(mock.sendConnectionRequestWithRetry(anyString(), anyString(), anyString(), anyInt()))
                                    .thenReturn(failedRetry);
                        })) {
            assertDoesNotThrow(() -> AcsConnectionRequestExample.main(new String[0]));
            verify(connectionMocked.constructed().getFirst()).connect();
            verify(managerMocked.constructed().getFirst())
                    .sendConnectionRequestWithRetry("cpe001@example.com", "cpe-username", "cpe-password", 3);
        }
    }
}
