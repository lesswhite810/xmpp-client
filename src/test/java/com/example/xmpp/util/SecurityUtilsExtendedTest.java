package com.example.xmpp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityUtils 补充测试。
 */
class SecurityUtilsExtendedTest {

    @Test
    @DisplayName("toBytes 应处理空数组")
    void testToBytesEmpty() {
        char[] chars = new char[0];
        byte[] bytes = SecurityUtils.toBytes(chars);
        
        assertNotNull(bytes);
        assertEquals(0, bytes.length);
    }

    @Test
    @DisplayName("toBytes 应处理 null")
    void testToBytesNull() {
        byte[] bytes = SecurityUtils.toBytes(null);

        assertNotNull(bytes);
        assertEquals(0, bytes.length);
    }

    @Test
    @DisplayName("summarizeXml 应处理 null")
    void testSummarizeXmlNull() {
        String result = SecurityUtils.summarizeXml(null);
        
        assertNull(result);
    }

    @Test
    @DisplayName("summarizeXml 应处理空字符串")
    void testSummarizeXmlEmpty() {
        String result = SecurityUtils.summarizeXml("");
        
        assertEquals("", result);
    }

    @Test
    @DisplayName("summarizeXml 不应保留元素正文")
    void testSummarizeXmlNonSensitive() {
        String xml = "<message><body>Hello</body></message>";
        String result = SecurityUtils.summarizeXml(xml);

        assertEquals("message", result);
        assertFalse(result.contains("Hello"));
    }
}
