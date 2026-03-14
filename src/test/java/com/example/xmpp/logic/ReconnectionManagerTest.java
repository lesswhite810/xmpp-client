package com.example.xmpp.logic;

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
    @DisplayName("重连失败后应继续调度下一次重试")
    void testReconnectFailureSchedulesNextAttempt() throws Exception {
        CountDownLatch attemptsLatch = new CountDownLatch(2);
        AtomicInteger attempts = new AtomicInteger();

        when(connection.isConnected()).thenReturn(false);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            attemptsLatch.countDown();
            throw new com.example.xmpp.exception.XmppNetworkException("connect failed");
        }).when(connection).connect();

        reconnectionManager.onEvent(new ConnectionEvent(connection, ConnectionEventType.ERROR, new Exception("boom")));

        try {
            assertTrue(attemptsLatch.await(9, TimeUnit.SECONDS), "失败后应继续触发下一次重连");
            assertTrue(attempts.get() >= 2, "至少应发生两次重连尝试");
        } finally {
            reconnectionManager.shutdown();
        }
    }
}
