package com.example.xmpp.config;

import lombok.Builder;
import lombok.Getter;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 * 安全/SSL 配置。
 */
@Getter
@Builder
public class SecurityConfig {

    /** TLS 安全模式 */
    @Builder.Default
    private SecurityMode securityMode = SecurityMode.REQUIRED;

    /** 自定义信任管理器 */
    private TrustManager[] customTrustManager;

    /** 密钥管理器数组 */
    private KeyManager[] keyManagers;

    /** 自定义 SSL 上下文 */
    private SSLContext customSslContext;

    /** 启用的 SSL 协议列表 */
    private String[] enabledSSLProtocols;

    /** 启用的 SSL 密码套件列表 */
    private String[] enabledSSLCiphers;

    /** 是否使用 Direct TLS 模式 */
    @Builder.Default
    private boolean usingDirectTLS = false;

    /** SSL 握手超时时间（毫秒） */
    @Builder.Default
    private int handshakeTimeoutMs = 10000;

    /**
     * TLS 安全模式枚举。
     */
    public enum SecurityMode {
        /** 必须使用 TLS */
        REQUIRED,
        /** 尽可能使用 TLS */
        IF_POSSIBLE,
        /** 禁用 TLS */
        DISABLED
    }
}
