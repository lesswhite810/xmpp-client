package com.example.xmpp.protocol.model.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamHeader 单元测试。
 */
class StreamHeaderTest {

    @Test
    @DisplayName("Builder 应正确构建 StreamHeader")
    void testBuilder() {
        StreamHeader header = StreamHeader.builder()
                .from("server.example.com")
                .to("client@example.com")
                .id("stream-123")
                .version("1.0")
                .lang("en")
                .build();
        
        assertEquals("server.example.com", header.getFrom());
        assertEquals("client@example.com", header.getTo());
        assertEquals("stream-123", header.getId());
        assertEquals("1.0", header.getVersion());
        assertEquals("en", header.getLang());
    }

    @Test
    @DisplayName("空 Builder 应创建默认 StreamHeader")
    void testEmptyBuilder() {
        StreamHeader header = StreamHeader.builder().build();
        
        // 验证不会抛出异常
        assertNotNull(header);
    }

    @Test
    @DisplayName("toXml 应生成正确的 XML")
    void testToXml() {
        StreamHeader header = StreamHeader.builder()
                .from("example.com")
                .id("test-123")
                .version("1.0")
                .build();
        
        // 验证不会抛出异常
        header.toXml();
    }

    @Test
    @DisplayName("getElementName 应返回 stream")
    void testGetElementName() {
        StreamHeader header = StreamHeader.builder().build();
        
        assertEquals("stream", header.getElementName());
    }

    @Test
    @DisplayName("null 值应被正确处理")
    void testNullValues() {
        StreamHeader header = StreamHeader.builder()
                .from(null)
                .to(null)
                .id(null)
                .version(null)
                .lang(null)
                .build();
        
        assertNotNull(header);
    }

    @Test
    @DisplayName("带 namespace 的 StreamHeader")
    void testWithNamespace() {
        StreamHeader header = StreamHeader.builder()
                .namespace("jabber:client")
                .build();
        
        assertNotNull(header.getNamespace());
    }
}
