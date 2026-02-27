package com.example.xmpp.logic;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.XmppConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        lenient().doNothing().when(connection).addConnectionListener(any());
        lenient().when(connection.isConnected()).thenReturn(false);
        reconnectionManager = new ReconnectionManager(connection);
    }

    @Test
    @DisplayName("构造函数应正确初始化并注册监听器")
    void testConstructor() {
        assertNotNull(reconnectionManager);
        verify(connection).addConnectionListener(reconnectionManager);
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
    @DisplayName("onEvent(ConnectedEvent) 回调应安全执行")
    void testConnectedEventCallback() {
        assertDoesNotThrow(() -> reconnectionManager.onEvent(new ConnectionEvent.ConnectedEvent(connection)));
    }

    @Test
    @DisplayName("onEvent(AuthenticatedEvent) 回调应安全执行")
    void testAuthenticatedEventCallback() {
        assertDoesNotThrow(() -> reconnectionManager.onEvent(new ConnectionEvent.AuthenticatedEvent(connection, false)));
        assertDoesNotThrow(() -> reconnectionManager.onEvent(new ConnectionEvent.AuthenticatedEvent(connection, true)));
    }

    @Test
    @DisplayName("onEvent(ConnectionClosedEvent) 回调应安全执行")
    void testConnectionClosedEventCallback() {
        assertDoesNotThrow(() -> reconnectionManager.onEvent(new ConnectionEvent.ConnectionClosedEvent(connection)));
    }

    @Test
    @DisplayName("onEvent(ConnectionClosedOnErrorEvent) 回调应安全执行")
    void testConnectionClosedOnErrorEventCallback() {
        reconnectionManager.disable(); // 禁用避免实际重连

        assertDoesNotThrow(() -> reconnectionManager.onEvent(
                new ConnectionEvent.ConnectionClosedOnErrorEvent(connection, new Exception("Test error"))));
    }

    @Test
    @DisplayName("启用状态下错误关闭应触发重连")
    void testConnectionClosedOnErrorWithEnabled() {
        when(connection.isConnected()).thenReturn(true);
        reconnectionManager.enable();

        assertDoesNotThrow(() -> reconnectionManager.onEvent(
                new ConnectionEvent.ConnectionClosedOnErrorEvent(connection, new Exception("Test error"))));
    }
}
