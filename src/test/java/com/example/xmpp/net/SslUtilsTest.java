package com.example.xmpp.net;

import com.example.xmpp.config.AuthConfig;
import com.example.xmpp.config.ConnectionConfig;
import com.example.xmpp.config.SecurityConfig;
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
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain("example.com")
                        .host("example.com")
                        .port(5222)
                        .build())
                .security(SecurityConfig.builder()
                        .securityMode(SecurityConfig.SecurityMode.IF_POSSIBLE)
                        .build())
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
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain("example.com")
                        .host("example.com")
                        .port(5222)
                        .build())
                .security(SecurityConfig.builder()
                        .customTrustManager(new TrustManager[]{trustAllManager})
                        .build())
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);
        assertNotNull(handler);
    }

    @Test
    @DisplayName("Direct TLS 模式应正确配置")
    void testDirectTLSConfig() {
        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain("example.com")
                        .host("example.com")
                        .port(5223)
                        .build())
                .security(SecurityConfig.builder()
                        .usingDirectTLS(true)
                        .build())
                .build();

        assertTrue(config.getSecurity().isUsingDirectTLS());
        assertEquals(5223, config.getConnection().getPort());
    }

    @Test
    @DisplayName("应正确配置握手超时")
    void testHandshakeTimeout() throws XmppNetworkException {
        int customTimeout = 5000;
        XmppClientConfig config = XmppClientConfig.builder()
                .connection(ConnectionConfig.builder()
                        .xmppServiceDomain("example.com")
                        .host("example.com")
                        .port(5222)
                        .build())
                .security(SecurityConfig.builder()
                        .handshakeTimeoutMs(customTimeout)
                        .build())
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);
        assertNotNull(handler);
        assertEquals(customTimeout, handler.getHandshakeTimeoutMillis());
    }
}
