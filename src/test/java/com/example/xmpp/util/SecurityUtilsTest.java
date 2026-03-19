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

        assertNotNull(bytes);
        assertArrayEquals(new byte[0], bytes);
    }

    @Test
    @DisplayName("toBytes 应正确处理空数组")
    void testToBytesEmpty() {
        char[] chars = new char[0];
        
        byte[] bytes = SecurityUtils.toBytes(chars);
        
        assertArrayEquals(new byte[0], bytes);
    }

    @Test
    @DisplayName("summarizeXml 应返回 auth 摘要")
    void testSummarizeXmlAuth() {
        String xml = "<auth mechanism='PLAIN'>c2VjcmV0</auth>";

        String masked = SecurityUtils.summarizeXml(xml);

        assertEquals("auth", masked);
    }

    @Test
    @DisplayName("summarizeXml 应返回结构属性摘要")
    void testSummarizeXmlPassword() {
        String xml = "<message xmlns='jabber:client' id='m1' type='chat' from='a@b' to='c@d'><body>secret123</body></message>";

        String masked = SecurityUtils.summarizeXml(xml);

        assertTrue(masked.contains("message"));
        assertTrue(masked.contains("xmlns=jabber:client"));
        assertTrue(masked.contains("id=m1"));
        assertTrue(masked.contains("type=chat"));
        assertTrue(masked.contains("from=a@b"));
        assertTrue(masked.contains("to=c@d"));
        assertFalse(masked.contains("secret123"));
    }

    @Test
    @DisplayName("summarizeXml 应正确处理 null")
    void testSummarizeXmlNull() {
        String masked = SecurityUtils.summarizeXml(null);
        
        assertNull(masked);
    }

    @Test
    @DisplayName("summarizeXml 应正确处理空字符串")
    void testSummarizeXmlEmpty() {
        String masked = SecurityUtils.summarizeXml("");

        assertEquals("", masked);
    }

    @Test
    @DisplayName("summarizeXml 应处理非法 XML")
    void testSummarizeXmlInvalid() {
        String summary = SecurityUtils.summarizeXml("<message");

        assertEquals("xml(unparseable)", summary);
    }

    @Test
    @DisplayName("escapeXmlAttribute 应正确转义")
    void testEscapeXmlAttribute() {
        String escaped = SecurityUtils.escapeXmlAttribute("\"'<>&");

        assertTrue(escaped.contains("&quot;"));
        assertTrue(escaped.contains("&apos;"));
    }
}
