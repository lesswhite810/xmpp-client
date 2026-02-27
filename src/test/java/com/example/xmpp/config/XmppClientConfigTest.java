package com.example.xmpp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmppClientConfig 单元测试。
 */
class XmppClientConfigTest {

    @Test
    @DisplayName("应正确创建默认配置")
    void testDefaultConfig() {
        XmppClientConfig config = XmppClientConfig.builder().build();

        // 默认值
        assertEquals(5222, config.getPort());
        assertEquals(XmppClientConfig.SecurityMode.REQUIRED, config.getSecurityMode());
        assertTrue(config.isSendPresence());
        assertTrue(config.isReconnectionEnabled());
    }

    @Test
    @DisplayName("Builder 应正确构建配置")
    void testBuilder() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("xmpp.example.com")
                .port(5223)
                .username("user")
                .password("pass".toCharArray())
                .resource("mobile")
                .build();
        
        assertEquals("example.com", config.getXmppServiceDomain());
        assertEquals("xmpp.example.com", config.getHost());
        assertEquals(5223, config.getPort());
        assertEquals("user", config.getUsername());
        assertArrayEquals("pass".toCharArray(), config.getPassword());
        assertEquals("mobile", config.getResource());
    }

    @Test
    @DisplayName("SecurityMode 枚举应包含所有值")
    void testSecurityModeValues() {
        XmppClientConfig.SecurityMode[] modes = XmppClientConfig.SecurityMode.values();
        
        assertEquals(3, modes.length);
        assertTrue(contains(modes, XmppClientConfig.SecurityMode.REQUIRED));
        assertTrue(contains(modes, XmppClientConfig.SecurityMode.IF_POSSIBLE));
        assertTrue(contains(modes, XmppClientConfig.SecurityMode.DISABLED));
    }

    private <T> boolean contains(T[] array, T value) {
        for (T t : array) {
            if (t == value) return true;
        }
        return false;
    }

    @Test
    @DisplayName("应正确设置安全模式")
    void testSecurityMode() {
        XmppClientConfig config = XmppClientConfig.builder()
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();

        assertEquals(XmppClientConfig.SecurityMode.DISABLED, config.getSecurityMode());
    }

    @Test
    @DisplayName("应正确设置超时")
    void testTimeouts() {
        XmppClientConfig config = XmppClientConfig.builder()
                .connectTimeout(5000)
                .readTimeout(30000)
                .build();
        
        assertEquals(5000, config.getConnectTimeout());
        assertEquals(30000, config.getReadTimeout());
    }

    @Test
    @DisplayName("应正确设置重连参数")
    void testReconnectionSettings() {
        XmppClientConfig config = XmppClientConfig.builder()
                .reconnectionEnabled(false)
                .reconnectionBaseDelay(5)
                .reconnectionMaxDelay(300)
                .build();
        
        assertFalse(config.isReconnectionEnabled());
        assertEquals(5, config.getReconnectionBaseDelay());
        assertEquals(300, config.getReconnectionMaxDelay());
    }

    @Test
    @DisplayName("应正确设置 DirectTLS")
    void testDirectTLS() {
        XmppClientConfig config = XmppClientConfig.builder()
                .usingDirectTLS(true)
                .build();
        
        assertTrue(config.isUsingDirectTLS());
    }

    @Test
    @DisplayName("应正确设置不发送 Presence")
    void testSendPresence() {
        XmppClientConfig config = XmppClientConfig.builder()
                .sendPresence(false)
                .build();
        
        assertFalse(config.isSendPresence());
    }
}
