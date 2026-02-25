package com.example.xmpp.protocol.model.sasl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaslResponse 补充测试。
 */
class SaslResponseExtendedTest {

    @Test
    @DisplayName("SaslResponse 应正确创建")
    void testCreate() {
        String data = "dGVzdA=="; // Base64 encoded "test"
        SaslResponse response = new SaslResponse(data);
        
        assertNotNull(response);
        assertEquals(data, response.getContent());
    }

    @Test
    @DisplayName("SaslResponse 应处理 null 数据")
    void testNullData() {
        SaslResponse response = new SaslResponse(null);
        
        assertNotNull(response);
        assertNull(response.getContent());
    }

    @Test
    @DisplayName("SaslResponse 应处理空字符串")
    void testEmptyData() {
        SaslResponse response = new SaslResponse("");
        
        assertNotNull(response);
        assertEquals("", response.getContent());
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
        assertTrue(response.getNamespace().contains("sasl"));
    }

    @Test
    @DisplayName("toXml 应生成有效 XML")
    void testToXml() {
        SaslResponse response = new SaslResponse("dGVzdA==");

        String xml = response.toXml();
        assertNotNull(xml);
        assertFalse(xml.isEmpty());
        assertTrue(xml.contains("<response"));
        assertTrue(xml.contains("</response>"));
    }

    @Test
    @DisplayName("toXml 应处理空数据")
    void testToXmlEmpty() {
        SaslResponse response = new SaslResponse("");

        String xml = response.toXml();
        assertNotNull(xml);
        assertTrue(xml.contains("<response"));
    }

    @Test
    @DisplayName("toXml 应处理 null 数据")
    void testToXmlNull() {
        SaslResponse response = new SaslResponse(null);

        String xml = response.toXml();
        assertNotNull(xml);
        assertTrue(xml.contains("<response"));
    }

    @Test
    @DisplayName("应支持包含特殊字符的内容")
    void testSpecialCharacters() {
        SaslResponse response = new SaslResponse("aGVsbG8gd29ybGQ="); // Base64 "hello world"
        
        assertEquals("aGVsbG8gd29ybGQ=", response.getContent());
    }
}
