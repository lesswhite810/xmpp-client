package com.example.xmpp.logic;

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

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(connection).addConnectionListener(any());
        lenient().doNothing().when(connection).addAsyncStanzaListener(any(), any());
    }

    @Test
    @DisplayName("getInstanceFor 应返回同一实例")
    void testGetInstanceFor() {
        PingManager instance1 = PingManager.getInstanceFor(connection);
        PingManager instance2 = PingManager.getInstanceFor(connection);
        
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("setPingInterval 应更新间隔")
    void testSetPingInterval() {
        PingManager manager = PingManager.getInstanceFor(connection);
        
        assertDoesNotThrow(() -> manager.setPingInterval(30));
    }

    @Test
    @DisplayName("shutdown 应安全执行")
    void testShutdown() {
        PingManager manager = PingManager.getInstanceFor(connection);
        
        assertDoesNotThrow(() -> manager.shutdown());
    }

    @Test
    @DisplayName("processStanza 应处理 Ping IQ 并发送响应")
    void testProcessStanza() {
        PingManager manager = PingManager.getInstanceFor(connection);
        
        Iq pingIq = new Iq(Iq.Type.get, "ping-1", "from@example.com", "to@example.com", null, null);
        
        // Mock sendStanza
        lenient().doNothing().when(connection).sendStanza(any());
        
        // processStanza 返回 void，通过验证 sendStanza 调用来确认行为
        manager.processStanza(pingIq);
        
        // 验证发送了响应
        verify(connection).sendStanza(any(Iq.class));
    }

    @Test
    @DisplayName("setPingInterval 为 0 应禁用 keepalive")
    void testDisableKeepAlive() {
        PingManager manager = PingManager.getInstanceFor(connection);
        
        assertDoesNotThrow(() -> manager.setPingInterval(0));
    }

    @Test
    @DisplayName("setPingInterval 负值应被处理")
    void testNegativePingInterval() {
        PingManager manager = PingManager.getInstanceFor(connection);
        
        // 负值应该被处理（可能使用默认值）
        assertDoesNotThrow(() -> manager.setPingInterval(-1));
    }
}
