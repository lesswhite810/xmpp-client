package com.example.xmpp.logic;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.XmppConnection;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        // 清除 XmppEventBus 中的事件订阅
        XmppEventBus.getInstance().clear();

        reconnectionManager = new ReconnectionManager(connection);
    }

    @Test
    @DisplayName("构造函数应正确初始化")
    void testConstructor() {
        assertNotNull(reconnectionManager);
    }

    @Test
    @DisplayName("应支持启用自动重连")
    void testEnable() {
        assertDoesNotThrow(() -> reconnectionManager.enable());
    }

    @Test
    @DisplayName("应支持禁用自动重连")
    void testDisable() {
        assertDoesNotThrow(() -> reconnectionManager.disable());
    }

    @Test
    @DisplayName("多次启用/禁用应安全执行")
    void testMultipleEnableDisable() {
        assertDoesNotThrow(() -> {
            reconnectionManager.enable();
            reconnectionManager.enable();
            reconnectionManager.disable();
            reconnectionManager.disable();
            reconnectionManager.enable();
        });
    }

    @Test
    @DisplayName("onEvent(CONNECTED) 回调应安全执行")
    void testConnectedEventCallback() {
        assertDoesNotThrow(() -> reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED)));
    }

    @Test
    @DisplayName("onEvent(AUTHENTICATED) 回调应安全执行")
    void testAuthenticatedEventCallback() {
        assertDoesNotThrow(() -> reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED)));
        assertDoesNotThrow(() -> reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED)));
    }

    @Test
    @DisplayName("onEvent(CLOSED) 回调应安全执行")
    void testConnectionClosedEventCallback() {
        assertDoesNotThrow(() -> reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CLOSED)));
    }

    @Test
    @DisplayName("onEvent(ERROR) 回调应安全执行")
    void testConnectionClosedOnErrorEventCallback() {
        reconnectionManager.disable(); // 禁用避免实际重连

        assertDoesNotThrow(() -> reconnectionManager.onEvent(
                new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("Test error"))));
    }

    @Test
    @DisplayName("启用状态下错误关闭应触发重连")
    void testConnectionClosedOnErrorWithEnabled() {
        reconnectionManager.enable();

        assertDoesNotThrow(() -> reconnectionManager.onEvent(
                new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("Test error"))));
    }

    @Test
    @DisplayName("认证失败不应触发自动重连")
    void testAuthenticationFailureDoesNotTriggerReconnect() throws Exception {
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR,
                new XmppSaslFailureException(SaslFailure.builder().condition("not-authorized").build())));

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

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR,
                new XmppNetworkException("temporary network issue")));

        Thread.sleep(3500);

        verify(connection, times(1)).connect();
        assertEquals(1, attempts.get(), "遇到不可恢复的重连失败后不应继续调度下一次重试");
        assertEquals(0, getAttemptCount().get(), "不可恢复失败后应重置重连计数");
    }

    @Test
    @DisplayName("协议流错误不应触发自动重连")
    void testStreamProtocolFailureDoesNotTriggerReconnect() throws Exception {
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR,
                new XmppStreamErrorException(StreamError.builder()
                        .condition(StreamError.Condition.NOT_AUTHORIZED)
                        .build())));

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
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR,
                new XmppStanzaErrorException("bind failed", errorIq)));

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

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));

        try {
            assertTrue(attemptsLatch.await(9, TimeUnit.SECONDS), "失败后应继续触发下一次重连");
            assertTrue(attempts.get() >= 2, "至少应发生两次重连尝试");
        } finally {
            reconnectionManager.shutdown();
        }
    }

    @Test
    @DisplayName("禁用重连后不应执行已排队的重连任务")
    void testDisableCancelsScheduledReconnectBeforeExecution() throws Exception {
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));
        reconnectionManager.disable();

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

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));

        assertTrue(attemptStarted.await(4, TimeUnit.SECONDS), "应至少启动一次重连尝试");

        reconnectionManager.shutdown();
        releaseAttempt.countDown();

        Thread.sleep(3500);

        assertEquals(1, attempts.get(), "关闭后不应再调度新的重连尝试");
    }

    @Test
    @DisplayName("禁用重连时运行中的成功重连应立即关闭连接")
    void testDisableClosesConnectionWhenRunningReconnectSucceeds() throws Exception {
        CountDownLatch attemptStarted = new CountDownLatch(1);
        CountDownLatch releaseAttempt = new CountDownLatch(1);

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attemptStarted.countDown();
            assertTrue(releaseAttempt.await(3, TimeUnit.SECONDS), "测试应释放当前重连尝试");
            return null;
        }).when(connection).connect();

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));

        assertTrue(attemptStarted.await(4, TimeUnit.SECONDS), "应启动一次重连尝试");
        reconnectionManager.disable();
        releaseAttempt.countDown();

        Thread.sleep(500);

        verify(connection, times(1)).disconnect();
    }

    @Test
    @DisplayName("禁用后重新启用应重置重连周期计数")
    void testDisableThenEnableStartsFreshReconnectCycle() throws Exception {
        CountDownLatch attemptsLatch = new CountDownLatch(2);
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            attemptsLatch.countDown();
            throw new XmppNetworkException("connect failed");
        }).when(connection).connect();

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-1")));
        Thread.sleep(500);
        reconnectionManager.disable();
        reconnectionManager.enable();
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-2")));

        try {
            assertTrue(attemptsLatch.await(9, TimeUnit.SECONDS), "重新启用后应开启新的重连周期");
            assertTrue(attempts.get() >= 2, "两轮错误关闭都应触发重连尝试");
        } finally {
            reconnectionManager.shutdown();
        }
    }

    @Test
    @DisplayName("连续错误事件不应并发排队多个重连任务")
    void testRepeatedErrorEventsDoNotScheduleDuplicateReconnectTasks() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstAttemptLatch = new CountDownLatch(1);

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            firstAttemptLatch.countDown();
            throw new XmppNetworkException("connect failed");
        }).when(connection).connect();

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-1")));
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-2")));
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-3")));

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
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));

        Thread.sleep(3500);

        verify(connection, never()).connect();
    }

    @Test
    @DisplayName("排队中的重连任务在连接已完成认证后不应再次执行")
    void testAuthenticatedEventCancelsQueuedReconnectAttempt() throws Exception {
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED));

        Thread.sleep(3500);

        verify(connection, never()).connect();
    }

    @Test
    @DisplayName("仅 CONNECTED 事件不应重置重连计数")
    void testConnectedEventDoesNotResetReconnectAttemptCount() throws Exception {
        getAttemptCount().set(3);

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));

        assertTrue(getAttemptCount().get() >= 3, "TCP 连接成功但未认证前，不应清空重连退避计数");
    }

    @Test
    @DisplayName("CONNECTED 事件应清理错误触发的重连标记")
    void testConnectedEventClearsReconnectScheduledDueToErrorFlag() throws Exception {
        setReconnectionScheduledDueToError(true);

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));

        assertFalse(isReconnectionScheduledDueToError(), "TCP 已连接后不应继续保留旧的错误重连标记");
    }

    @Test
    @DisplayName("AUTHENTICATED 事件应重置重连计数")
    void testAuthenticatedEventResetsReconnectAttemptCount() throws Exception {
        getAttemptCount().set(3);

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED));

        assertEquals(0, getAttemptCount().get());
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

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-1")));
        assertTrue(firstAttemptLatch.await(4, TimeUnit.SECONDS), "第一次错误关闭后应完成一次重连尝试");

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-2")));

        try {
            assertTrue(secondAttemptLatch.await(4, TimeUnit.SECONDS), "成功重连后再次错误关闭应重新触发重连");
            assertEquals(2, attempts.get(), "应发生两次独立的重连尝试");
        } finally {
            reconnectionManager.shutdown();
        }
    }

    @Test
    @DisplayName("达到最大重连次数后不应继续执行新的重连尝试")
    void testMaxReconnectAttemptsStopsFurtherReconnects() throws Exception {
        getAttemptCount().set(getMaxReconnectAttempts());

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));

        Thread.sleep(1000);

        verify(connection, never()).connect();
        assertEquals(0, getAttemptCount().get(), "达到最大次数后应重置计数");
    }

    @Test
    @DisplayName("达到最大重连次数并重置后，新的错误关闭应开启新重连周期")
    void testReconnectCycleRestartsAfterMaxAttemptsReset() throws Exception {
        getAttemptCount().set(getMaxReconnectAttempts());
        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-1")));

        CountDownLatch attemptLatch = new CountDownLatch(1);
        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attemptLatch.countDown();
            return null;
        }).when(connection).connect();

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom-2")));

        try {
            assertTrue(attemptLatch.await(4, TimeUnit.SECONDS), "计数重置后新的错误关闭应重新触发重连");
            verify(connection, times(1)).connect();
        } finally {
            reconnectionManager.shutdown();
        }
    }

    private AtomicInteger getAttemptCount() throws Exception {
        Field field = ReconnectionManager.class.getDeclaredField("attemptCount");
        field.setAccessible(true);
        return (AtomicInteger) field.get(reconnectionManager);
    }

    private int getMaxReconnectAttempts() throws Exception {
        Field field = ReconnectionManager.class.getDeclaredField("MAX_RECONNECT_ATTEMPTS");
        field.setAccessible(true);
        return field.getInt(null);
    }

    private boolean isReconnectionScheduledDueToError() throws Exception {
        Field field = ReconnectionManager.class.getDeclaredField("reconnectionScheduledDueToError");
        field.setAccessible(true);
        return field.getBoolean(reconnectionManager);
    }

    private void setReconnectionScheduledDueToError(boolean value) throws Exception {
        Field field = ReconnectionManager.class.getDeclaredField("reconnectionScheduledDueToError");
        field.setAccessible(true);
        field.setBoolean(reconnectionManager, value);
    }
}
