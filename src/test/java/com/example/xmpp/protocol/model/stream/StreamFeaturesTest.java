package com.example.xmpp.protocol.model.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamFeatures 单元测试。
 */
class StreamFeaturesTest {

    @Test
    @DisplayName("Builder 应正确构建 StreamFeatures")
    void testBuilder() {
        List<String> mechanisms = Arrays.asList("SCRAM-SHA-256", "PLAIN");
        
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(true)
                .mechanisms(mechanisms)
                .bindAvailable(true)
                .build();
        
        assertTrue(features.isStarttlsAvailable());
        assertTrue(features.isBindAvailable());
        assertEquals(2, features.getMechanisms().size());
        assertTrue(features.getMechanisms().contains("SCRAM-SHA-256"));
        assertTrue(features.getMechanisms().contains("PLAIN"));
    }

    @Test
    @DisplayName("空 mechanisms 应返回空列表")
    void testEmptyMechanisms() {
        StreamFeatures features = StreamFeatures.builder().build();
        
        assertTrue(features.getMechanisms().isEmpty());
    }

    @Test
    @DisplayName("不设置 mechanisms 应返回空列表")
    void testNullMechanisms() {
        StreamFeatures features = StreamFeatures.builder()
                .build();

        assertNotNull(features.getMechanisms());
        assertTrue(features.getMechanisms().isEmpty());
    }

    @Test
    @DisplayName("toXml 应生成正确的 XML")
    void testToXml() {
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(true)
                .mechanisms(Arrays.asList("SCRAM-SHA-256"))
                .bindAvailable(true)
                .build();

        String result = features.toXml();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getElementName 应返回 features")
    void testGetElementName() {
        StreamFeatures features = StreamFeatures.builder().build();
        
        assertEquals("features", features.getElementName());
    }

    @Test
    @DisplayName("getNamespace 应返回正确的命名空间")
    void testGetNamespace() {
        StreamFeatures features = StreamFeatures.builder().build();
        
        assertNotNull(features.getNamespace());
    }

    @Test
    @DisplayName("mechanisms 列表应不可变")
    void testMechanismsImmutable() {
        List<String> mutableList = Arrays.asList("SCRAM-SHA-256");
        StreamFeatures features = StreamFeatures.builder()
                .mechanisms(mutableList)
                .build();
        
        // 返回的列表应该不可修改
        assertThrows(UnsupportedOperationException.class, () -> 
            features.getMechanisms().add("PLAIN"));
    }

    @Test
    @DisplayName("全 false 的 StreamFeatures")
    void testAllFalse() {
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(false)
                .bindAvailable(false)
                .build();
        
        assertFalse(features.isStarttlsAvailable());
        assertFalse(features.isBindAvailable());
        assertTrue(features.getMechanisms().isEmpty());
    }

    @Test
    @DisplayName("只有 STARTTLS 可用")
    void testOnlyStartTls() {
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(true)
                .build();
        
        assertTrue(features.isStarttlsAvailable());
        assertFalse(features.isBindAvailable());
        assertTrue(features.getMechanisms().isEmpty());
    }

    @Test
    @DisplayName("多个 mechanisms")
    void testMultipleMechanisms() {
        List<String> mechanisms = Arrays.asList(
            "SCRAM-SHA-512", "SCRAM-SHA-256", "SCRAM-SHA-1", "PLAIN"
        );
        
        StreamFeatures features = StreamFeatures.builder()
                .mechanisms(mechanisms)
                .build();
        
        assertEquals(4, features.getMechanisms().size());
    }
}
