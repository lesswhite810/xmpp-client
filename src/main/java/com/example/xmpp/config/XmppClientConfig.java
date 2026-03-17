package com.example.xmpp.config;

import com.example.xmpp.util.SecurityUtils;
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
 * XMPP 客户端配置类（不可变）。
 *
 * <p>集中管理所有连接参数，支持连接、认证、安全和心跳配置。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * XmppClientConfig config = XmppClientConfig.builder()
 *     .xmppServiceDomain("example.com")
 *     .username("user")
 *     .password("password".toCharArray())
 *     .securityMode(SecurityMode.REQUIRED)
 *     .pingEnabled(true)
 *     .build();
 * }</pre>
 *
 * @since 2026-02-09
 */
@Getter
@Builder
@FieldDefaults(makeFinal = true)
public class XmppClientConfig {

    /**
     * 安全模式枚举。
     *
     * <p>控制 TLS/SSL 加密要求级别。</p>
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
     *
     * <p>单向认证表示仅校验服务端证书；双向认证表示客户端还需提供证书参与认证。</p>
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
     *
     * <p>如果同时配置了 {@link #host} 和 {@link #hostAddress}，优先使用 IP 地址。</p>
     */
    @Builder.Default
    private InetAddress hostAddress = null;

    /**
     * 显式配置的 XMPP 服务器端口。
     *
     * <p>Builder 原始默认值为 {@code 0}，表示未显式配置。实际生效端口由 {@link #getPort()}
     * 按连接模式计算：普通模式使用 5222，Direct TLS 模式使用 5223。</p>
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
     *
     * <p>为空集合时表示由客户端根据服务端能力自动选择最优机制。</p>
     */
    @Builder.Default
    private Set<String> enabledSaslMechanisms = Set.of();

    /**
     * 显式配置的 TCP 连接建立超时时间（毫秒）。
     *
     * <p>Builder 原始默认值为 {@code 0}，表示未显式配置。实际生效值由
     * {@link #getConnectTimeout()} 返回，未显式配置时使用
     * {@link XmppConstants#DEFAULT_CONNECT_TIMEOUT_SECONDS} 换算后的毫秒值。</p>
     */
    @Builder.Default
    private int connectTimeout = 0;

    /**
     * 显式配置的读超时时间（毫秒）。
     *
     * <p>用于连接建立后的服务端响应等待与通道读空闲检测。Builder 原始默认值为 {@code 0}，
     * 表示未显式配置。实际生效值由 {@link #getReadTimeout()} 返回，未显式配置时使用
     * {@link XmppConstants#DEFAULT_READ_TIMEOUT_SECONDS} 换算后的毫秒值。</p>
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
    private char[] password = null;

    /**
     * 授权标识符。
     *
     * <p>该字段为可选配置，通常用于 SASL 授权身份与认证身份分离的场景。</p>
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
     *
     * <p>如果未配置，则使用 JVM 默认信任配置或自定义 SSLContext 中的信任配置。</p>
     */
    @Builder.Default
    private TrustManager[] customTrustManager = null;

    /**
     * 自定义 KeyManager（可选）。
     *
     * <p>当 TLS 认证模式为双向认证时，通常需要提供客户端证书对应的 KeyManager。</p>
     */
    @Builder.Default
    private KeyManager[] keyManagers = null;

    /**
     * TLS 认证模式。
     *
     * <p>Builder 原始默认值为 {@link TlsAuthenticationMode#ONE_WAY}，表示仅校验服务端证书。</p>
     */
    @Builder.Default
    private TlsAuthenticationMode tlsAuthenticationMode = TlsAuthenticationMode.ONE_WAY;

    /**
     * 自定义 SSLContext。
     *
     * <p>如果配置该项，则优先使用该上下文创建 TLS 连接。</p>
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
     *
     * <p>Builder 原始默认值为 {@code false}，表示优先走 STARTTLS 升级流程。</p>
     */
    @Builder.Default
    private boolean usingDirectTLS = false;

    /**
     * 显式配置的 SSL 握手超时时间（毫秒）。
     *
     * <p>Builder 原始默认值为 {@code 0}，表示未显式配置。实际生效值由
     * {@link #getHandshakeTimeoutMs()} 返回，未显式配置时使用
     * {@link XmppConstants#SSL_HANDSHAKE_TIMEOUT_SECONDS} 换算后的毫秒值。</p>
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
    private int pingInterval = 60;

    /**
     * 语言区域设置。
     */
    @Builder.Default
    private Locale language = Locale.getDefault();

    private static String trimToNull(String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(trimmed -> !trimmed.isEmpty())
                .orElse(null);
    }

    /**
     * 清除内存中的密码。
     *
     * <p>安全地清除认证密码的内存副本，防止密码泄露。</p>
     */
    public void clearPassword() {
        SecurityUtils.clear(password);
    }

    /**
     * 获取 XML 语言标签。
     *
     * @return RFC 5646 语言标签；如果未设置或无法确定则返回 {@code null}
     */
    public String getXmlLang() {
        return Optional.ofNullable(language)
                .map(Locale::toLanguageTag)
                .filter(tag -> !"und".equals(tag))
                .orElse(null);
    }

    /**
     * 获取当前实际生效的端口号。
     *
     * @return 显式配置端口；若原始字段值为 {@code 0}，则普通模式返回 5222，Direct TLS 模式返回 5223
     */
    public int getPort() {
        if (port > 0) {
            return port;
        }
        return usingDirectTLS ? XmppConstants.DIRECT_TLS_PORT : XmppConstants.DEFAULT_XMPP_PORT;
    }

    /**
     * 获取当前实际生效的连接超时时间。
     *
     * @return 显式配置值；若原始字段值为 {@code 0}，则返回
     * {@link XmppConstants#DEFAULT_CONNECT_TIMEOUT_SECONDS} 换算后的毫秒值
     */
    public int getConnectTimeout() {
        return connectTimeout > 0
                ? connectTimeout
                : Math.toIntExact(TimeUnit.SECONDS.toMillis(XmppConstants.DEFAULT_CONNECT_TIMEOUT_SECONDS));
    }

    /**
     * 获取当前实际生效的读超时时间。
     *
     * @return 显式配置值；若原始字段值为 {@code 0}，则返回
     * {@link XmppConstants#DEFAULT_READ_TIMEOUT_SECONDS} 换算后的毫秒值
     */
    public int getReadTimeout() {
        return readTimeout > 0
                ? readTimeout
                : Math.toIntExact(TimeUnit.SECONDS.toMillis(XmppConstants.DEFAULT_READ_TIMEOUT_SECONDS));
    }

    /**
     * 获取当前实际生效的 SSL 握手超时时间。
     *
     * @return 显式配置值；若原始字段值为 {@code 0}，则返回
     * {@link XmppConstants#SSL_HANDSHAKE_TIMEOUT_SECONDS} 换算后的毫秒值
     */
    public int getHandshakeTimeoutMs() {
        return handshakeTimeoutMs > 0
                ? handshakeTimeoutMs
                : Math.toIntExact(TimeUnit.SECONDS.toMillis(XmppConstants.SSL_HANDSHAKE_TIMEOUT_SECONDS));
    }
}
