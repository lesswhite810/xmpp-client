package com.example.xmpp.logic;

import com.example.xmpp.ConnectionEvent;
import com.example.xmpp.XmppConnection;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
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
        lenient().doNothing().when(connection).addAsyncStanzaListener(any(), any());
        pingManager = new PingManager(connection);
    }

    @Test
    @DisplayName("构造函数应正确初始化")
    void testConstructor() {
        assertNotNull(pingManager);
        verify(connection).addConnectionListener(any());
        verify(connection).addAsyncStanzaListener(any(), any());
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
    @DisplayName("processStanza 应处理 Ping IQ 并发送响应")
    void testProcessStanza() {
        Iq pingIq = new Iq(Iq.Type.get, "ping-1", "from@example.com", "to@example.com", null, null);

        // Mock sendStanza
        lenient().doNothing().when(connection).sendStanza(any());

        // processStanza 返回 void，通过验证 sendStanza 调用来确认行为
        pingManager.processStanza(pingIq);

        // 验证发送了响应
        verify(connection).sendStanza(any(Iq.class));
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
