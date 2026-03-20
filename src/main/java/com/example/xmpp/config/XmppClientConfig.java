package com.example.xmpp.config;

import com.example.xmpp.util.XmppConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * XMPP 客户端配置。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
@FieldDefaults(makeFinal = true)
public class XmppClientConfig {

    /**
     * 安全模式枚举。
     */
    public enum SecurityMode {
        /**
         * 要求 TLS 加密连接
         */
        REQUIRED,
        /**
         * 尽可能使用 TLS 加密
         */
        IF_POSSIBLE,
        /**
         * 禁用 TLS 加密
         */
        DISABLED
    }

    /**
     * TLS 认证模式。
     */
    public enum TlsAuthenticationMode {
        ONE_WAY,
        MUTUAL
    }

    /**
     * XMPP 服务域名。
     */
    @Builder.Default
    private String xmppServiceDomain = "";

    /**
     * XMPP 服务器主机名。
     */
    @Builder.Default
    private String host = "";

    /**
     * XMPP 服务器 IP 地址。
     */
    @Builder.Default
    private InetAddress hostAddress = null;

    /**
     * XMPP 服务器端口。
     */
    @Builder.Default
    private int port = 0;

    /**
     * XMPP 资源标识。
     */
    @Builder.Default
    private String resource = "xmpp";

    /**
     * 启用的 SASL 认证机制集合。
     */
    @Builder.Default
    private Set<String> enabledSaslMechanisms = Set.of();

    /**
     * TCP 连接超时时间，单位为毫秒。
     */
    @Builder.Default
    private int connectTimeout = 0;

    /**
     * 读超时时间，单位为毫秒。
     */
    @Builder.Default
    private int readTimeout = 0;

    /**
     * 是否在连接成功后发送初始 Presence 节。
     */
    @Builder.Default
    private boolean sendPresence = true;

    /**
     * 用户名。
     */
    @Builder.Default
    private String username = "";

    /**
     * 密码。
     */
    @Builder.Default
    private char[] password = new char[0];

    /**
     * 授权标识符。
     */
    @Builder.Default
    private String authzid = "";

    /**
     * 安全模式。
     */
    @Builder.Default
    private SecurityMode securityMode = SecurityMode.REQUIRED;

    /**
     * 自定义 TrustManager。
     */
    @Builder.Default
    private TrustManager[] customTrustManager = null;

    /**
     * 自定义 KeyManager。
     */
    @Builder.Default
    private KeyManager[] keyManagers = null;

    /**
     * TLS 认证模式。
     */
    @Builder.Default
    private TlsAuthenticationMode tlsAuthenticationMode = TlsAuthenticationMode.ONE_WAY;

    /**
     * 自定义 SSLContext。
     */
    @Builder.Default
    private SSLContext customSslContext = null;

    /**
     * 显式启用的 SSL/TLS 协议数组。
     */
    @Builder.Default
    private String[] enabledSSLProtocols = null;

    /**
     * 显式启用的 SSL 密码套件数组。
     */
    @Builder.Default
    private String[] enabledSSLCiphers = null;

    /**
     * 是否使用 Direct TLS。
     */
    @Builder.Default
    private boolean usingDirectTLS = false;

    /**
     * SSL 握手超时时间，单位为毫秒。
     */
    @Builder.Default
    private int handshakeTimeoutMs = 0;

    /**
     * 是否启用自动重连。
     */
    @Builder.Default
    private boolean reconnectionEnabled = false;

    /**
     * 重连基础延迟，单位为秒。
     */
    @Builder.Default
    private int reconnectionBaseDelay = 5;

    /**
     * 重连最大延迟，单位为秒。
     */
    @Builder.Default
    private int reconnectionMaxDelay = 300;

    /**
     * 是否启用 Ping 心跳。
     */
    @Builder.Default
    private boolean pingEnabled = false;

    /**
     * Ping 间隔，单位为秒。
     */
    @Builder.Default
    private int pingInterval = XmppConstants.DEFAULT_PING_INTERVAL_SECONDS;

    /**
     * 语言区域设置。
     */
    @Builder.Default
    private Locale language = Locale.getDefault();

    /**
     * 获取 XML 语言标签。
     *
     * @return 语言标签；未设置时返回 null
     */
    public String getXmlLang() {
        return Optional.ofNullable(language)
                .map(Locale::toLanguageTag)
                .filter(tag -> !"und".equals(tag))
                .orElse(null);
    }

    /**
     * 获取密码副本。
     *
     * @return 密码字符数组副本
     */
    public char[] getPassword() {
        return password.clone();
    }

    /**
     * 获取端口。
     *
     * @return 端口
     */
    public int getPort() {
        if (port > 0) {
            return port;
        }
        return usingDirectTLS ? XmppConstants.DIRECT_TLS_PORT : XmppConstants.DEFAULT_XMPP_PORT;
    }

    /**
     * 获取连接超时时间。
     *
     * @return 连接超时时间
     */
    public int getConnectTimeout() {
        return connectTimeout > 0
                ? connectTimeout
                : Math.toIntExact(TimeUnit.SECONDS.toMillis(XmppConstants.DEFAULT_CONNECT_TIMEOUT_SECONDS));
    }

    /**
     * 获取读超时时间。
     *
     * @return 读超时时间
     */
    public int getReadTimeout() {
        return readTimeout > 0
                ? readTimeout
                : Math.toIntExact(TimeUnit.SECONDS.toMillis(XmppConstants.DEFAULT_READ_TIMEOUT_SECONDS));
    }

    /**
     * 获取 SSL 握手超时时间。
     *
     * @return SSL 握手超时时间
     */
    public int getHandshakeTimeoutMs() {
        return handshakeTimeoutMs > 0
                ? handshakeTimeoutMs
                : Math.toIntExact(TimeUnit.SECONDS.toMillis(XmppConstants.SSL_HANDSHAKE_TIMEOUT_SECONDS));
    }

}
