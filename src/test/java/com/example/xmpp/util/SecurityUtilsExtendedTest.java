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
        
        assertNull(bytes);
    }

    @Test
    @DisplayName("filterSensitiveXml 应处理 null")
    void testFilterSensitiveXmlNull() {
        String result = SecurityUtils.filterSensitiveXml(null);
        
        assertNull(result);
    }

    @Test
    @DisplayName("filterSensitiveXml 应处理空字符串")
    void testFilterSensitiveXmlEmpty() {
        String result = SecurityUtils.filterSensitiveXml("");
        
        assertEquals("", result);
    }

    @Test
    @DisplayName("filterSensitiveXml 应保留非敏感内容")
    void testFilterSensitiveXmlNonSensitive() {
        String xml = "<message><body>Hello</body></message>";
        String result = SecurityUtils.filterSensitiveXml(xml);
        
        assertTrue(result.contains("Hello"));
    }
}
