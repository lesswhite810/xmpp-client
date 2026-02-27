package com.example.xmpp.config;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.SecurityUtils;
import lombok.AccessLevel;
import lombok.Getter;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 * XMPP 客户端配置类（不可变）。
 *
 * <p>提供 XMPP 连接的完整配置选项，包括连接目标、认证信息、安全设置、SASL 机制等。</p>
 *
 * <p>使用 {@link #builder()} 创建配置实例。所有字段在构建后不可修改。</p>
 *
 * @since 2026-02-09
 */
@Getter
public class XmppClientConfig {

    /**
     * TLS 安全模式枚举。
     *
     * <p>定义 TLS 加密的不同安全级别。</p>
     */
    public enum SecurityMode {
        /** 必须使用 TLS，无法建立 TLS 则连接失败 */
        REQUIRED,
        /** 尽可能使用 TLS，服务器不支持则降级为明文 */
        IF_POSSIBLE,
        /** 禁用 TLS（仅用于测试环境） */
        DISABLED
    }

    /** XMPP 服务域名 */
    private final String xmppServiceDomain;
    /** 主机名（可选，用于覆盖 DNS 解析） */
    private final String host;
    /** IP 地址（可选，用于直接连接） */
    private final InetAddress hostAddress;
    /** 端口号 */
    private final int port;
    /** 用户名 */
    private final String username;
    /** 密码（不对外暴露） */
    @Getter(AccessLevel.NONE)
    private final char[] password;
    /** 资源标识符 */
    private final String resource;
    /** 授权标识符（authzid） */
    private final String authzid;
    /** 安全模式 */
    private final SecurityMode securityMode;
    /** 自定义信任管理器 */
    private final TrustManager[] customTrustManager;
    /** 密钥管理器数组 */
    private final KeyManager[] keyManagers;
    /** 自定义 SSL 上下文 */
    private final SSLContext customSslContext;
    /** 主机名验证器 */
    private final HostnameVerifier hostnameVerifier;
    /** 启用的 SSL 协议列表 */
    private final String[] enabledSSLProtocols;
    /** 启用的 SSL 密码套件列表 */
    private final String[] enabledSSLCiphers;
    /** 是否使用 Direct TLS 模式 */
    private final boolean usingDirectTLS;
    /** 是否启用主机名验证 */
    private final boolean enableHostnameVerification;
    /** SSL 握手超时时间（毫秒） */
    private final int handshakeTimeoutMs;
    /** 启用的 SASL 机制集合 */
    private final Set<String> enabledSaslMechanisms;
    /** 连接超时时间（毫秒） */
    private final int connectTimeout;
    /** 读取超时时间（毫秒） */
    private final int readTimeout;
    /** 是否发送在线状态 */
    private final boolean sendPresence;
    /** 是否启用压缩 */
    private final boolean compressionEnabled;
    /** 语言区域设置 */
    private final Locale language;
    /** 是否启用自动重连 */
    private final boolean reconnectionEnabled;
    /** 重连基础延迟（秒） */
    private final int reconnectionBaseDelay;
    /** 重连最大延迟（秒） */
    private final int reconnectionMaxDelay;
    /** 是否启用调试模式 */
    private final boolean debugEnabled;

    /**
     * 安全获取密码（返回克隆副本）。
     *
     * @return 密码的克隆副本，如果未设置则返回 null
     */
    public char[] getPassword() {
        return password != null ? password.clone() : null;
    }

    /**
     * 清除内存中的密码。
     *
     * <p>在不再需要密码时调用此方法，减少敏感数据在内存中的残留时间。</p>
     */
    public void clearPassword() {
        SecurityUtils.clear(this.password);
    }

    /**
     * 获取 XML 语言标签。
     *
     * @return 语言标签，如果未设置或为默认语言则返回 null
     */
    public String getXmlLang() {
        if (language == null) return null;
        String tag = language.toLanguageTag();
        return "und".equals(tag) ? null : tag;
    }

    /**
     * 创建配置 Builder。
     *
     * @return Builder 实例
     */
    public static XmppClientConfigBuilder builder() {
        return new XmppClientConfigBuilder();
    }

    /**
     * 配置构建器类。
     *
     * <p>手动实现 Builder（不使用 Lombok @Builder，以便自定义密码处理）。</p>
     *
     * @since 2026-02-09
     */
    public static class XmppClientConfigBuilder {
        private String xmppServiceDomain;
        private String host;
        private InetAddress hostAddress;
        private int port = XmppConstants.DEFAULT_XMPP_PORT;
        private String username;
        private char[] password;
        private String resource;
        private String authzid;
        private SecurityMode securityMode = SecurityMode.REQUIRED;
        private TrustManager[] customTrustManager;
        private KeyManager[] keyManagers;
        private SSLContext customSslContext;
        private HostnameVerifier hostnameVerifier;
        private String[] enabledSSLProtocols;
        private String[] enabledSSLCiphers;
        private boolean usingDirectTLS = false;
        private boolean enableHostnameVerification = true;
        private int handshakeTimeoutMs = XmppConstants.SSL_HANDSHAKE_TIMEOUT_MS;
        private Set<String> enabledSaslMechanisms;
        private int connectTimeout = XmppConstants.DEFAULT_CONNECT_TIMEOUT_MS;
        private int readTimeout = XmppConstants.DEFAULT_READ_TIMEOUT_MS;
        private boolean sendPresence = true;
        private boolean compressionEnabled = false;
        private Locale language = Locale.getDefault();
        private boolean reconnectionEnabled = true;
        private int reconnectionBaseDelay = XmppConstants.RECONNECT_BASE_DELAY_SECONDS;
        private int reconnectionMaxDelay = XmppConstants.RECONNECT_MAX_DELAY_SECONDS;
        private boolean debugEnabled = false;

        /**
         * 设置 XMPP 服务域名。
         *
         * @param val 服务域名
         * @return Builder 实例
         */
        public XmppClientConfigBuilder xmppServiceDomain(String val) {
            xmppServiceDomain = val;
            return this;
        }

        /**
         * 设置主机名。
         *
         * @param val 主机名
         * @return Builder 实例
         */
        public XmppClientConfigBuilder host(String val) {
            host = val;
            return this;
        }

        /**
         * 设置 IP 地址。
         *
         * @param val IP 地址
         * @return Builder 实例
         */
        public XmppClientConfigBuilder hostAddress(InetAddress val) {
            hostAddress = val;
            return this;
        }

        /**
         * 设置端口号。
         *
         * @param val 端口号
         * @return Builder 实例
         */
        public XmppClientConfigBuilder port(int val) {
            port = val;
            return this;
        }

        /**
         * 设置用户名。
         *
         * @param val 用户名
         * @return Builder 实例
         */
        public XmppClientConfigBuilder username(String val) {
            username = val;
            return this;
        }

        /**
         * 设置密码。
         *
         * @param val 密码字符数组
         * @return Builder 实例
         */
        public XmppClientConfigBuilder password(char[] val) {
            password = val != null ? val.clone() : null;
            return this;
        }

        /**
         * 设置资源标识符。
         *
         * @param val 资源标识符
         * @return Builder 实例
         */
        public XmppClientConfigBuilder resource(String val) {
            resource = val;
            return this;
        }

        /**
         * 设置授权标识符。
         *
         * @param val 授权标识符
         * @return Builder 实例
         */
        public XmppClientConfigBuilder authzid(String val) {
            authzid = val;
            return this;
        }

        /**
         * 设置安全模式。
         *
         * @param val 安全模式
         * @return Builder 实例
         */
        public XmppClientConfigBuilder securityMode(SecurityMode val) {
            securityMode = val;
            return this;
        }

        /**
         * 设置自定义信任管理器。
         *
         * @param val 信任管理器
         * @return Builder 实例
         */
        public XmppClientConfigBuilder customTrustManager(TrustManager[] val) {
            customTrustManager = val;
            return this;
        }

        /**
         * 设置密钥管理器数组。
         *
         * @param val 密钥管理器数组
         * @return Builder 实例
         */
        public XmppClientConfigBuilder keyManagers(KeyManager[] val) {
            keyManagers = val;
            return this;
        }

        /**
         * 设置自定义 SSL 上下文。
         *
         * @param val SSL 上下文
         * @return Builder 实例
         */
        public XmppClientConfigBuilder customSslContext(SSLContext val) {
            customSslContext = val;
            return this;
        }

        /**
         * 设置主机名验证器。
         *
         * @param val 主机名验证器
         * @return Builder 实例
         */
        public XmppClientConfigBuilder hostnameVerifier(HostnameVerifier val) {
            hostnameVerifier = val;
            return this;
        }

        /**
         * 设置启用的 SSL 协议列表。
         *
         * @param val SSL 协议数组
         * @return Builder 实例
         */
        public XmppClientConfigBuilder enabledSSLProtocols(String[] val) {
            enabledSSLProtocols = val;
            return this;
        }

        /**
         * 设置启用的 SSL 密码套件列表。
         *
         * @param val 密码套件数组
         * @return Builder 实例
         */
        public XmppClientConfigBuilder enabledSSLCiphers(String[] val) {
            enabledSSLCiphers = val;
            return this;
        }

        /**
         * 设置是否使用 Direct TLS 模式。
         *
         * @param val 是否使用 Direct TLS
         * @return Builder 实例
         */
        public XmppClientConfigBuilder usingDirectTLS(boolean val) {
            usingDirectTLS = val;
            return this;
        }

        /**
         * 设置是否启用主机名验证。
         *
         * <p>默认启用。禁用会降低安全性，仅用于测试环境。</p>
         *
         * @param val 是否启用
         * @return Builder 实例
         */
        public XmppClientConfigBuilder enableHostnameVerification(boolean val) {
            enableHostnameVerification = val;
            return this;
        }

        /**
         * 设置 SSL 握手超时时间。
         *
         * @param val 超时时间（毫秒）
         * @return Builder 实例
         */
        public XmppClientConfigBuilder handshakeTimeoutMs(int val) {
            handshakeTimeoutMs = val;
            return this;
        }

        /**
         * 设置启用的 SASL 机制集合。
         *
         * @param val SASL 机制集合
         * @return Builder 实例
         */
        public XmppClientConfigBuilder enabledSaslMechanisms(Set<String> val) {
            enabledSaslMechanisms = val;
            return this;
        }

        /**
         * 设置连接超时时间。
         *
         * @param val 超时时间（毫秒）
         * @return Builder 实例
         */
        public XmppClientConfigBuilder connectTimeout(int val) {
            connectTimeout = val;
            return this;
        }

        /**
         * 设置读取超时时间。
         *
         * @param val 超时时间（毫秒）
         * @return Builder 实例
         */
        public XmppClientConfigBuilder readTimeout(int val) {
            readTimeout = val;
            return this;
        }

        /**
         * 设置是否发送在线状态。
         *
         * @param val 是否发送
         * @return Builder 实例
         */
        public XmppClientConfigBuilder sendPresence(boolean val) {
            sendPresence = val;
            return this;
        }

        /**
         * 设置是否启用压缩。
         *
         * @param val 是否启用
         * @return Builder 实例
         */
        public XmppClientConfigBuilder compressionEnabled(boolean val) {
            compressionEnabled = val;
            return this;
        }

        /**
         * 设置语言区域。
         *
         * @param val 语言区域
         * @return Builder 实例
         */
        public XmppClientConfigBuilder language(Locale val) {
            language = val;
            return this;
        }

        /**
         * 设置是否启用自动重连。
         *
         * @param val 是否启用
         * @return Builder 实例
         */
        public XmppClientConfigBuilder reconnectionEnabled(boolean val) {
            reconnectionEnabled = val;
            return this;
        }

        /**
         * 设置重连基础延迟。
         *
         * @param val 基础延迟（秒）
         * @return Builder 实例
         */
        public XmppClientConfigBuilder reconnectionBaseDelay(int val) {
            reconnectionBaseDelay = val;
            return this;
        }

        /**
         * 设置重连最大延迟。
         *
         * @param val 最大延迟（秒）
         * @return Builder 实例
         */
        public XmppClientConfigBuilder reconnectionMaxDelay(int val) {
            reconnectionMaxDelay = val;
            return this;
        }

        /**
         * 设置是否启用调试模式。
         *
         * @param val 是否启用
         * @return Builder 实例
         */
        public XmppClientConfigBuilder debugEnabled(boolean val) {
            debugEnabled = val;
            return this;
        }

        /**
         * 构建 XmppClientConfig 实例。
         *
         * @return 配置实例
         */
        public XmppClientConfig build() {
            XmppClientConfig config = new XmppClientConfig(this);
            // 清除 Builder 中的密码副本，减少敏感数据在内存中的残留
            if (password != null) {
                SecurityUtils.clear(password);
                password = null;
            }
            return config;
        }

        /**
         * 私有构造器。
         */
        private XmppClientConfigBuilder() {
        }
    }

    /**
     * 私有构造器，只能通过 Builder 创建。
     *
     * @param builder 配置构建器
     */
    private XmppClientConfig(XmppClientConfigBuilder builder) {
        this.xmppServiceDomain = builder.xmppServiceDomain;
        this.host = builder.host;
        this.hostAddress = builder.hostAddress;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password != null ? builder.password.clone() : null;
        this.resource = builder.resource;
        this.authzid = builder.authzid;
        this.securityMode = builder.securityMode;
        this.customTrustManager = builder.customTrustManager;
        this.keyManagers = builder.keyManagers;
        this.customSslContext = builder.customSslContext;
        this.hostnameVerifier = builder.hostnameVerifier;
        this.enabledSSLProtocols = builder.enabledSSLProtocols;
        this.enabledSSLCiphers = builder.enabledSSLCiphers;
        this.usingDirectTLS = builder.usingDirectTLS;
        this.enableHostnameVerification = builder.enableHostnameVerification;
        this.handshakeTimeoutMs = builder.handshakeTimeoutMs;
        this.enabledSaslMechanisms = builder.enabledSaslMechanisms;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.sendPresence = builder.sendPresence;
        this.compressionEnabled = builder.compressionEnabled;
        this.language = builder.language;
        this.reconnectionEnabled = builder.reconnectionEnabled;
        this.reconnectionBaseDelay = builder.reconnectionBaseDelay;
        this.reconnectionMaxDelay = builder.reconnectionMaxDelay;
        this.debugEnabled = builder.debugEnabled;
    }
}
