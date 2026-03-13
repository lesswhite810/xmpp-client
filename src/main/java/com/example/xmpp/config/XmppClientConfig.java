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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
     * {@link XmppConstants#DEFAULT_CONNECT_TIMEOUT_MS}。</p>
     */
    @Builder.Default
    private int connectTimeout = 0;

    /**
     * 显式配置的读超时时间（毫秒）。
     *
     * <p>用于连接建立后的服务端响应等待与通道读空闲检测。Builder 原始默认值为 {@code 0}，
     * 表示未显式配置。实际生效值由 {@link #getReadTimeout()} 返回，未显式配置时使用
     * {@link XmppConstants#DEFAULT_READ_TIMEOUT_MS}。</p>
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
     * {@link XmppConstants#SSL_HANDSHAKE_TIMEOUT_MS}。</p>
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

    /**
     * 从 SystemService 创建配置。
     *
     * <p>适用于单连接场景，所有配置项均从 {@link SystemService} 中读取。</p>
     *
     * @param systemService 配置服务 Bean
     * @return XMPP 客户端配置
     */
    public static XmppClientConfig fromSystemService(SystemService systemService) {
        return fromSystemService(systemService, null);
    }

    /**
     * 从 SystemService 创建配置，并允许按连接覆盖 IP。
     *
     * <p>适用于多连接场景：公共配置统一从 {@link SystemService} 获取，仅通过 {@code nodeIp}
     * 覆盖不同连接的目标 IP。</p>
     *
     * @param systemService 配置服务 Bean
     * @param nodeIp        节点 IP；如果为 {@code null} 或空白，则回退到系统配置
     * @return XMPP 客户端配置
     */
    public static XmppClientConfig fromSystemService(SystemService systemService, String nodeIp) {
        Objects.requireNonNull(systemService, "systemService must not be null");

        String serviceDomain = requireValue(systemService, XmppConfigKeys.XMPP_SERVICE_DOMAIN);
        String username = requireValue(systemService, XmppConfigKeys.USERNAME);
        String password = requireValue(systemService, XmppConfigKeys.PASSWORD);
        String resolvedIp = trimToNull(nodeIp);
        String resolvedHost = resolvedIp != null ? resolvedIp : trimToNull(systemService.getValue(XmppConfigKeys.HOST));

        XmppClientConfigBuilder builder = XmppClientConfig.builder()
                .xmppServiceDomain(serviceDomain)
                .host(defaultString(resolvedHost))
                .hostAddress(resolveHostAddress(systemService, resolvedIp).orElse(null))
                .port(getInt(systemService, XmppConfigKeys.PORT, 0))
                .resource(defaultString(trimToNull(systemService.getValue(XmppConfigKeys.RESOURCE)), "xmpp"))
                .username(username)
                .password(password.toCharArray())
                .authzid(defaultString(trimToNull(systemService.getValue(XmppConfigKeys.AUTHZID))))
                .securityMode(getSecurityMode(systemService))
                .connectTimeout(getInt(systemService, XmppConfigKeys.CONNECT_TIMEOUT, 0))
                .readTimeout(getInt(systemService, XmppConfigKeys.READ_TIMEOUT, 0))
                .sendPresence(getBoolean(systemService, XmppConfigKeys.SEND_PRESENCE, true))
                .reconnectionEnabled(getBoolean(systemService, XmppConfigKeys.RECONNECTION_ENABLED, false))
                .reconnectionBaseDelay(getInt(systemService, XmppConfigKeys.RECONNECTION_BASE_DELAY, 5))
                .reconnectionMaxDelay(getInt(systemService, XmppConfigKeys.RECONNECTION_MAX_DELAY, 300))
                .pingEnabled(getBoolean(systemService, XmppConfigKeys.PING_ENABLED, false))
                .pingInterval(getInt(systemService, XmppConfigKeys.PING_INTERVAL, 60))
                .usingDirectTLS(getBoolean(systemService, XmppConfigKeys.DIRECT_TLS, false))
                .handshakeTimeoutMs(getInt(systemService, XmppConfigKeys.HANDSHAKE_TIMEOUT, 0))
                .tlsAuthenticationMode(getTlsAuthenticationMode(systemService))
                .enabledSaslMechanisms(getSaslMechanisms(systemService));

        return builder.build();
    }

    private static String requireValue(SystemService systemService, String key) {
        String value = trimToNull(systemService.getValue(key));
        if (value == null) {
            throw new IllegalArgumentException("Missing required config value: " + key);
        }
        return value;
    }

    private static Optional<InetAddress> resolveHostAddress(SystemService systemService, String nodeIp) {
        String value = nodeIp != null ? nodeIp : trimToNull(systemService.getValue(XmppConfigKeys.HOST_ADDRESS));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid host address: " + value, e);
        }
    }

    private static SecurityMode getSecurityMode(SystemService systemService) {
        String value = trimToNull(systemService.getValue(XmppConfigKeys.SECURITY_MODE));
        if (value == null) {
            return SecurityMode.REQUIRED;
        }
        return SecurityMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static TlsAuthenticationMode getTlsAuthenticationMode(SystemService systemService) {
        String value = trimToNull(systemService.getValue(XmppConfigKeys.TLS_AUTHENTICATION_MODE));
        if (value == null) {
            return TlsAuthenticationMode.ONE_WAY;
        }
        return TlsAuthenticationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static Set<String> getSaslMechanisms(SystemService systemService) {
        String value = trimToNull(systemService.getValue(XmppConfigKeys.ENABLED_SASL_MECHANISMS));
        if (value == null) {
            return Set.of();
        }
        LinkedHashSet<String> mechanisms = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String mechanism = trimToNull(item);
            if (mechanism != null) {
                mechanisms.add(mechanism);
            }
        }
        return mechanisms.isEmpty() ? Set.of() : Set.copyOf(mechanisms);
    }

    private static int getInt(SystemService systemService, String key, int defaultValue) {
        String value = trimToNull(systemService.getValue(key));
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static boolean getBoolean(SystemService systemService, String key, boolean defaultValue) {
        String value = trimToNull(systemService.getValue(key));
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String defaultString(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

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
     * @return 显式配置值；若原始字段值为 {@code 0}，则返回 {@link XmppConstants#DEFAULT_CONNECT_TIMEOUT_MS}
     */
    public int getConnectTimeout() {
        return connectTimeout > 0 ? connectTimeout : XmppConstants.DEFAULT_CONNECT_TIMEOUT_MS;
    }

    /**
     * 获取当前实际生效的读超时时间。
     *
     * @return 显式配置值；若原始字段值为 {@code 0}，则返回 {@link XmppConstants#DEFAULT_READ_TIMEOUT_MS}
     */
    public int getReadTimeout() {
        return readTimeout > 0 ? readTimeout : XmppConstants.DEFAULT_READ_TIMEOUT_MS;
    }

    /**
     * 获取当前实际生效的 SSL 握手超时时间。
     *
     * @return 显式配置值；若原始字段值为 {@code 0}，则返回 {@link XmppConstants#SSL_HANDSHAKE_TIMEOUT_MS}
     */
    public int getHandshakeTimeoutMs() {
        return handshakeTimeoutMs > 0 ? handshakeTimeoutMs : XmppConstants.SSL_HANDSHAKE_TIMEOUT_MS;
    }
}
