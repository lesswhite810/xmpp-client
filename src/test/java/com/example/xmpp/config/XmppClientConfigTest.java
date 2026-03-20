package com.example.xmpp.config;

import com.example.xmpp.util.XmppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
        assertEquals(XmppConstants.DEFAULT_PING_INTERVAL_SECONDS, config.getPingInterval());
        assertNotNull(config.getPassword());
        assertEquals(0, config.getPassword().length);
    }

    @Test
    @DisplayName("getXmlLang 应返回有效语言标签")
    void testXmlLangFromLocale() {
        XmppClientConfig config = XmppClientConfig.builder()
                .language(Locale.ENGLISH)
                .build();

        assertEquals("en", config.getXmlLang());
    }

    @Test
    @DisplayName("getXmlLang 在未定义语言或显式 null 时应返回 null")
    void testXmlLangReturnsNullForUndefinedOrNullLanguage() {
        XmppClientConfig undefinedLanguage = XmppClientConfig.builder()
                .language(Locale.forLanguageTag("und"))
                .build();
        XmppClientConfig nullLanguage = XmppClientConfig.builder()
                .language(null)
                .build();

        assertNull(undefinedLanguage.getXmlLang());
        assertNull(nullLanguage.getXmlLang());
    }

    @Test
    @DisplayName("Builder 应正确构建配置")
    void testBuilder() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("xmpp.example.com")
                .port(XmppConstants.DIRECT_TLS_PORT)
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
    @DisplayName("getPassword 应返回防御性副本")
    void testPasswordGetterReturnsDefensiveCopy() {
        XmppClientConfig config = XmppClientConfig.builder()
                .password("pass".toCharArray())
                .build();

        char[] password = config.getPassword();
        password[0] = 'x';

        assertArrayEquals("pass".toCharArray(), config.getPassword());
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
    @DisplayName("未显式配置时应返回基于秒常量换算的毫秒默认值")
    void testDefaultTimeouts() {
        XmppClientConfig config = XmppClientConfig.builder()
                .build();

        assertEquals(TimeUnit.SECONDS.toMillis(XmppConstants.DEFAULT_CONNECT_TIMEOUT_SECONDS), config.getConnectTimeout());
        assertEquals(TimeUnit.SECONDS.toMillis(XmppConstants.DEFAULT_READ_TIMEOUT_SECONDS), config.getReadTimeout());
        assertEquals(TimeUnit.SECONDS.toMillis(XmppConstants.SSL_HANDSHAKE_TIMEOUT_SECONDS), config.getHandshakeTimeoutMs());
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
    @DisplayName("应正确设置不发送 Presence")
    void testSendPresence() {
        XmppClientConfig config = XmppClientConfig.builder()
                .sendPresence(false)
                .build();

        assertFalse(config.isSendPresence());
    }

    private static <T> boolean contains(T[] array, T value) {
        for (T t : array) {
            if (t == value) {
                return true;
            }
        }
        return false;
    }
}
