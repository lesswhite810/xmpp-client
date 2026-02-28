package com.example.xmpp.config;

import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.XmppConstants;
import lombok.Builder;
import lombok.Getter;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;

/**
 * XMPP 客户端配置类（不可变）。
 *
 * <p>提供两种配置方式：</p>
 * <ul>
 *   <li>新方式：使用模块化配置类（ConnectionConfig, AuthConfig, SecurityConfig, KeepAliveConfig）</li>
 *   <li>旧方式：直接使用 XmppClientConfig.builder() 方法（保留向后兼容）</li>
 * </ul>
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class XmppClientConfig {

    /**
     * TLS 安全模式枚举。
     */
    public enum SecurityMode {
        REQUIRED,
        IF_POSSIBLE,
        DISABLED
    }

    // ==================== 模块化配置（推荐） ====================

    /** 连接配置 */
    private final ConnectionConfig connection;

    /** 认证配置 */
    private final AuthConfig auth;

    /** 安全配置 */
    private final SecurityConfig security;

    /** 心跳/保活配置 */
    private final KeepAliveConfig keepAlive;

    /** 语言区域设置 */
    @Builder.Default
    private Locale language = Locale.getDefault();

    // ==================== 向后兼容字段（旧 API） ====================

    /** XMPP 服务域名 */
    private final String xmppServiceDomain;
    private final String host;
    private final InetAddress hostAddress;
    @Builder.Default
    private int port = XmppConstants.DEFAULT_XMPP_PORT;
    private final String username;
    private final char[] password;
    private final String resource;
    private final String authzid;
    @Builder.Default
    private SecurityMode securityMode = SecurityMode.REQUIRED;
    private final TrustManager[] customTrustManager;
    private final KeyManager[] keyManagers;
    private final SSLContext customSslContext;
    private final String[] enabledSSLProtocols;
    private final String[] enabledSSLCiphers;
    @Builder.Default
    private boolean usingDirectTLS = false;
    @Builder.Default
    private int handshakeTimeoutMs = XmppConstants.SSL_HANDSHAKE_TIMEOUT_MS;
    private final Set<String> enabledSaslMechanisms;
    @Builder.Default
    private int connectTimeout = XmppConstants.DEFAULT_CONNECT_TIMEOUT_MS;
    @Builder.Default
    private int readTimeout = XmppConstants.DEFAULT_READ_TIMEOUT_MS;
    @Builder.Default
    private boolean sendPresence = true;
    @Builder.Default
    private boolean reconnectionEnabled = false;
    @Builder.Default
    private int reconnectionBaseDelay = XmppConstants.RECONNECT_BASE_DELAY_SECONDS;
    @Builder.Default
    private int reconnectionMaxDelay = XmppConstants.RECONNECT_MAX_DELAY_SECONDS;
    @Builder.Default
    private boolean pingEnabled = false;
    @Builder.Default
    private int pingInterval = XmppConstants.DEFAULT_PING_INTERVAL_SECONDS;

    // ==================== 便捷方法 ====================

    /**
     * 安全获取密码（返回克隆副本）。
     */
    public char[] getPassword() {
        if (password != null) {
            return password.clone();
        }
        return auth != null ? auth.getPassword() : null;
    }

    /**
     * 清除内存中的密码。
     */
    public void clearPassword() {
        if (password != null) {
            SecurityUtils.clear(password);
        }
        if (auth != null && auth.getPassword() != null) {
            SecurityUtils.clear(auth.getPassword());
        }
    }

    /**
     * 获取 XML 语言标签。
     */
    public String getXmlLang() {
        if (language == null) return null;
        String tag = language.toLanguageTag();
        return "und".equals(tag) ? null : tag;
    }

    /**
     * 获取安全模式（兼容新旧 API）。
     */
    public SecurityMode getSecurityMode() {
        if (security != null && security.getSecurityMode() != null) {
            return switch (security.getSecurityMode()) {
                case REQUIRED -> SecurityMode.REQUIRED;
                case IF_POSSIBLE -> SecurityMode.IF_POSSIBLE;
                case DISABLED -> SecurityMode.DISABLED;
            };
        }
        return securityMode;
    }

    /**
     * 是否使用 Direct TLS。
     */
    public boolean isUsingDirectTLS() {
        if (security != null) {
            return security.isUsingDirectTLS();
        }
        return usingDirectTLS;
    }

    /**
     * 是否启用重连。
     */
    public boolean isReconnectionEnabled() {
        if (keepAlive != null) {
            return keepAlive.isReconnectionEnabled();
        }
        return reconnectionEnabled;
    }

    /**
     * 是否启用 Ping。
     */
    public boolean isPingEnabled() {
        if (keepAlive != null) {
            return keepAlive.isPingEnabled();
        }
        return pingEnabled;
    }
}
