package com.example.xmpp.protocol.model.extension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bind 单元测试补充。
 */
class BindExtendedTest {

    @Test
    @DisplayName("Bind 应正确创建带 jid")
    void testBindWithJid() {
        Bind bind = Bind.builder()
                .jid("user@example.com/resource")
                .build();
        
        assertEquals("user@example.com/resource", bind.getJid());
        assertNull(bind.getResource());
    }

    @Test
    @DisplayName("Bind 应正确创建带 resource")
    void testBindWithResource() {
        Bind bind = Bind.builder()
                .resource("mobile")
                .build();
        
        assertEquals("mobile", bind.getResource());
        assertNull(bind.getJid());
    }

    @Test
    @DisplayName("Bind getElementName 应返回 bind")
    void testGetElementName() {
        Bind bind = Bind.builder().build();
        
        assertEquals("bind", Bind.ELEMENT);
    }

    @Test
    @DisplayName("Bind getNamespace 应返回正确的命名空间")
    void testGetNamespace() {
        Bind bind = Bind.builder().build();
        
        assertNotNull(Bind.NAMESPACE);
    }

    @Test
    @DisplayName("Bind toXml 应不抛出异常")
    void testToXml() {
        Bind bind = Bind.builder()
                .jid("user@example.com")
                .build();

        assertDoesNotThrow(() -> bind.toXml());
    }
}
