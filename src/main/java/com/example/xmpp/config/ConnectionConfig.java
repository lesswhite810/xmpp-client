package com.example.xmpp.config;

import com.example.xmpp.util.XmppConstants;
import lombok.Builder;
import lombok.Getter;

import java.net.InetAddress;
import java.util.Set;

/**
 * 连接配置。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class ConnectionConfig {

    /** XMPP 服务域名 *
 * @since 2026-02-09
 */
    private String xmppServiceDomain;

    /** 主机名（可选，用于覆盖 DNS 解析） *
 * @since 2026-02-09
 */
    private String host;

    /** IP 地址（可选，用于直接连接） *
 * @since 2026-02-09
 */
    private InetAddress hostAddress;

    /** 端口号 *
 * @since 2026-02-09
 */
    @Builder.Default
    private int port = XmppConstants.DEFAULT_XMPP_PORT;

    /** 资源标识符 *
 * @since 2026-02-09
 */
    private String resource;

    /** 启用的 SASL 机制集合 *
 * @since 2026-02-09
 */
    private Set<String> enabledSaslMechanisms;

    /** 连接超时时间（毫秒） *
 * @since 2026-02-09
 */
    @Builder.Default
    private int connectTimeout = XmppConstants.DEFAULT_CONNECT_TIMEOUT_MS;

    /** 读取超时时间（毫秒） *
 * @since 2026-02-09
 */
    @Builder.Default
    private int readTimeout = XmppConstants.DEFAULT_READ_TIMEOUT_MS;

    /** 是否发送在线状态 *
 * @since 2026-02-09
 */
    @Builder.Default
    private boolean sendPresence = true;
}
