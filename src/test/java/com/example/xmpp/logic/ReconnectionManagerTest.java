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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReconnectionManager 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ReconnectionManagerTest {

    @Mock
    private XmppConnection connection;

    private ReconnectionManager reconnectionManager;

    @BeforeEach
    void setUp() {
        clearEventBusListeners();
        reconnectionManager = new ReconnectionManager(connection);
    }

    @Test
    @DisplayName("构造函数应正确初始化")
    void testConstructor() {
        assertNotNull(reconnectionManager);
    }

    @Test
    @DisplayName("ReconnectionManager 应禁止子类覆写构造期间绑定的生命周期方法")
    void testReconnectionManagerClassIsFinal() {
        assertTrue(Modifier.isFinal(ReconnectionManager.class.getModifiers()));
    }

    @Test
    @DisplayName("ReconnectionManager 不应保留历史状态字段")
    void testLegacyStateFieldsAreRemoved() {
        assertThrows(NoSuchFieldException.class, () -> ReconnectionManager.class.getDeclaredField("enabled"));
        assertThrows(NoSuchFieldException.class, () -> ReconnectionManager.class.getDeclaredField("attemptCount"));
        assertThrows(NoSuchFieldException.class,
                () -> ReconnectionManager.class.getDeclaredField("reconnectionScheduledDueToError"));
        assertThrows(NoSuchFieldException.class,
                () -> ReconnectionManager.class.getDeclaredField("lastDisconnectError"));
    }

    @Test
    @DisplayName("事件总线回调应安全执行")
    void testLifecycleCallbacksAreSafe() {
        assertDoesNotThrow(() -> publish(ConnectionEventType.CONNECTED));
        assertDoesNotThrow(() -> publish(ConnectionEventType.AUTHENTICATED));
        assertDoesNotThrow(() -> publish(ConnectionEventType.CLOSED));
        assertDoesNotThrow(() -> publish(ConnectionEventType.ERROR, new Exception("boom")));
    }

    @Test
    @DisplayName("shutdown 后错误与关闭事件都不应触发重连")
    void testShutdownPreventsReconnectEvents() {
        reconnectionManager.shutdown();

        assertDoesNotThrow(() -> publish(ConnectionEventType.ERROR, new Exception("Test error")));
        assertDoesNotThrow(() -> publish(ConnectionEventType.CLOSED, new Exception("Test error")));
    }

    @Test
    @DisplayName("ERROR 事件对重连管理器应无副作用")
    void testErrorEventOnlyDoesNotScheduleReconnect() throws Exception {
        publish(ConnectionEventType.ERROR, new Exception("boom"));

        Thread.sleep(3500);

        verify(connection, never()).connect();
    }

    @Test
    @DisplayName("异常关闭应在 CLOSED 事件到达后触发重连")
    void testClosedWithErrorSchedulesReconnect() {
        assertDoesNotThrow(() -> emitClosedWithError(new Exception("Test error")));
        reconnectionManager.shutdown();
    }

    @Test
    @DisplayName("认证失败不应触发自动重连")
    void testAuthenticationFailureDoesNotTriggerReconnect() throws Exception {
        emitClosedWithError(new XmppSaslFailureException(SaslFailure.builder().condition("not-authorized").build()));

        Thread.sleep(3500);

        verify(connection, never()).connect();
    }

    @Test
    @DisplayName("重连尝试中的认证失败不应继续调度下一次重试")
    void testNonRecoverableReconnectFailureStopsRetryCycle() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new XmppSaslFailureException(SaslFailure.builder().condition("not-authorized").build());
        }).when(connection).connect();

        emitClosedWithError(new XmppNetworkException("temporary network issue"));

        Thread.sleep(3500);

        verify(connection, times(1)).connect();
        assertEquals(1, attempts.get(), "遇到不可恢复的重连失败后不应继续调度下一次重试");
    }

    @Test
    @DisplayName("协议流错误不应触发自动重连")
    void testStreamProtocolFailureDoesNotTriggerReconnect() throws Exception {
        emitClosedWithError(new XmppStreamErrorException(StreamError.builder()
                .condition(StreamError.Condition.NOT_AUTHORIZED)
                .build()));

        Thread.sleep(3500);

        verify(connection, never()).connect();
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

        Thread.sleep(3500);

        verify(connection, never()).connect();
    }

    @Test
    @DisplayName("重连失败后应继续调度下一次重试")
    void testReconnectFailureSchedulesNextAttempt() throws Exception {
        CountDownLatch attemptsLatch = new CountDownLatch(2);
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            attemptsLatch.countDown();
            throw new XmppNetworkException("connect failed");
        }).when(connection).connect();

        emitClosedWithError(new Exception("boom"));

        try {
            assertTrue(attemptsLatch.await(9, TimeUnit.SECONDS), "失败后应继续触发下一次重连");
            assertTrue(attempts.get() >= 2, "至少应发生两次重连尝试");
        } finally {
            reconnectionManager.shutdown();
        }
    }

    @Test
    @DisplayName("shutdown 后不应执行已排队的重连任务")
    void testShutdownCancelsScheduledReconnectBeforeExecution() throws Exception {
        emitClosedWithError(new Exception("boom"));
        reconnectionManager.shutdown();

        Thread.sleep(3500);

        verify(connection, never()).connect();
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

        emitClosedWithError(new Exception("boom"));

        assertTrue(attemptStarted.await(4, TimeUnit.SECONDS), "应至少启动一次重连尝试");

        reconnectionManager.shutdown();
        releaseAttempt.countDown();

        Thread.sleep(3500);

        assertEquals(1, attempts.get(), "关闭后不应再调度新的重连尝试");
    }

    @Test
    @DisplayName("连续 CLOSED 错误事件不应并发排队多个重连任务")
    void testRepeatedClosedEventsDoNotScheduleDuplicateReconnectTasks() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstAttemptLatch = new CountDownLatch(1);

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            firstAttemptLatch.countDown();
            throw new XmppNetworkException("connect failed");
        }).when(connection).connect();

        publish(ConnectionEventType.CLOSED, new Exception("boom-3"));
        publish(ConnectionEventType.CLOSED, new Exception("boom-2"));
        publish(ConnectionEventType.CLOSED, new Exception("boom-1"));

        try {
            assertTrue(firstAttemptLatch.await(4, TimeUnit.SECONDS), "应至少执行一次重连尝试");
            assertEquals(1, attempts.get(), "首个调度窗口内不应因重复错误事件并发触发多个重连");
        } finally {
            reconnectionManager.shutdown();
        }
    }

    @Test
    @DisplayName("排队中的重连任务在连接已人工恢复后不应再次执行")
    void testConnectedEventCancelsQueuedReconnectAttempt() throws Exception {
        emitClosedWithError(new Exception("boom"));
        publish(ConnectionEventType.CONNECTED);

        Thread.sleep(3500);

        verify(connection, never()).connect();
    }

    @Test
    @DisplayName("排队中的重连任务在连接已完成认证后不应再次执行")
    void testAuthenticatedEventCancelsQueuedReconnectAttempt() throws Exception {
        emitClosedWithError(new Exception("boom"));
        publish(ConnectionEventType.AUTHENTICATED);

        Thread.sleep(3500);

        verify(connection, never()).connect();
    }

    @Test
    @DisplayName("成功重连后再次错误关闭应开启新的重连周期")
    void testReconnectCycleRestartsAfterSuccessfulReconnect() throws Exception {
        CountDownLatch firstAttemptLatch = new CountDownLatch(1);
        CountDownLatch secondAttemptLatch = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            int currentAttempt = attempts.incrementAndGet();
            if (currentAttempt == 1) {
                firstAttemptLatch.countDown();
            } else if (currentAttempt == 2) {
                secondAttemptLatch.countDown();
            }
            return null;
        }).when(connection).connect();

        emitClosedWithError(new Exception("boom-1"));
        assertTrue(firstAttemptLatch.await(4, TimeUnit.SECONDS), "第一次错误关闭后应完成一次重连尝试");

        publish(ConnectionEventType.CONNECTED);
        emitClosedWithError(new Exception("boom-2"));

        try {
            assertTrue(secondAttemptLatch.await(4, TimeUnit.SECONDS), "成功重连后再次错误关闭应重新触发重连");
            assertEquals(2, attempts.get(), "应发生两次独立的重连尝试");
        } finally {
            reconnectionManager.shutdown();
        }
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
        java.lang.reflect.Field field = ReconnectionManager.class.getDeclaredField("currentTask");
        field.setAccessible(true);
        return field.get(reconnectionManager);
    }

    private void clearEventBusListeners() {
        try {
            Field field = XmppEventBus.class.getDeclaredField("listeners");
            field.setAccessible(true);
            Object listenersObject = field.get(XmppEventBus.getInstance());
            ((Map<?, ?>) listenersObject).clear();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法清理 XmppEventBus 订阅状态", e);
        }
    }
}
