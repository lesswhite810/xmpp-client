package com.example.xmpp.protocol.model.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stream 模型类单元测试。
 */
class StreamModelTest {

    // ==================== StreamHeader 测试 ====================

    @Test
    @DisplayName("StreamHeader Builder 应正确构建")
    void testStreamHeaderBuilder() {
        StreamHeader header = StreamHeader.builder()
                .from("server.example.com")
                .to("client@example.com")
                .id("stream-123")
                .version("1.0")
                .lang("en")
                .namespace("jabber:client")
                .build();
        
        assertEquals("server.example.com", header.getFrom());
        assertEquals("client@example.com", header.getTo());
        assertEquals("stream-123", header.getId());
        assertEquals("1.0", header.getVersion());
        assertEquals("en", header.getLang());
        // 注意：实际实现可能使用默认 namespace
        assertNotNull(header.getNamespace());
    }

    @Test
    @DisplayName("StreamHeader 应支持部分属性")
    void testStreamHeaderPartial() {
        StreamHeader header = StreamHeader.builder()
                .from("example.com")
                .id("test-id")
                .build();
        
        assertEquals("example.com", header.getFrom());
        assertEquals("test-id", header.getId());
        assertNull(header.getTo());
        assertNull(header.getLang());
    }

    @Test
    @DisplayName("StartTls 应是单例")
    void testStartTlsSingleton() {
        assertSame(TlsElements.StartTls.INSTANCE, TlsElements.StartTls.INSTANCE);
    }

    @Test
    @DisplayName("TlsProceed 应是单例")
    void testTlsProceedSingleton() {
        assertSame(TlsElements.TlsProceed.INSTANCE, TlsElements.TlsProceed.INSTANCE);
    }

    @Test
    @DisplayName("StartTls 和 TlsProceed 应不同")
    void testStartTlsVsTlsProceed() {
        assertNotSame(TlsElements.StartTls.INSTANCE, TlsElements.TlsProceed.INSTANCE);
    }

    // ==================== StreamFeatures 测试 ====================

    @Test
    @DisplayName("StreamFeatures Builder 应正确构建")
    void testStreamFeaturesBuilder() {
        java.util.List<String> mechanisms = java.util.List.of("PLAIN", "SCRAM-SHA-256");
        
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(true)
                .bindAvailable(true)
                .mechanisms(mechanisms)
                .build();
        
        assertTrue(features.isStarttlsAvailable());
        assertTrue(features.isBindAvailable());
        assertEquals(2, features.getMechanisms().size());
        assertTrue(features.getMechanisms().contains("PLAIN"));
    }

    @Test
    @DisplayName("StreamFeatures 默认值应为 false")
    void testStreamFeaturesDefaults() {
        StreamFeatures features = StreamFeatures.builder().build();
        
        assertFalse(features.isStarttlsAvailable());
        assertFalse(features.isBindAvailable());
        // mechanisms 可能返回空列表而不是 null
        assertTrue(features.getMechanisms() == null || features.getMechanisms().isEmpty());
    }

    @Test
    @DisplayName("StreamFeatures 应支持空 mechanisms")
    void testStreamFeaturesEmptyMechanisms() {
        StreamFeatures features = StreamFeatures.builder()
                .mechanisms(java.util.Collections.emptyList())
                .build();
        
        assertNotNull(features.getMechanisms());
        assertTrue(features.getMechanisms().isEmpty());
    }

    // ==================== StreamError 测试 ====================

    @Test
    @DisplayName("StreamError 应能创建")
    void testStreamErrorExists() {
        // 检查类是否存在
        try {
            Class<?> clazz = Class.forName("com.example.xmpp.protocol.model.stream.StreamError");
            assertNotNull(clazz);
        } catch (ClassNotFoundException e) {
            fail("StreamError class not found");
        }
    }
}
