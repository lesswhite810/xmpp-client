package com.example.xmpp.logic;

import com.example.xmpp.ConnectionEvent;
import com.example.xmpp.ReconnectionManager;
import com.example.xmpp.XmppConnection;
import com.example.xmpp.config.XmppClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ReconnectionManager 单元测试。
 */
class ReconnectionManagerTest {

    private XmppConnection mockConnection;

    @BeforeEach
    void setUp() {
        mockConnection = mock(XmppConnection.class);
        when(mockConnection.isConnected()).thenReturn(false);
    }

    @Test
    @DisplayName("应通过 getInstanceFor 获取 ReconnectionManager 实例")
    void testGetInstanceFor() {
        ReconnectionManager manager = ReconnectionManager.getInstanceFor(mockConnection);

        assertNotNull(manager);
        verify(mockConnection).addConnectionListener(manager);
    }

    @Test
    @DisplayName("相同连接应返回相同实例")
    void testSameInstanceForSameConnection() {
        ReconnectionManager manager1 = ReconnectionManager.getInstanceFor(mockConnection);
        ReconnectionManager manager2 = ReconnectionManager.getInstanceFor(mockConnection);

        assertSame(manager1, manager2);
    }

    @Test
    @DisplayName("应支持启用自动重连")
    void testEnable() {
        ReconnectionManager manager = ReconnectionManager.getInstanceFor(mockConnection);

        assertDoesNotThrow(() -> manager.enable());
    }

    @Test
    @DisplayName("应支持禁用自动重连")
    void testDisable() {
        ReconnectionManager manager = ReconnectionManager.getInstanceFor(mockConnection);

        assertDoesNotThrow(() -> manager.disable());
    }

    @Test
    @DisplayName("多次启用/禁用应安全执行")
    void testMultipleEnableDisable() {
        ReconnectionManager manager = ReconnectionManager.getInstanceFor(mockConnection);

        assertDoesNotThrow(() -> {
            manager.enable();
            manager.enable();
            manager.disable();
            manager.disable();
            manager.enable();
        });
    }

    @Test
    @DisplayName("onEvent(ConnectedEvent) 回调应安全执行")
    void testConnectedEventCallback() {
        ReconnectionManager manager = ReconnectionManager.getInstanceFor(mockConnection);

        assertDoesNotThrow(() -> manager.onEvent(new ConnectionEvent.ConnectedEvent(mockConnection)));
    }

    @Test
    @DisplayName("onEvent(AuthenticatedEvent) 回调应安全执行")
    void testAuthenticatedEventCallback() {
        ReconnectionManager manager = ReconnectionManager.getInstanceFor(mockConnection);

        assertDoesNotThrow(() -> manager.onEvent(new ConnectionEvent.AuthenticatedEvent(mockConnection, false)));
        assertDoesNotThrow(() -> manager.onEvent(new ConnectionEvent.AuthenticatedEvent(mockConnection, true)));
    }

    @Test
    @DisplayName("onEvent(ConnectionClosedEvent) 回调应安全执行")
    void testConnectionClosedEventCallback() {
        ReconnectionManager manager = ReconnectionManager.getInstanceFor(mockConnection);

        assertDoesNotThrow(() -> manager.onEvent(new ConnectionEvent.ConnectionClosedEvent(mockConnection)));
    }

    @Test
    @DisplayName("onEvent(ConnectionClosedOnErrorEvent) 回调应安全执行")
    void testConnectionClosedOnErrorEventCallback() {
        ReconnectionManager manager = ReconnectionManager.getInstanceFor(mockConnection);
        manager.disable(); // 禁用避免实际重连

        assertDoesNotThrow(() -> manager.onEvent(
                new ConnectionEvent.ConnectionClosedOnErrorEvent(mockConnection, new Exception("Test error"))));
    }
}
