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

    // ==================== 连接配置 ====================

    /**
     * XMPP 服务域名
     */
    @Builder.Default
    private String xmppServiceDomain = "";

    /**
     * XMPP 服务器主机名
     */
    @Builder.Default
    private String host = "";

    /**
     * XMPP 服务器 IP 地址（优先于 host）
     */
    @Builder.Default
    private InetAddress hostAddress = null;

    /**
     * XMPP 服务器端口
     */
    @Builder.Default
    private int port = 0;

    /**
     * XMPP 资源标识
     */
    @Builder.Default
    private String resource = "xmpp";

    /**
     * 启用的 SASL 认证机制
     */
    @Builder.Default
    private Set<String> enabledSaslMechanisms = Set.of();

    /**
     * 连接超时时间（毫秒），默认 30000ms
     */
    @Builder.Default
    private int connectTimeout = 0;

    /**
     * 读取超时时间（毫秒），默认 60000ms
     */
    @Builder.Default
    private int readTimeout = 0;

    /**
     * 是否在连接成功后发送 Presence 节
     */
    @Builder.Default
    private boolean sendPresence = true;

    // ==================== 认证配置 ====================

    /**
     * 用户名
     */
    @Builder.Default
    private String username = "";

    /**
     * 密码
     */
    @Builder.Default
    private char[] password = null;

    /**
     * 授权标识符（可选）
     */
    @Builder.Default
    private String authzid = "";

    // ==================== 安全配置 ====================

    /**
     * 安全模式
     */
    @Builder.Default
    private SecurityMode securityMode = SecurityMode.REQUIRED;

    /**
     * 自定义 TrustManager（可选）
     */
    @Builder.Default
    private TrustManager[] customTrustManager = null;

    /**
     * 自定义 KeyManager（可选）
     */
    @Builder.Default
    private KeyManager[] keyManagers = null;

    /**
     * 自定义 SSLContext（可选）
     */
    @Builder.Default
    private SSLContext customSslContext = null;

    /**
     * 启用的 SSL 协议数组
     */
    @Builder.Default
    private String[] enabledSSLProtocols = null;

    /**
     * 启用的 SSL 密码套件数组
     */
    @Builder.Default
    private String[] enabledSSLCiphers = null;

    /**
     * 是否使用 Direct TLS（默认 false，使用 STARTTLS）
     */
    @Builder.Default
    private boolean usingDirectTLS = false;

    /**
     * SSL 握手超时时间（毫秒）
     */
    @Builder.Default
    private int handshakeTimeoutMs = 0;

    // ==================== 心跳/重连配置 ====================

    /**
     * 是否启用自动重连
     */
    @Builder.Default
    private boolean reconnectionEnabled = false;

    /**
     * 重连基础延迟（秒）
     */
    @Builder.Default
    private int reconnectionBaseDelay = 5;

    /**
     * 重连最大延迟（秒）
     */
    @Builder.Default
    private int reconnectionMaxDelay = 300;

    /**
     * 是否启用 Ping 心跳
     */
    @Builder.Default
    private boolean pingEnabled = false;

    /**
     * Ping 间隔（秒）
     */
    @Builder.Default
    private int pingInterval = 60;

    // ==================== 其他配置 ====================

    /**
     * 语言区域设置
     */
    @Builder.Default
    private Locale language = Locale.getDefault();

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
     * @return RFC 5646 语言标签，如果未设置则返回 null
     */
    public String getXmlLang() {
        if (language == null) return null;
        String tag = language.toLanguageTag();
        return "und".equals(tag) ? null : tag;
    }

    /**
     * 获取端口号，默认值 5222。
     *
     * @return XMPP 服务器端口号
     */
    public int getPort() {
        return port > 0 ? port : XmppConstants.DEFAULT_XMPP_PORT;
    }

    /**
     * 获取连接超时，默认值 30000ms。
     *
     * @return 连接超时时间（毫秒）
     */
    public int getConnectTimeout() {
        return connectTimeout > 0 ? connectTimeout : XmppConstants.DEFAULT_CONNECT_TIMEOUT_MS;
    }

    /**
     * 获取读取超时，默认值 60000ms。
     *
     * @return 读取超时时间（毫秒）
     */
    public int getReadTimeout() {
        return readTimeout > 0 ? readTimeout : XmppConstants.DEFAULT_READ_TIMEOUT_MS;
    }

    /**
     * 获取 SSL 握手超时，默认值 30000ms。
     *
     * @return SSL 握手超时时间（毫秒）
     */
    public int getHandshakeTimeoutMs() {
        return handshakeTimeoutMs > 0 ? handshakeTimeoutMs : XmppConstants.SSL_HANDSHAKE_TIMEOUT_MS;
    }
}
