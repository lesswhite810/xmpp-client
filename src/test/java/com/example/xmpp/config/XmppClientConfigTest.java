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
                .build();

        assertEquals(XmppConstants.DEFAULT_XMPP_PORT, config.getPort());
        assertEquals(XmppClientConfig.SecurityMode.REQUIRED, config.getSecurityMode());
        assertTrue(config.isSendPresence());
        assertFalse(config.isReconnectionEnabled());
        assertFalse(config.isPingEnabled());
    }

    @Test
    @DisplayName("Builder 应正确构建配置")
    void testBuilder() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("xmpp.example.com")
                .port(5223)
                .resource("mobile")
                .username("user")
                .password("pass".toCharArray())
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
    void testDirectTls() {
        XmppClientConfig config = XmppClientConfig.builder()
                .usingDirectTLS(true)
                .build();

        assertTrue(config.isUsingDirectTLS());
        assertEquals(XmppConstants.DIRECT_TLS_PORT, config.getPort());
    }

    @Test
    @DisplayName("TLS 认证模式默认应为单向认证")
    void testDefaultTlsAuthenticationMode() {
        XmppClientConfig config = XmppClientConfig.builder().build();

        assertEquals(XmppClientConfig.TlsAuthenticationMode.ONE_WAY, config.getTlsAuthenticationMode());
    }

    @Test
    @DisplayName("应从 SystemService 读取 TLS 认证模式")
    void testFromSystemServiceTlsAuthenticationMode() {
        SystemService systemService = key -> switch (key) {
            case XmppConfigKeys.XMPP_SERVICE_DOMAIN -> "example.com";
            case XmppConfigKeys.USERNAME -> "admin";
            case XmppConfigKeys.PASSWORD -> "secret";
            case XmppConfigKeys.TLS_AUTHENTICATION_MODE -> "mutual";
            default -> null;
        };

        XmppClientConfig config = XmppClientConfig.fromSystemService(systemService);

        assertEquals(XmppClientConfig.TlsAuthenticationMode.MUTUAL, config.getTlsAuthenticationMode());
    }

    @Test
    @DisplayName("应正确设置不发送 Presence")
    void testSendPresence() {
        XmppClientConfig config = XmppClientConfig.builder()
                .sendPresence(false)
                .build();

        assertFalse(config.isSendPresence());
    }

    @Test
    @DisplayName("应从 SystemService 装配配置")
    void testFromSystemService() {
        SystemService systemService = key -> switch (key) {
            case XmppConfigKeys.XMPP_SERVICE_DOMAIN -> "example.com";
            case XmppConfigKeys.USERNAME -> "admin";
            case XmppConfigKeys.PASSWORD -> "secret";
            case XmppConfigKeys.RESOURCE -> "console";
            case XmppConfigKeys.SECURITY_MODE -> "disabled";
            case XmppConfigKeys.PORT -> "5223";
            case XmppConfigKeys.CONNECT_TIMEOUT -> "8000";
            case XmppConfigKeys.READ_TIMEOUT -> "12000";
            case XmppConfigKeys.RECONNECTION_ENABLED -> "true";
            case XmppConfigKeys.RECONNECTION_BASE_DELAY -> "7";
            case XmppConfigKeys.RECONNECTION_MAX_DELAY -> "77";
            case XmppConfigKeys.PING_ENABLED -> "true";
            case XmppConfigKeys.PING_INTERVAL -> "25";
            case XmppConfigKeys.SEND_PRESENCE -> "false";
            case XmppConfigKeys.DIRECT_TLS -> "true";
            case XmppConfigKeys.ENABLED_SASL_MECHANISMS -> "SCRAM-SHA-256,PLAIN";
            case XmppConfigKeys.TLS_AUTHENTICATION_MODE -> "mutual";
            default -> null;
        };

        XmppClientConfig config = XmppClientConfig.fromSystemService(systemService, "192.168.1.10");

        assertEquals("example.com", config.getXmppServiceDomain());
        assertEquals("192.168.1.10", config.getHost());
        assertEquals("192.168.1.10", config.getHostAddress().getHostAddress());
        assertEquals(5223, config.getPort());
        assertEquals("admin", config.getUsername());
        assertArrayEquals("secret".toCharArray(), config.getPassword());
        assertEquals("console", config.getResource());
        assertEquals(XmppClientConfig.SecurityMode.DISABLED, config.getSecurityMode());
        assertEquals(8000, config.getConnectTimeout());
        assertEquals(12000, config.getReadTimeout());
        assertTrue(config.isReconnectionEnabled());
        assertEquals(7, config.getReconnectionBaseDelay());
        assertEquals(77, config.getReconnectionMaxDelay());
        assertTrue(config.isPingEnabled());
        assertEquals(25, config.getPingInterval());
        assertFalse(config.isSendPresence());
        assertTrue(config.isUsingDirectTLS());
        assertEquals(XmppClientConfig.TlsAuthenticationMode.MUTUAL, config.getTlsAuthenticationMode());
        assertEquals(2, config.getEnabledSaslMechanisms().size());
    }

    @Test
    @DisplayName("缺少必填配置时应失败")
    void testFromSystemServiceMissingRequiredValue() {
        SystemService systemService = key -> null;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> XmppClientConfig.fromSystemService(systemService)
        );

        assertTrue(exception.getMessage().contains(XmppConfigKeys.XMPP_SERVICE_DOMAIN));
    }

    private <T> boolean contains(T[] array, T value) {
        for (T t : array) {
            if (t == value) {
                return true;
            }
        }
        return false;
    }
}
