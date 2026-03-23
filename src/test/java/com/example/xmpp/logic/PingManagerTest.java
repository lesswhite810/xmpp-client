package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PingManager 行为测试。
 */
class PingManagerTest {

    @Mock
    private XmppConnection connection;

    private AutoCloseable mocks;

    private PingManager pingManager;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        pingManager = new PingManager(connection);
    }

    @AfterEach
    void tearDownMocks() throws Exception {
        if (pingManager != null) {
            pingManager.shutdown();
        }
        XmppEventBus.getInstance().unsubscribeAll(connection);
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("不应再暴露指定启动事件的构造函数")
    void testConstructorDoesNotSupportCustomStartEvent() {
        assertThrows(NoSuchMethodException.class,
                () -> PingManager.class.getDeclaredConstructor(XmppConnection.class, ConnectionEventType.class));
    }

    @Test
    @DisplayName("setPingInterval 应重调度已运行的保活任务")
    void testSetPingIntervalReschedulesActiveTask() throws Exception {
        pingManager.setPingInterval(1);
        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));
        ScheduledFuture<?> firstTask = getKeepAliveTask();

        pingManager.setPingInterval(2);
        ScheduledFuture<?> secondTask = getKeepAliveTask();

        assertNotNull(firstTask);
        assertNotNull(secondTask);
        assertNotSame(firstTask, secondTask);
        assertTrue(firstTask.isCancelled(), "更新间隔后旧保活任务应被取消");
    }

    @Test
    @DisplayName("setPingInterval 为 0 应抛出异常")
    void testDisableKeepAlive() {
        assertThrows(IllegalArgumentException.class, () -> pingManager.setPingInterval(0));
    }

    @Test
    @DisplayName("setPingInterval 负值应抛出异常")
    void testNegativePingInterval() {
        assertThrows(IllegalArgumentException.class, () -> pingManager.setPingInterval(-1));
    }

    @Test
    @DisplayName("onEvent(CONNECTED) 应启动保活任务")
    void testConnectedEventStartsKeepAlive() throws Exception {
        pingManager.setPingInterval(1);

        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));

        assertNotNull(getKeepAliveTask());
    }

    @Test
    @DisplayName("onEvent(AUTHENTICATED) 不应启动保活任务")
    void testAuthenticatedEventDoesNotStartKeepAlive() throws Exception {
        pingManager.setPingInterval(1);

        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED));

        assertNull(getKeepAliveTask());
    }

    @Test
    @DisplayName("收到 CLOSED 事件后应关闭并销毁 PingManager")
    void testClosedEventShutsDownManager() throws Exception {
        pingManager.setPingInterval(1);
        XmppEventBus.getInstance().publish(connection, ConnectionEventType.CONNECTED);

        ScheduledFuture<?> keepAliveTask = getKeepAliveTask();
        assertNotNull(keepAliveTask);

        XmppEventBus.getInstance().publish(connection, ConnectionEventType.CLOSED);

        assertNull(getKeepAliveTask());
        assertTrue(keepAliveTask.isCancelled());
        assertNull(getUnsubscribe());

        XmppEventBus.getInstance().publish(connection, ConnectionEventType.CONNECTED);
        assertNull(getKeepAliveTask());
    }

    @Test
    @DisplayName("onEvent(ERROR) 应取消已启动的保活任务")
    void testConnectionErrorEventShutsDown() throws Exception {
        pingManager.setPingInterval(1);
        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));

        ScheduledFuture<?> keepAliveTask = getKeepAliveTask();
        assertNotNull(keepAliveTask);

        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR));

        assertNull(getKeepAliveTask());
        assertTrue(keepAliveTask.isCancelled());
    }

    @Test
    @DisplayName("shutdown 后不应被 CONNECTED 事件重新启动")
    void testShutdownPreventsRestartOnConnectedEvent() throws Exception {
        pingManager.setPingInterval(1);
        pingManager.shutdown();
        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));

        verify(connection, never()).sendIqPacketAsync(any());
        assertNull(getKeepAliveTask());
    }

    @Test
    @DisplayName("shutdown 后修改间隔不应重新启动保活任务")
    void testShutdownPreventsRestartOnIntervalUpdate() throws Exception {
        pingManager.setPingInterval(1);
        pingManager.shutdown();
        pingManager.setPingInterval(1);

        verify(connection, never()).sendIqPacketAsync(any());
        assertNull(getKeepAliveTask());
    }

    @Test
    @DisplayName("未收到 CONNECTED 事件前修改间隔不应提前调度")
    void testSetPingIntervalDoesNotScheduleTaskBeforeConnected() throws Exception {
        pingManager.setPingInterval(3);

        assertNull(getKeepAliveTask());
        verify(connection, never()).sendIqPacketAsync(any());
    }

    @Test
    @DisplayName("重复 CONNECTED 事件应替换旧保活任务而不是叠加多个任务")
    void testRepeatedConnectedEventReplacesPreviousKeepAliveTask() throws Exception {
        pingManager.setPingInterval(30);

        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));
        ScheduledFuture<?> firstTask = getKeepAliveTask();

        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CONNECTED));
        ScheduledFuture<?> secondTask = getKeepAliveTask();

        assertNotNull(firstTask);
        assertNotNull(secondTask);
        assertNotSame(firstTask, secondTask);
        assertTrue(firstTask.isCancelled(), "重复认证后旧保活任务应被取消");
    }

    @Test
    @DisplayName("未连接时保活任务不应发送 Ping")
    void testKeepAliveDoesNotSendPingWhenDisconnected() throws Exception {
        when(connection.isConnected()).thenReturn(false);

        invokeSendPing();

        verify(connection, never()).sendIqPacketAsync(any());
    }

    @Test
    @DisplayName("未认证时保活任务不应发送 Ping")
    void testKeepAliveDoesNotSendPingWhenUnauthenticated() throws Exception {
        when(connection.isConnected()).thenReturn(true);
        when(connection.isAuthenticated()).thenReturn(false);

        invokeSendPing();

        verify(connection, never()).sendIqPacketAsync(any());
    }

    @Test
    @DisplayName("已连接且已认证时应发送 Ping IQ")
    void testSendPingWhenConnectedAndAuthenticated() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .build();
        when(connection.getConfig()).thenReturn(config);
        when(connection.isConnected()).thenReturn(true);
        when(connection.isAuthenticated()).thenReturn(true);
        when(connection.sendIqPacketAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        invokeSendPing();

        ArgumentCaptor<Iq> captor = ArgumentCaptor.forClass(Iq.class);
        verify(connection).sendIqPacketAsync(captor.capture());
        Iq pingIq = captor.getValue();
        assertEquals(Iq.Type.GET, pingIq.getType());
        assertEquals("example.com", pingIq.getTo());
        assertTrue(pingIq.getChildElement() instanceof Ping);
        assertFalse(pingIq.getId().isBlank());
    }

    @Test
    @DisplayName("Ping 失败时应吞掉异常而不向外抛出")
    void testSendPingHandlesFailedFuture() throws Exception {
        Logger logger = (Logger) LogManager.getLogger(PingManager.class);
        TestLogAppender appender = attachAppender("pingFailure", logger);
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .build();
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("boom"));

        try {
            when(connection.getConfig()).thenReturn(config);
            when(connection.isConnected()).thenReturn(true);
            when(connection.isAuthenticated()).thenReturn(true);
            when(connection.sendIqPacketAsync(any())).thenReturn((CompletableFuture) failedFuture);

            invokeSendPing();

            verify(connection).sendIqPacketAsync(any(Iq.class));
            assertTrue(appender.containsAtLevel("Keepalive Ping failed - ErrorType: IllegalStateException", Level.ERROR));
        } finally {
            detachAppender(appender, logger);
        }
    }

    @Test
    @DisplayName("Ping 成功返回 ERROR IQ 也应作为完成结果处理")
    void testSendPingAcceptsErrorIqCompletion() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .build();
        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                .id("ping-error")
                .build();

        when(connection.getConfig()).thenReturn(config);
        when(connection.isConnected()).thenReturn(true);
        when(connection.isAuthenticated()).thenReturn(true);
        when(connection.sendIqPacketAsync(any())).thenReturn(CompletableFuture.completedFuture(errorIq));

        invokeSendPing();

        verify(connection).sendIqPacketAsync(any(Iq.class));
    }

    private void invokeSendPing() throws Exception {
        Method method = PingManager.class.getDeclaredMethod("sendPing");
        method.setAccessible(true);
        method.invoke(pingManager);
    }

    private ScheduledFuture<?> getKeepAliveTask() throws Exception {
        Field field = PingManager.class.getDeclaredField("keepAliveTask");
        field.setAccessible(true);
        return (ScheduledFuture<?>) field.get(pingManager);
    }

    private Runnable getUnsubscribe() throws Exception {
        Field field = PingManager.class.getDeclaredField("unsubscribe");
        field.setAccessible(true);
        return (Runnable) field.get(pingManager);
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
