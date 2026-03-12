package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实服务器失败模式测试。
 *
 * <p>覆盖更严重的配置错误、TLS 缺参和认证机制不可用等失败场景。</p>
 *
 * @since 2026-03-13
 */
class XmppRealServerFailureModeTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final int DIRECT_TLS_PORT = 5223;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    @Test
    void testMutualTlsWithoutKeyManagersFailsFastOnStartTls() {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(ADMIN_USERNAME)
                .password(ADMIN_PASSWORD.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.REQUIRED)
                .tlsAuthenticationMode(XmppClientConfig.TlsAuthenticationMode.MUTUAL)
                .customTrustManager(new TrustManager[]{TRUST_ALL_MANAGER})
                .build());

        XmppException exception = assertThrows(XmppException.class, connection::connect,
                "双向 TLS 缺少 KeyManager 时应直接失败");
        assertTrue(exception.getMessage().contains("KeyManager") || exception.getCause() instanceof XmppNetworkException,
                "异常信息应指出 KeyManager 缺失");
        connection.disconnect();
    }

    @Test
    void testMutualTlsWithEmptyKeyManagersFailsFastOnStartTls() {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(ADMIN_USERNAME)
                .password(ADMIN_PASSWORD.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.REQUIRED)
                .tlsAuthenticationMode(XmppClientConfig.TlsAuthenticationMode.MUTUAL)
                .customTrustManager(new TrustManager[]{TRUST_ALL_MANAGER})
                .keyManagers(new javax.net.ssl.KeyManager[0])
                .build());

        XmppException exception = assertThrows(XmppException.class, connection::connect,
                "双向 TLS 配置空 KeyManager 数组时应直接失败");
        assertTrue(exception.getMessage().contains("KeyManager") || exception.getCause() instanceof XmppNetworkException,
                "异常信息应指出 KeyManager 缺失");
        connection.disconnect();
    }

    @Test
    void testMutualDirectTlsWithoutKeyManagersFailsFast() {
        Assumptions.assumeTrue(isPortReachable(HOST, DIRECT_TLS_PORT, 1000), "本地真实服务器未开放 5223 Direct TLS 端口");

        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(DIRECT_TLS_PORT)
                .username(ADMIN_USERNAME)
                .password(ADMIN_PASSWORD.toCharArray())
                .usingDirectTLS(true)
                .securityMode(XmppClientConfig.SecurityMode.REQUIRED)
                .tlsAuthenticationMode(XmppClientConfig.TlsAuthenticationMode.MUTUAL)
                .customTrustManager(new TrustManager[]{TRUST_ALL_MANAGER})
                .build());

        assertThrows(Throwable.class, connection::connect,
                "Direct TLS 双向认证缺少 KeyManager 时应直接快速失败");
        connection.disconnect();
    }

    @Test
    void testUnsupportedSaslMechanismFailsAuthentication() {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(ADMIN_USERNAME)
                .password(ADMIN_PASSWORD.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .enabledSaslMechanisms(Set.of("NON-EXISTENT-MECH"))
                .build());

        XmppException exception = assertThrows(XmppException.class, connection::connect,
                "仅允许不存在的 SASL 机制时应认证失败");
        assertInstanceOf(XmppException.class, exception, "应抛出 XMPP 相关异常");
        connection.disconnect();
    }

    @Test
    void testPlainMechanismWithoutTlsFailsFast() {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(ADMIN_USERNAME)
                .password(ADMIN_PASSWORD.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .enabledSaslMechanisms(Set.of("PLAIN"))
                .build());

        XmppException exception = assertThrows(XmppException.class, connection::connect,
                "明文链路上强制 PLAIN 时应被拒绝");
        assertTrue(exception.getMessage().contains("PLAIN") || exception.getCause() instanceof XmppException,
                "异常信息应体现 PLAIN 需要 TLS");
        connection.disconnect();
    }

    @Test
    void testWrongPasswordFailsEvenWhenTlsRequiredSucceeds() {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(ADMIN_USERNAME)
                .password("definitely-wrong-password".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.REQUIRED)
                .customTrustManager(new TrustManager[]{TRUST_ALL_MANAGER})
                .build());

        XmppException exception = assertThrows(XmppException.class, connection::connect,
                "TLS 成功建立后错误密码仍应导致认证失败");
        assertNotNull(exception.getMessage(), "认证失败应提供异常信息");
        connection.disconnect();
    }

    private boolean isPortReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
