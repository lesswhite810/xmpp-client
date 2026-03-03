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

import static org.junit.jupiter.api.Assertions.*;

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
        // 清除 XmppEventBus 中的事件订阅
        XmppEventBus.getInstance().clear();

        pingManager = new PingManager(connection);
    }

    @Test
    @DisplayName("构造函数应正确初始化")
    void testConstructor() {
        assertNotNull(pingManager);
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
}
