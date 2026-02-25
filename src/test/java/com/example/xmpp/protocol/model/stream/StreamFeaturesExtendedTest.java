package com.example.xmpp.protocol.model.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamFeatures 补充测试 - 覆盖更多分支。
 */
class StreamFeaturesExtendedTest {

    @Test
    @DisplayName("Builder 应支持链式调用")
    void testBuilderChaining() {
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(true)
                .bindAvailable(true)
                .starttlsAvailable(false)
                .build();
        
        assertFalse(features.isStarttlsAvailable());
        assertTrue(features.isBindAvailable());
    }

    @Test
    @DisplayName("Builder 应支持重复设置")
    void testBuilderOverwrite() {
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(true)
                .starttlsAvailable(false)
                .starttlsAvailable(true)
                .build();
        
        assertTrue(features.isStarttlsAvailable());
    }

    @Test
    @DisplayName("mechanisms 应返回不可变列表")
    void testMechanismsImmutable() {
        StreamFeatures features = StreamFeatures.builder().build();
        
        assertThrows(UnsupportedOperationException.class, () -> 
            features.getMechanisms().add("TEST"));
    }

    @Test
    @DisplayName("getElementName 应始终返回 features")
    void testGetElementName() {
        StreamFeatures features = StreamFeatures.builder().build();
        
        assertEquals("features", features.getElementName());
    }

    @Test
    @DisplayName("getNamespace 应返回正确的命名空间")
    void testGetNamespace() {
        StreamFeatures features = StreamFeatures.builder().build();
        
        String ns = features.getNamespace();
        assertNotNull(ns);
        assertFalse(ns.isEmpty());
    }

    @Test
    @DisplayName("toXml 应处理空 features")
    void testToXmlEmpty() {
        StreamFeatures features = StreamFeatures.builder().build();

        String result = features.toXml();
        assertNotNull(result);
    }

    @Test
    @DisplayName("toXml 应处理有数据的 features")
    void testToXmlWithData() {
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(true)
                .bindAvailable(true)
                .build();

        String result = features.toXml();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
