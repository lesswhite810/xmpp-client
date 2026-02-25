package com.example.xmpp.net;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppNetworkException;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SslUtils 单元测试。
 *
 * @since 2026-02-24
 */
class SslUtilsTest {

    @Test
    @DisplayName("应正确创建默认 SslHandler")
    void testCreateDefaultSslHandler() throws XmppNetworkException {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .port(5222)
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);
        assertNotNull(handler);
        assertNotNull(handler.engine());
        assertTrue(handler.engine().getUseClientMode());
    }

    @Test
    @DisplayName("应正确处理自定义 TrustManager")
    void testCustomTrustManager() throws XmppNetworkException {
        TrustManager trustAllManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };

        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .port(5222)
                .customTrustManager(new TrustManager[]{trustAllManager})
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);
        assertNotNull(handler);
    }

    @Test
    @DisplayName("应正确配置主机名验证")
    void testHostnameVerification() throws XmppNetworkException {
        // 启用主机名验证
        XmppClientConfig configEnabled = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .port(5222)
                .enableHostnameVerification(true)
                .build();

        SslHandler handlerEnabled = SslUtils.createSslHandler(configEnabled);
        assertNotNull(handlerEnabled);

        // 禁用主机名验证
        XmppClientConfig configDisabled = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .port(5222)
                .enableHostnameVerification(false)
                .build();

        SslHandler handlerDisabled = SslUtils.createSslHandler(configDisabled);
        assertNotNull(handlerDisabled);
    }

    @Test
    @DisplayName("Direct TLS 模式应正确配置")
    void testDirectTLSConfig() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .port(5223)
                .usingDirectTLS(true)
                .build();

        assertTrue(config.isUsingDirectTLS());
        assertEquals(5223, config.getPort());
    }

    @Test
    @DisplayName("应正确配置握手超时")
    void testHandshakeTimeout() throws XmppNetworkException {
        int customTimeout = 5000;
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .port(5222)
                .handshakeTimeoutMs(customTimeout)
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);
        assertNotNull(handler);
        assertEquals(customTimeout, handler.getHandshakeTimeoutMillis());
    }
}
