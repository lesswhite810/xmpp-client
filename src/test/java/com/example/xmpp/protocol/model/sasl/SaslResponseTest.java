package com.example.xmpp.protocol.model.sasl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaslResponse 单元测试。
 */
class SaslResponseTest {

    @Test
    @DisplayName("SaslResponse 应正确创建")
    void testCreate() {
        SaslResponse response = new SaslResponse("dXNlcj1hZG1pbg==");
        
        assertNotNull(response);
        assertEquals("dXNlcj1hZG1pbg==", response.getContent());
    }

    @Test
    @DisplayName("SaslResponse 空负载")
    void testEmptyPayload() {
        SaslResponse response = new SaslResponse("");
        
        assertNotNull(response);
        assertEquals("", response.getContent());
    }

    @Test
    @DisplayName("SaslResponse null 负载")
    void testNullPayload() {
        SaslResponse response = new SaslResponse(null);
        
        assertNotNull(response);
        assertNull(response.getContent());
    }

    @Test
    @DisplayName("getElementName 应返回 response")
    void testGetElementName() {
        SaslResponse response = new SaslResponse("test");
        
        assertEquals("response", response.getElementName());
    }

    @Test
    @DisplayName("getNamespace 应返回正确的命名空间")
    void testGetNamespace() {
        SaslResponse response = new SaslResponse("test");
        
        assertNotNull(response.getNamespace());
    }
}
