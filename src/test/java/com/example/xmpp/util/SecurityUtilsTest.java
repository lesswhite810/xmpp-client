package com.example.xmpp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityUtils 单元测试。
 */
class SecurityUtilsTest {

    @Test
    @DisplayName("clear 应正确清理字符数组")
    void testClearCharArray() {
        char[] chars = "password".toCharArray();
        
        SecurityUtils.clear(chars);
        
        for (char c : chars) {
            assertEquals('\0', c);
        }
    }

    @Test
    @DisplayName("clear 应正确处理 null 字符数组")
    void testClearNullCharArray() {
        // 不应抛出异常
        assertDoesNotThrow(() -> SecurityUtils.clear((char[]) null));
    }

    @Test
    @DisplayName("clear 应正确清理空字符数组")
    void testClearEmptyCharArray() {
        char[] chars = new char[0];
        
        assertDoesNotThrow(() -> SecurityUtils.clear(chars));
    }

    @Test
    @DisplayName("clear 应正确清理字节数组")
    void testClearByteArray() {
        byte[] bytes = "secret".getBytes(StandardCharsets.UTF_8);
        
        SecurityUtils.clear(bytes);
        
        for (byte b : bytes) {
            assertEquals(0, b);
        }
    }

    @Test
    @DisplayName("clear 应正确处理 null 字节数组")
    void testClearNullByteArray() {
        assertDoesNotThrow(() -> SecurityUtils.clear((byte[]) null));
    }

    @Test
    @DisplayName("clear 应正确处理空字节数组")
    void testClearEmptyByteArray() {
        byte[] bytes = new byte[0];
        
        assertDoesNotThrow(() -> SecurityUtils.clear(bytes));
    }

    @Test
    @DisplayName("toBytes 应正确转换 ASCII 字符")
    void testToBytesAscii() {
        char[] chars = "hello".toCharArray();
        
        byte[] bytes = SecurityUtils.toBytes(chars);
        
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    @DisplayName("toBytes 应正确转换 UTF-8 多字节字符")
    void testToBytesUtf8() {
        char[] chars = "你好".toCharArray();
        
        byte[] bytes = SecurityUtils.toBytes(chars);
        
        assertArrayEquals("你好".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    @DisplayName("toBytes 应正确处理 null")
    void testToBytesNull() {
        byte[] bytes = SecurityUtils.toBytes(null);
        
        assertNull(bytes);
    }

    @Test
    @DisplayName("toBytes 应正确处理空数组")
    void testToBytesEmpty() {
        char[] chars = new char[0];
        
        byte[] bytes = SecurityUtils.toBytes(chars);
        
        assertArrayEquals(new byte[0], bytes);
    }

    @Test
    @DisplayName("isEmpty 应正确判断空字符串")
    void testIsEmpty() {
        assertTrue(SecurityUtils.isEmpty(""));
        assertTrue(SecurityUtils.isEmpty(null));
        assertFalse(SecurityUtils.isEmpty("test"));
    }

    @Test
    @DisplayName("filterSensitiveXml 应遮罩 auth 元素")
    void testFilterSensitiveXmlAuth() {
        String xml = "<auth mechanism='PLAIN'>c2VjcmV0</auth>";
        
        String masked = SecurityUtils.filterSensitiveXml(xml);
        
        assertFalse(masked.contains("c2VjcmV0"));
        assertTrue(masked.contains("*****"));
    }

    @Test
    @DisplayName("filterSensitiveXml 应遮罩 password 元素")
    void testFilterSensitiveXmlPassword() {
        String xml = "<password>secret123</password>";
        
        String masked = SecurityUtils.filterSensitiveXml(xml);
        
        assertFalse(masked.contains("secret123"));
        assertTrue(masked.contains("*****"));
    }

    @Test
    @DisplayName("filterSensitiveXml 应正确处理 null")
    void testFilterSensitiveXmlNull() {
        String masked = SecurityUtils.filterSensitiveXml(null);
        
        assertNull(masked);
    }

    @Test
    @DisplayName("filterSensitiveXml 应正确处理空字符串")
    void testFilterSensitiveXmlEmpty() {
        String masked = SecurityUtils.filterSensitiveXml("");
        
        assertEquals("", masked);
    }

    @Test
    @DisplayName("escapeXml 应正确转义特殊字符")
    void testEscapeXml() {
        String escaped = SecurityUtils.escapeXml("<>&");
        
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
        assertTrue(escaped.contains("&amp;"));
    }

    @Test
    @DisplayName("escapeXml 应正确处理 null")
    void testEscapeXmlNull() {
        String escaped = SecurityUtils.escapeXml(null);
        
        assertNull(escaped);
    }

    @Test
    @DisplayName("escapeXmlAttribute 应正确转义")
    void testEscapeXmlAttribute() {
        String escaped = SecurityUtils.escapeXmlAttribute("\"'<>&");
        
        assertTrue(escaped.contains("&quot;"));
        assertTrue(escaped.contains("&apos;"));
    }
}
