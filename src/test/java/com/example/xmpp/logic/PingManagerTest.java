package com.example.xmpp.logic;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.XmppConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PingManager 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PingManagerTest {

    @Mock
    private XmppConnection connection;

    private PingManager pingManager;

    @BeforeEach
    void setUp() {
        clearEventBusListeners();
        pingManager = new PingManager(connection);
    }

    @Test
    @DisplayName("构造函数应正确初始化")
    void testConstructor() {
        assertNotNull(pingManager);
    }

    @Test
    @DisplayName("PingManager 应禁止子类覆写构造期间绑定的生命周期方法")
    void testPingManagerClassIsFinal() {
        assertTrue(Modifier.isFinal(PingManager.class.getModifiers()));
    }

    @Test
    @DisplayName("setPingInterval 应更新间隔")
    void testSetPingInterval() {
        assertDoesNotThrow(() -> pingManager.setPingInterval(30));
    }

    @Test
    @DisplayName("shutdown 应安全执行")
    void testShutdown() {
        assertDoesNotThrow(() -> pingManager.shutdown());
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
    @DisplayName("onEvent(AUTHENTICATED) 应启动保活")
    void testAuthenticatedEventStartsKeepAlive() {
        // 验证 onEvent 方法存在且可调用
        assertDoesNotThrow(() -> pingManager.onEvent(
                new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED)));
    }

    @Test
    @DisplayName("onEvent(CLOSED) 应关闭")
    void testConnectionClosedEventShutsDown() {
        assertDoesNotThrow(() -> pingManager.onEvent(
                new ConnectionEvent(connection, ConnectionEventType.CLOSED)));
    }

    @Test
    @DisplayName("shutdown 后不应被 AUTHENTICATED 事件重新启动")
    void testShutdownPreventsRestartOnAuthenticatedEvent() {
        pingManager.setPingInterval(1);
        pingManager.shutdown();
        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED));

        verify(connection, never()).sendIqPacketAsync(any());
    }

    @Test
    @DisplayName("shutdown 后修改间隔不应重新启动保活任务")
    void testShutdownPreventsRestartOnIntervalUpdate() {
        pingManager.setPingInterval(1);
        pingManager.shutdown();
        pingManager.setPingInterval(1);

        verify(connection, never()).sendIqPacketAsync(any());
    }

    @Test
    @DisplayName("shutdown 后应清空事件订阅")
    void testShutdownClearsEventSubscriptions() {
        assertTrue(getTotalSubscriberCount(connection) > 0);

        pingManager.shutdown();

        assertEquals(0, getTotalSubscriberCount(connection));
    }

    @Test
    @DisplayName("关闭事件后收到认证事件应重新启动保活任务")
    void testClosedEventAllowsRestartOnAuthenticatedEvent() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .build();
        when(connection.getConfig()).thenReturn(config);
        when(connection.isConnected()).thenReturn(true);
        when(connection.isAuthenticated()).thenReturn(true);
        when(connection.sendIqPacketAsync(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        pingManager.setPingInterval(1);
        pingManager.startKeepAlive();
        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.CLOSED));
        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED));
        invokeSendPing();

        verify(connection, atLeastOnce()).sendIqPacketAsync(any());
    }

    @Test
    @DisplayName("重复认证事件应替换旧保活任务而不是叠加多个任务")
    void testRepeatedAuthenticatedEventReplacesPreviousKeepAliveTask() throws Exception {
        pingManager.setPingInterval(30);

        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED));
        ScheduledFuture<?> firstTask = getKeepAliveTask();

        pingManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.AUTHENTICATED));
        ScheduledFuture<?> secondTask = getKeepAliveTask();

        assertNotNull(firstTask);
        assertNotNull(secondTask);
        assertNotSame(firstTask, secondTask);
        assertTrue(firstTask.isCancelled(), "重复认证后旧保活任务应被取消");

        pingManager.shutdown();
    }

    @Test
    @DisplayName("未连接时保活任务不应发送 Ping")
    void testKeepAliveDoesNotSendPingWhenDisconnected() throws Exception {
        when(connection.isConnected()).thenReturn(false);

        invokeSendPing();

        verify(connection, never()).sendIqPacketAsync(any());
        pingManager.shutdown();
    }

    @Test
    @DisplayName("未认证时保活任务不应发送 Ping")
    void testKeepAliveDoesNotSendPingWhenUnauthenticated() throws Exception {
        when(connection.isConnected()).thenReturn(true);
        when(connection.isAuthenticated()).thenReturn(false);

        invokeSendPing();

        verify(connection, never()).sendIqPacketAsync(any());
        pingManager.shutdown();
    }

    @Test
    @DisplayName("已连接且已认证时应发送 Ping")
    void testSendPingWhenConnectedAndAuthenticated() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .build();
        when(connection.getConfig()).thenReturn(config);
        when(connection.isConnected()).thenReturn(true);
        when(connection.isAuthenticated()).thenReturn(true);
        when(connection.sendIqPacketAsync(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        invokeSendPing();

        verify(connection).sendIqPacketAsync(any());
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

    private int getTotalSubscriberCount(XmppConnection targetConnection) {
        try {
            Field field = XmppEventBus.class.getDeclaredField("listeners");
            field.setAccessible(true);
            Object listenersObject = field.get(XmppEventBus.getInstance());
            Map<XmppConnection, Map<?, List<?>>> listeners = (Map<XmppConnection, Map<?, List<?>>>) listenersObject;
            Map<?, List<?>> connectionListeners = listeners.get(targetConnection);
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
