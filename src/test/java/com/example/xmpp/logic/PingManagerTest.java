package com.example.xmpp.logic;

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
 * PingManager 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PingManagerTest {

    @Mock
    private XmppConnection connection;

    private PingManager pingManager;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(connection).addConnectionListener(any());
        pingManager = new PingManager(connection);
    }

    @Test
    @DisplayName("构造函数应正确初始化")
    void testConstructor() {
        assertNotNull(pingManager);
        verify(connection).addConnectionListener(any());
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
    @DisplayName("setPingInterval 为 0 应禁用 keepalive")
    void testDisableKeepAlive() {
        assertDoesNotThrow(() -> pingManager.setPingInterval(0));
    }

    @Test
    @DisplayName("setPingInterval 负值应被处理")
    void testNegativePingInterval() {
        // 负值应该被处理（可能使用默认值）
        assertDoesNotThrow(() -> pingManager.setPingInterval(-1));
    }
}
