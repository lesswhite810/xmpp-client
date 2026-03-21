package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.exception.XmppSaslFailureException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.exception.XmppStreamErrorException;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.stream.StreamError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReconnectionManager 行为测试。
 */
class ReconnectionManagerTest {

    @Mock
    private XmppConnection connection;

    private AutoCloseable mocks;

    private ReconnectionManager reconnectionManager;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        reconnectionManager = new ReconnectionManager(connection);
    }

    @AfterEach
    void tearDownMocks() throws Exception {
        if (reconnectionManager != null) {
            reconnectionManager.shutdown();
        }
        XmppEventBus.getInstance().unsubscribeAll(connection);
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("不应再暴露指定回连完成事件的构造函数")
    void testConstructorDoesNotSupportCustomReconnectReadyEvent() {
        assertThrows(NoSuchMethodException.class,
                () -> ReconnectionManager.class.getDeclaredConstructor(XmppConnection.class, ConnectionEventType.class));
    }

    @Test
    @DisplayName("CLOSED 且无错误时应销毁管理器且不调度重连")
    void testClosedWithoutErrorDoesNotScheduleReconnect() throws Exception {
        publish(ConnectionEventType.CLOSED, null);

        verify(connection, never()).connect();
        assertNull(getCurrentTask());
        assertNull(getUnsubscribe());

        emitClosedWithError(new Exception("boom"));
        assertNull(getCurrentTask());
    }

    @Test
    @DisplayName("异常关闭应在 CLOSED 事件到达后触发重连")
    void testClosedWithErrorSchedulesReconnect() throws Exception {
        publish(ConnectionEventType.CLOSED, new Exception("boom"));

        assertNotNull(getCurrentTask());
        reconnectionManager.shutdown();
    }

    @Test
    @DisplayName("认证失败不应触发自动重连")
    void testAuthenticationFailureDoesNotTriggerReconnect() throws Exception {
        emitClosedWithError(new XmppSaslFailureException(new SaslFailure("not-authorized", null)));

        verify(connection, never()).connect();
        assertNull(getCurrentTask());
    }

    @Test
    @DisplayName("协议流错误不应触发自动重连")
    void testStreamProtocolFailureDoesNotTriggerReconnect() throws Exception {
        emitClosedWithError(new XmppStreamErrorException(StreamError.builder()
                .condition(StreamError.Condition.NOT_AUTHORIZED)
                .build()));

        verify(connection, never()).connect();
        assertNull(getCurrentTask());
    }

    @Test
    @DisplayName("节错误异常不应触发自动重连")
    void testStanzaErrorDoesNotTriggerReconnect() throws Exception {
        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                .id("bind-1")
                .error(new XmppError.Builder(XmppError.Condition.NOT_AUTHORIZED)
                        .type(XmppError.Type.AUTH)
                        .text("binding failed")
                        .build())
                .build();

        emitClosedWithError(new XmppStanzaErrorException("bind failed", errorIq));

        verify(connection, never()).connect();
        assertNull(getCurrentTask());
    }

    @Test
    @DisplayName("重连失败后应继续调度下一次重试")
    void testReconnectFailureSchedulesNextAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new XmppNetworkException("connect failed");
        }).when(connection).connect();

        invokeRunReconnectAttempt(0);

        assertEquals(1, attempts.get());
        assertNotNull(getCurrentTask());
        reconnectionManager.shutdown();
    }

    @Test
    @DisplayName("运行时异常重连失败后也应继续调度下一次重试")
    void testRuntimeReconnectFailureSchedulesNextAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new RuntimeException("boom");
        }).when(connection).connect();

        invokeRunReconnectAttempt(0);

        assertEquals(1, attempts.get());
        assertNotNull(getCurrentTask());
        reconnectionManager.shutdown();
    }

    @Test
    @DisplayName("shutdown 后不应执行已排队的重连任务")
    void testShutdownCancelsScheduledReconnectBeforeExecution() throws Exception {
        emitClosedWithError(new Exception("boom"));
        reconnectionManager.shutdown();

        verify(connection, never()).connect();
        assertNull(getCurrentTask());
    }

    @Test
    @DisplayName("关闭管理器后运行中的失败重连不应继续调度下一次重试")
    void testShutdownPreventsRescheduleAfterRunningReconnectFailure() throws Exception {
        CountDownLatch attemptStarted = new CountDownLatch(1);
        CountDownLatch releaseAttempt = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            attemptStarted.countDown();
            assertTrue(releaseAttempt.await(3, TimeUnit.SECONDS), "测试应释放当前重连尝试");
            throw new XmppNetworkException("connect failed");
        }).when(connection).connect();

        Thread runner = new Thread(() -> {
            try {
                invokeRunReconnectAttempt(0);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        runner.start();

        assertTrue(attemptStarted.await(1, TimeUnit.SECONDS), "应至少启动一次重连尝试");
        reconnectionManager.shutdown();
        releaseAttempt.countDown();
        runner.join(1000);

        assertEquals(1, attempts.get(), "关闭后不应再调度新的重连尝试");
        assertNull(getCurrentTask());
    }

    @Test
    @DisplayName("连续 CLOSED 错误事件不应并发排队多个重连任务")
    void testRepeatedClosedEventsDoNotScheduleDuplicateReconnectTasks() throws Exception {
        publish(ConnectionEventType.CLOSED, new Exception("boom-3"));
        Object firstTask = getCurrentTask();
        publish(ConnectionEventType.CLOSED, new Exception("boom-2"));
        publish(ConnectionEventType.CLOSED, new Exception("boom-1"));

        assertNotNull(firstTask, "第一次错误关闭后应已排队重连");
        assertNotNull(getCurrentTask(), "重复错误事件后仍应只有一个活动重连任务");
        assertTrue(((ScheduledFuture<?>) firstTask).isCancelled(),
                "旧任务应在重复错误事件到来时被取消");
        verify(connection, never()).connect();
        reconnectionManager.shutdown();
    }

    @Test
    @DisplayName("排队中的重连任务在连接已人工恢复后不应再次执行")
    void testConnectedEventCancelsQueuedReconnectAttempt() throws Exception {
        emitClosedWithError(new Exception("boom"));
        publish(ConnectionEventType.CONNECTED);

        verify(connection, never()).connect();
        assertNull(getCurrentTask());
    }

    @Test
    @DisplayName("AUTHENTICATED 事件不应取消排队中的重连任务")
    void testAuthenticatedEventDoesNotCancelQueuedReconnectAttempt() throws Exception {
        emitClosedWithError(new Exception("boom"));
        publish(ConnectionEventType.AUTHENTICATED);

        verify(connection, never()).connect();
        assertNotNull(getCurrentTask());
    }

    @Test
    @DisplayName("成功重连后再次错误关闭应开启新的重连周期")
    void testReconnectCycleRestartsAfterSuccessfulReconnect() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            return null;
        }).when(connection).connect();

        invokeRunReconnectAttempt(0);
        assertEquals(1, attempts.get(), "第一次错误关闭后应完成一次重连尝试");

        publish(ConnectionEventType.CONNECTED);
        emitClosedWithError(new Exception("boom-2"));

        invokeRunReconnectAttempt(0);

        assertEquals(2, attempts.get(), "成功重连后再次错误关闭应重新触发重连");
        reconnectionManager.shutdown();
    }

    @Test
    @DisplayName("高退避轮次下仍应继续调度重连，只受最大延迟限制")
    void testHighBackoffAttemptStillSchedulesReconnect() throws Exception {
        Method method = ReconnectionManager.class.getDeclaredMethod("scheduleReconnect", int.class);
        method.setAccessible(true);

        method.invoke(reconnectionManager, 100);

        assertNotNull(getCurrentTask(), "高退避轮次下仍应继续调度重连任务");
        reconnectionManager.shutdown();
    }

    @Test
    @DisplayName("已连接时重连尝试应直接跳过")
    void testReconnectAttemptSkipsWhenAlreadyConnected() throws Exception {
        when(connection.isConnected()).thenReturn(true);

        invokeRunReconnectAttempt(0);

        verify(connection, never()).connect();
        assertNull(getCurrentTask());
    }

    private void emitClosedWithError(Exception error) {
        publish(ConnectionEventType.CLOSED, error);
    }

    private void publish(ConnectionEventType eventType) {
        XmppEventBus.getInstance().publish(connection, eventType, null);
    }

    private void publish(ConnectionEventType eventType, Exception error) {
        XmppEventBus.getInstance().publish(connection, eventType, error);
    }

    private Object getCurrentTask() throws Exception {
        Field field = ReconnectionManager.class.getDeclaredField("currentTask");
        field.setAccessible(true);
        return field.get(reconnectionManager);
    }

    private Runnable getUnsubscribe() throws Exception {
        Field field = ReconnectionManager.class.getDeclaredField("unsubscribe");
        field.setAccessible(true);
        return (Runnable) field.get(reconnectionManager);
    }

    private void invokeRunReconnectAttempt(int retryIndex) throws Exception {
        Method method = ReconnectionManager.class.getDeclaredMethod("runReconnectAttempt", int.class);
        method.setAccessible(true);
        method.invoke(reconnectionManager, retryIndex);
    }
}
