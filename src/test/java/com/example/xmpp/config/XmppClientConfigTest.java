package com.example.xmpp.config;

import com.example.xmpp.util.XmppConstants;
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
        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder().build())
                .security(SecurityConfig.builder().build())
                .keepAlive(KeepAliveConfig.builder().build())
                .build();

        // 默认值
        assertEquals(XmppConstants.DEFAULT_XMPP_PORT, config.getPort());
        assertEquals(SecurityConfig.SecurityMode.REQUIRED, config.getSecurity().getSecurityMode());
        assertTrue(config.isSendPresence());
        assertFalse(config.getKeepAlive().isReconnectionEnabled());
        assertFalse(config.getKeepAlive().isPingEnabled());
    }

    @Test
    @DisplayName("Builder 应正确构建配置")
    void testBuilder() {
        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain("example.com")
                        .host("xmpp.example.com")
                        .port(5223)
                        .resource("mobile")
                        .build())
                .auth(AuthConfig.builder()
                        .username("user")
                        .password("pass".toCharArray())
                        .build())
                .build();

        assertEquals("example.com", config.getConnection().getXmppServiceDomain());
        assertEquals("xmpp.example.com", config.getConnection().getHost());
        assertEquals(5223, config.getPort());
        assertEquals("user", config.getAuth().getUsername());
        assertArrayEquals("pass".toCharArray(), config.getPassword());
        assertEquals("mobile", config.getConnection().getResource());
    }

    @Test
    @DisplayName("SecurityMode 枚举应包含所有值")
    void testSecurityModeValues() {
        SecurityConfig.SecurityMode[] modes = SecurityConfig.SecurityMode.values();

        assertEquals(3, modes.length);
        assertTrue(contains(modes, SecurityConfig.SecurityMode.REQUIRED));
        assertTrue(contains(modes, SecurityConfig.SecurityMode.IF_POSSIBLE));
        assertTrue(contains(modes, SecurityConfig.SecurityMode.DISABLED));
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
                .security(SecurityConfig.builder()
                        .securityMode(SecurityConfig.SecurityMode.DISABLED)
                        .build())
                .build();

        assertEquals(SecurityConfig.SecurityMode.DISABLED, config.getSecurity().getSecurityMode());
    }

    @Test
    @DisplayName("应正确设置超时")
    void testTimeouts() {
        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .connectTimeout(5000)
                        .readTimeout(30000)
                        .build())
                .build();

        assertEquals(5000, config.getConnectTimeout());
        assertEquals(30000, config.getReadTimeout());
    }

    @Test
    @DisplayName("应正确设置重连参数")
    void testReconnectionSettings() {
        XmppClientConfig config = XmppClientConfig.builder()
                .keepAlive(KeepAliveConfig.builder()
                        .reconnectionEnabled(false)
                        .reconnectionBaseDelay(5)
                        .reconnectionMaxDelay(300)
                        .build())
                .build();

        assertFalse(config.getKeepAlive().isReconnectionEnabled());
        assertEquals(5, config.getKeepAlive().getReconnectionBaseDelay());
        assertEquals(300, config.getKeepAlive().getReconnectionMaxDelay());
    }

    @Test
    @DisplayName("应正确设置 DirectTLS")
    void testDirectTLS() {
        XmppClientConfig config = XmppClientConfig.builder()
                .security(SecurityConfig.builder()
                        .usingDirectTLS(true)
                        .build())
                .build();

        assertTrue(config.getSecurity().isUsingDirectTLS());
    }

    @Test
    @DisplayName("应正确设置不发送 Presence")
    void testSendPresence() {
        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .sendPresence(false)
                        .build())
                .build();

        assertFalse(config.isSendPresence());
    }
}
