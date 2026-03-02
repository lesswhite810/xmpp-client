package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppDnsException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.logic.PingIqRequestHandler;
import com.example.xmpp.logic.PingManager;
import com.example.xmpp.logic.ReconnectionManager;
import com.example.xmpp.net.DnsResolver;
import com.example.xmpp.net.SrvRecord;
import com.example.xmpp.net.XmppNettyHandler;
import com.example.xmpp.net.XmppStreamDecoder;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.util.ConnectionUtils;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.net.SslUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 基于 TCP 的 XMPP 连接实现。
 *
 * <p>提供完整的 XMPP over TCP 连接功能，包括 DNS SRV 解析、TLS/SSL 加密、
 * SASL 认证和资源绑定等核心功能。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public class XmppTcpConnection extends AbstractXmppConnection {

    /**
     * 客户端配置。
     */
    private final XmppClientConfig config;

    /**
     * Netty 工作线程组。
     */
    private EventLoopGroup workerGroup;

    /**
     * Netty 通道。
     */
    private Channel channel;

    /**
     * XMPP 协议处理器。
     */
    private XmppNettyHandler nettyHandler;

    /**
     * Ping 管理器。
     */
    private PingManager pingManager;

    /**
     * 重连管理器。
     */
    private ReconnectionManager reconnectionManager;

    /**
     * 构造 TCP 连接。
     *
     * @param config 客户端配置
     * @throws IllegalArgumentException 如果 config 为 null
     */
    public XmppTcpConnection(XmppClientConfig config) {
        this.config = Objects.requireNonNull(config, "XmppClientConfig must not be null");

        // 根据配置决定是否启用 Ping 心跳
        if (config.getKeepAlive().isPingEnabled()) {
            this.pingManager = new PingManager(this);
        }
        
        // 根据配置决定是否启用自动重连
        if (config.getKeepAlive().isReconnectionEnabled()) {
            this.reconnectionManager = new ReconnectionManager(this);
        }

        // 注册 Ping IQ 请求处理器，响应服务端 Ping
        registerIqRequestHandler(new PingIqRequestHandler());
    }

    /**
     * 建立 TCP 连接。
     *
     * @throws XmppException 如果连接过程中发生错误（如网络失败、认证失败等）
     */
    @Override
    public void connect() throws XmppException {
        // 每个连接使用独立的 EventLoopGroup
        workerGroup = new NioEventLoopGroup();
        nettyHandler = new XmppNettyHandler(config, this);

        // 1. 解析连接目标列表
        List<ConnectionTarget> targets = resolveConnectionTargets();
        if (targets.isEmpty()) {
            throw new XmppNetworkException("No connection targets available");
        }

        // 2. 创建 Netty Bootstrap（合并所有配置）
        Bootstrap bootstrap = createBootstrap();

        // 3. 尝试连接到目标列表
        this.channel = connectToTargets(bootstrap, targets)
                .orElseThrow(() -> new XmppNetworkException("All connection attempts failed"));
    }

    /**
     * 连接目标记录。
     *
     * <p>封装连接目标的主机名、IP地址和端口信息。</p>
     *
     * @since 2026-02-09
     */
    private record ConnectionTarget(String host, InetAddress address, int port) {

        /**
         * 从主机信息创建连接目标。
         *
         * @param address IP 地址（优先使用）
         * @param host    主机名
         * @param port    端口号
         * @return 如果配置了 address 或 host，返回目标；否则返回 empty
         */
        static Optional<ConnectionTarget> of(InetAddress address, String host, int port) {
            if (address == null && (host == null || host.isEmpty())) {
                return Optional.empty();
            }
            return Optional.of(new ConnectionTarget(host, address, port));
        }

        /**
         * 从 SRV 记录创建连接目标列表。
         *
         * @param records SRV 记录列表
         * @return 连接目标列表
         */
        static List<ConnectionTarget> fromSrvRecords(List<SrvRecord> records) {
            return records.stream()
                    .map(record -> new ConnectionTarget(normalizeHost(record.target()), null, record.port()))
                    .toList();
        }

        /**
         * 规范化主机名（移除尾随点）。
         *
         * @param host 原始主机名
         * @return 规范化后的主机名
         */
        private static String normalizeHost(String host) {
            return host != null && host.endsWith(".")
                    ? host.substring(0, host.length() - 1)
                    : host;
        }

        @Override
        public String toString() {
            return address != null
                    ? address.getHostAddress() + ":" + port
                    : host + ":" + port;
        }

        /**
         * 转换为 InetSocketAddress。
         *
         * @return InetSocketAddress 实例
         */
        InetSocketAddress toSocketAddress() {
            return address != null
                    ? new InetSocketAddress(address, port)
                    : InetSocketAddress.createUnresolved(host, port);
        }
    }

    /**
     * 解析连接目标列表。
     *
     * @return 连接目标列表
     */
    private List<ConnectionTarget> resolveConnectionTargets() {
        // 确定端口
        int basePort = config.getSecurity().isUsingDirectTLS()
                ? XmppConstants.DIRECT_TLS_PORT
                : XmppConstants.DEFAULT_XMPP_PORT;
        int port = config.getConnection().getPort() > 0 ? config.getConnection().getPort() : basePort;

        // 优先级1和2：从配置创建（IP地址或主机名）
        Optional<ConnectionTarget> configTarget = ConnectionTarget.of(
                config.getConnection().getHostAddress(), config.getConnection().getHost(), port);
        if (configTarget.isPresent()) {
            return List.of(configTarget.get());
        }

        // 优先级3：DNS SRV 记录
        List<SrvRecord> srvRecords = resolveSrvRecords(config.getConnection().getXmppServiceDomain());
        if (!srvRecords.isEmpty()) {
            return ConnectionTarget.fromSrvRecords(srvRecords);
        }

        // 优先级4：回退到服务域名
        return ConnectionTarget.of(null, config.getConnection().getXmppServiceDomain(), port)
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * 创建 Netty Bootstrap。
     *
     * @return 配置好的 Bootstrap
     */
    private Bootstrap createBootstrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        // P2-PERF-1 修复：Netty Pipeline 优化配置
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true); // 禁用 Nagle 算法，降低延迟
        bootstrap.option(ChannelOption.SO_RCVBUF, 64 * 1024); // 接收缓冲区 64KB
        bootstrap.option(ChannelOption.SO_SNDBUF, 64 * 1024); // 发送缓冲区 64KB
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnection().getConnectTimeout());
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // Direct TLS 模式：添加 SSL 处理器
                if (config.getSecurity().isUsingDirectTLS()) {
                    String host = config.getConnection().getHost() != null ? config.getConnection().getHost() : config.getConnection().getXmppServiceDomain();
                    int port = config.getConnection().getPort() > 0 ? config.getConnection().getPort() : XmppConstants.DIRECT_TLS_PORT;
                    SslHandler sslHandler = SslUtils.createSslHandler(host, port, config);
                    ch.pipeline().addLast(sslHandler);
                }
                // 添加 XMPP 协议处理器
                ch.pipeline().addLast(new XmppStreamDecoder());
                ch.pipeline().addLast(nettyHandler);
            }
        });
        return bootstrap;
    }

    /**
     * 尝试连接到目标列表。
     *
     * @param bootstrap Netty Bootstrap
     * @param targets   连接目标列表
     * @return 成功连接的 Channel 的 Optional
     */
    private Optional<Channel> connectToTargets(Bootstrap bootstrap, List<ConnectionTarget> targets) {
        for (ConnectionTarget target : targets) {
            try {
                log.info("Connecting to {}...", target);
                Channel channel = ConnectionUtils.connectSync(bootstrap, target.toSocketAddress());
                log.info("Connected to {}", target);
                return Optional.of(channel);
            } catch (InterruptedException e) {
                log.warn("Connection interrupted to {}", target);
            }
        }
        return Optional.empty();
    }

    /**
     * 解析 DNS SRV 记录。
     *
     * @param domain 服务域名
     * @return SRV 记录列表
     */
    private List<SrvRecord> resolveSrvRecords(String domain) {
        try (DnsResolver resolver = new DnsResolver()) {
            return resolver.resolveXmppService(domain);
        } catch (XmppDnsException e) {
            log.error("DNS SRV lookup failed: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 断开连接。
     *
     * <p>关闭 Netty 通道、停止 Ping 定时任务，并清理相关资源。</p>
     */
    @Override
    public void disconnect() {
        // 清理 PingManager
        if (pingManager != null) {
            pingManager.stopKeepAlive();
        }
        
        // 清理 ReconnectionManager
        if (reconnectionManager != null) {
            reconnectionManager.disable();
        }
        
        cleanupCollectors();

        if (channel != null) {
            channel.close();
        }

        shutdownWorkerGroup();
    }

    /**
     * 优雅关闭 Netty 工作线程组。
     */
    private void shutdownWorkerGroup() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(
                    XmppConstants.SHUTDOWN_QUIET_PERIOD_MS,
                    XmppConstants.SHUTDOWN_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            workerGroup = null;
        }
    }

    /**
     * 检查连接状态。
     *
     * @return 如果连接活跃返回 true，否则返回 false
     */
    @Override
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * 检查认证状态。
     *
     * @return 如果已认证返回 true，否则返回 false
     */
    @Override
    public boolean isAuthenticated() {
        return nettyHandler != null && nettyHandler.isAuthenticated();
    }

    /**
     * 发送节。
     *
     * @param stanza 要发送的节
     */
    @Override
    public void sendStanza(XmppStanza stanza) {
        if (stanza == null) {
            log.warn("Stanza is null, ignoring send request");
            return;
        }

        if (channel != null && channel.isActive()) {
            nettyHandler.sendStanza(channel.pipeline().lastContext(), stanza);
        } else {
            log.error("Channel is not active, cannot send stanza");
        }
    }

    /**
     * 获取客户端配置。
     *
     * @return 客户端配置对象
     */
    @Override
    public XmppClientConfig getConfig() {
        return config;
    }

    /**
     * 重置处理器状态。
     */
    @Override
    public void resetHandlerState() {
        if (nettyHandler != null) {
            nettyHandler.resetState();
        }
    }
}
