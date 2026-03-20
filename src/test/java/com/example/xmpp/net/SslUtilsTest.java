package com.example.xmpp.net;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppNetworkException;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.List;

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

    @Test
    @DisplayName("双向认证模式缺少 KeyManager 时应失败")
    void testMutualTlsWithoutKeyManagersFails() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .tlsAuthenticationMode(XmppClientConfig.TlsAuthenticationMode.MUTUAL)
                .build();

        XmppNetworkException exception = assertThrows(XmppNetworkException.class,
                () -> SslUtils.createSslHandler(config));
        assertTrue(exception.getMessage().contains("KeyManager"));
    }

    @Test
    @DisplayName("双向认证模式在提供自定义 SSLContext 时可跳过 KeyManager 校验")
    void testMutualTlsWithCustomSslContextSucceeds() throws Exception {
        SSLContext customContext = SSLContext.getInstance("TLS");
        customContext.init(null, null, null);

        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .tlsAuthenticationMode(XmppClientConfig.TlsAuthenticationMode.MUTUAL)
                .customSslContext(customContext)
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);

        assertNotNull(handler);
    }

    @Test
    @DisplayName("创建 SslHandler 时不启用主机校验参数")
    void testCreateSslHandlerDoesNotUsePeerHostVerification() throws XmppNetworkException {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("")
                .port(5222)
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);

        assertNotNull(handler);
        assertNull(handler.engine().getPeerHost());
    }

    @Test
    @DisplayName("应优先使用自定义 SSLContext")
    void testCreateSslHandlerUsesCustomSslContext() throws Exception {
        SSLContext customContext = SSLContext.getInstance("TLS");
        customContext.init(null, null, null);

        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .customSslContext(customContext)
                .handshakeTimeoutMs(3000)
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);

        assertNotNull(handler);
        assertEquals(3000, handler.getHandshakeTimeoutMillis());
        assertTrue(handler.engine().getUseClientMode());
    }

    @Test
    @DisplayName("未显式配置握手超时时应使用默认值")
    void testCreateSslHandlerUsesDefaultHandshakeTimeout() throws XmppNetworkException {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);

        assertEquals(10000, handler.getHandshakeTimeoutMillis());
    }

    @Test
    @DisplayName("仅应启用受支持的协议和密码套件")
    void testCreateSslHandlerFiltersUnsupportedProtocolsAndCiphers() throws XmppNetworkException {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("example.com")
                .enabledSSLProtocols(new String[]{"TLSv1.3", "UNSUPPORTED_PROTOCOL"})
                .enabledSSLCiphers(new String[]{"TLS_AES_128_GCM_SHA256", "UNSUPPORTED_CIPHER"})
                .build();

        SslHandler handler = SslUtils.createSslHandler(config);

        assertTrue(List.of(handler.engine().getEnabledProtocols()).contains("TLSv1.3"));
        assertFalse(List.of(handler.engine().getEnabledProtocols()).contains("UNSUPPORTED_PROTOCOL"));
        assertTrue(List.of(handler.engine().getEnabledCipherSuites()).contains("TLS_AES_128_GCM_SHA256"));
        assertFalse(List.of(handler.engine().getEnabledCipherSuites()).contains("UNSUPPORTED_CIPHER"));
    }
}
