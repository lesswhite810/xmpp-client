package com.example.xmpp.protocol.model.extension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extension 模型类单元测试。
 */
class ExtensionTest {

    @Test
    @DisplayName("Ping 应是单例")
    void testPingSingleton() {
        assertSame(Ping.INSTANCE, Ping.INSTANCE);
    }

    @Test
    @DisplayName("Ping 应有正确的元素名和命名空间")
    void testPingProperties() {
        assertEquals("ping", Ping.ELEMENT);
        assertEquals("urn:xmpp:ping", Ping.NAMESPACE);
    }

    @Test
    @DisplayName("Bind 应正确创建")
    void testBindCreate() {
        Bind bind = Bind.builder()
                .jid("user@example.com/resource")
                .build();
        
        assertEquals("user@example.com/resource", bind.getJid());
    }

    @Test
    @DisplayName("Bind 应支持 resource")
    void testBindWithResource() {
        Bind bind = Bind.builder()
                .resource("mobile")
                .build();
        
        assertEquals("mobile", bind.getResource());
    }

    @Test
    @DisplayName("Bind 应支持空值")
    void testBindEmpty() {
        Bind bind = Bind.builder().build();
        
        assertNull(bind.getJid());
        assertNull(bind.getResource());
    }

    @Test
    @DisplayName("Bind 应有正确的元素名和命名空间")
    void testBindProperties() {
        assertEquals("bind", Bind.ELEMENT);
        assertEquals("urn:ietf:params:xml:ns:xmpp-bind", Bind.NAMESPACE);
    }

    @Test
    @DisplayName("ConnectionRequest 应正确创建")
    void testConnectionRequestCreate() {
        ConnectionRequest request = ConnectionRequest.builder()
                .username("device-123")
                .password("secret")
                .build();
        
        assertEquals("device-123", request.getUsername());
        assertEquals("secret", request.getPassword());
    }

    @Test
    @DisplayName("ConnectionRequest 应有正确的元素名")
    void testConnectionRequestProperties() {
        assertEquals("connectionRequest", ConnectionRequest.ELEMENT);
        assertNotNull(ConnectionRequest.NAMESPACE);
    }
}
