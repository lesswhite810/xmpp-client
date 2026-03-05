package com.example.xmpp.config;

import com.example.xmpp.util.XmppConstants;
import lombok.Builder;
import lombok.Getter;

import java.net.InetAddress;
import java.util.Set;

/**
 * 连接配置。
 *
 * <p>包含 XMPP 连接所需的所有网络和协议配置参数，
 * 包括服务器地址、端口、超时设置等。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class ConnectionConfig {

    /**
     * XMPP 服务域名。
     *
     * <p>用于 DNS SRV 记录查询和 SASL 认证。例如：{@code example.com}</p>
     */
    private String xmppServiceDomain;

    /**
     * 主机名（可选，用于覆盖 DNS 解析）。
     *
     * <p>当设置此值时，优先使用此主机名进行连接，
     * 跳过 DNS SRV 记录查询。</p>
     */
    private String host;

    /**
     * IP 地址（可选，用于直接连接）。
     *
     * <p>当设置此值时，直接使用此 IP 地址建立 TCP 连接。
     * 优先级最高：hostAddress > host > DNS SRV</p>
     */
    private InetAddress hostAddress;

    /**
     * 端口号。
     *
     * <p>XMPP 服务器端口。默认值为 5222（STARTTLS 模式）。
     * Direct TLS 模式默认使用 5223。</p>
     */
    @Builder.Default
    private int port = XmppConstants.DEFAULT_XMPP_PORT;

    /**
     * 资源标识符。
     *
     * <p>用于区分同一用户的多个并发连接。
     * 如果未设置，服务器将自动分配资源。</p>
     */
    private String resource;

    /**
     * 启用的 SASL 机制集合。
     *
     * <p>指定允许使用的 SASL 认证机制。
     * 如果未设置或为空，将使用服务器支持的默认机制。</p>
     */
    private Set<String> enabledSaslMechanisms;

    /**
     * 连接超时时间（毫秒）。
     *
     * <p>TCP 连接建立的超时时间。</p>
     */
    @Builder.Default
    private int connectTimeout = XmppConstants.DEFAULT_CONNECT_TIMEOUT_MS;

    /**
     * 读取超时时间（毫秒）。
     *
     * <p>等待服务器响应的超时时间。</p>
     */
    @Builder.Default
    private int readTimeout = XmppConstants.DEFAULT_READ_TIMEOUT_MS;

    /**
     * 是否发送在线状态。
     *
     * <p>连接成功后是否自动发送 Presence 节通知在线状态。</p>
     */
    @Builder.Default
    private boolean sendPresence = true;
}
