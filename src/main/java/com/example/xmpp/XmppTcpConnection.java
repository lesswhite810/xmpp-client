package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppDnsException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.exception.XmppProtocolException;
import com.example.xmpp.handler.PingIqRequestHandler;
import com.example.xmpp.logic.PingManager;
import com.example.xmpp.logic.ReconnectionManager;
import com.example.xmpp.net.DnsResolver;
import com.example.xmpp.net.SrvRecord;
import com.example.xmpp.net.SslUtils;
import com.example.xmpp.net.XmppNettyHandler;
import com.example.xmpp.net.XmppStreamDecoder;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.util.ConnectionUtils;
import com.example.xmpp.util.XmppConstants;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于 TCP 的 XMPP 连接实现。
 *
 * <p>负责创建 Netty 管道、建立 XMPP 会话，并协调 Ping 与重连等生命周期管理组件。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
@Getter
public class XmppTcpConnection extends AbstractXmppConnection {

    private final XmppClientConfig config;

    private EventLoopGroup workerGroup;

    private Channel channel;

    private XmppNettyHandler nettyHandler;

    private PingManager pingManager;

    private ReconnectionManager reconnectionManager;

    private volatile CompletableFuture<Void> connectionReadyFuture = new CompletableFuture<>();

    /**
     * 创建 TCP XMPP 连接。
     *
     * @param config 客户端配置
     * @throws IllegalArgumentException 如果 {@code config} 为 {@code null}
     */
    public XmppTcpConnection(XmppClientConfig config) {
        this.config = Objects.requireNonNull(config, "XmppClientConfig must not be null");

        if (config.isPingEnabled()) {
            this.pingManager = new PingManager(this);
        }

        if (config.isReconnectionEnabled()) {
            this.reconnectionManager = new ReconnectionManager(this);
        }

        registerIqRequestHandler(new PingIqRequestHandler());
    }

    /**
     * 同步建立连接并等待会话就绪。
     *
     * @throws XmppException 如果建连、认证或会话初始化失败
     */
    @Override
    public void connect() throws XmppException {
        try {
            connectAsync().get(config.getReadTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            disconnect();
            throw new XmppNetworkException("Interrupted while waiting for XMPP session to become ready", e);
        } catch (TimeoutException e) {
            disconnect();
            throw new XmppNetworkException("Timed out waiting for XMPP session to become ready", e);
        } catch (ExecutionException e) {
            disconnect();
            throw unwrapXmppException(e.getCause());
        }
    }

    /**
     * 异步建立连接并返回会话就绪 Future。
     *
     * @return 当前会话的就绪 Future
     * @throws XmppException 如果连接目标解析或连接初始化失败
     */
    public CompletableFuture<Void> connectAsync() throws XmppException {
        resetConnectionLifecycleEvents();
        connectionReadyFuture = new CompletableFuture<>();
        workerGroup = new NioEventLoopGroup();
        nettyHandler = new XmppNettyHandler(config, this);

        List<ConnectionTarget> targets = resolveConnectionTargets();
        if (targets.isEmpty()) {
            XmppNetworkException exception = new XmppNetworkException("No connection targets available");
            connectionReadyFuture.completeExceptionally(exception);
            throw exception;
        }

        Bootstrap bootstrap = createBootstrap();
        try {
            this.channel = connectToTargets(bootstrap, targets)
                    .orElseThrow(() -> new XmppNetworkException("All connection attempts failed"));
            return connectionReadyFuture;
        } catch (XmppException e) {
            connectionReadyFuture.completeExceptionally(e);
            throw e;
        } catch (RuntimeException e) {
            XmppNetworkException exception = new XmppNetworkException("Failed to establish XMPP connection", e);
            connectionReadyFuture.completeExceptionally(exception);
            throw exception;
        }
    }

    private record ConnectionTarget(String host, InetAddress address, int port) {

        static Optional<ConnectionTarget> of(InetAddress address, String host, int port) {
            if (address == null && (host == null || host.isEmpty())) {
                return Optional.empty();
            }
            return Optional.of(new ConnectionTarget(host, address, port));
        }

        static List<ConnectionTarget> fromSrvRecords(List<SrvRecord> records) {
            return records.stream()
                    .map(record -> new ConnectionTarget(normalizeHost(record.target()), null, record.port()))
                    .toList();
        }

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

        InetSocketAddress toSocketAddress() {
            return address != null
                    ? new InetSocketAddress(address, port)
                    : InetSocketAddress.createUnresolved(host, port);
        }
    }

    private List<ConnectionTarget> resolveConnectionTargets() {
        int basePort = config.isUsingDirectTLS()
                ? XmppConstants.DIRECT_TLS_PORT
                : XmppConstants.DEFAULT_XMPP_PORT;
        int port = config.getPort() > 0 ? config.getPort() : basePort;

        Optional<ConnectionTarget> configTarget = ConnectionTarget.of(config.getHostAddress(), config.getHost(), port);
        if (configTarget.isPresent()) {
            return List.of(configTarget.get());
        }

        List<SrvRecord> srvRecords = resolveSrvRecords(config.getXmppServiceDomain());
        if (!srvRecords.isEmpty()) {
            return ConnectionTarget.fromSrvRecords(srvRecords);
        }

        return ConnectionTarget.of(null, config.getXmppServiceDomain(), port)
                .map(List::of)
                .orElse(List.of());
    }

    private Bootstrap createBootstrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_RCVBUF, 64 * 1024);
        bootstrap.option(ChannelOption.SO_SNDBUF, 64 * 1024);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout());
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                if (config.isUsingDirectTLS()) {
                    String host = config.getHost() != null ? config.getHost() : config.getXmppServiceDomain();
                    int port = config.getPort();
                    try {
                        SslHandler sslHandler = SslUtils.createSslHandler(host, port, config);
                        ch.pipeline().addLast(sslHandler);
                    } catch (XmppNetworkException e) {
                        throw new IllegalStateException("Failed to initialize Direct TLS pipeline", e);
                    }
                }
                ch.pipeline().addLast(new XmppStreamDecoder());
                ch.pipeline().addLast(nettyHandler);
            }
        });
        return bootstrap;
    }

    private Optional<Channel> connectToTargets(Bootstrap bootstrap, List<ConnectionTarget> targets) {
        for (ConnectionTarget target : targets) {
            try {
                log.info("Connecting to {}...", target);
                Channel channel = ConnectionUtils.connectSync(bootstrap, target.toSocketAddress());
                log.info("Connected to {}", target);
                return Optional.of(channel);
            } catch (XmppNetworkException e) {
                log.warn("Connection failed to {}: {}", target, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private XmppException unwrapXmppException(Throwable cause) {
        if (cause instanceof XmppException xmppException) {
            return xmppException;
        }
        if (cause instanceof RuntimeException runtimeException
                && runtimeException.getCause() instanceof XmppException xmppException) {
            return xmppException;
        }
        return new XmppProtocolException("Unexpected error while establishing XMPP session", cause);
    }

    /**
     * 将当前连接生命周期标记为就绪。
     *
     * <p>该方法会完成 {@code connectionReadyFuture}，用于唤醒同步 {@link #connect()}
     * 和异步 {@link #connectAsync()} 的等待方，表明资源绑定与会话激活已经完成。</p>
     */
    public void markConnectionReady() {
        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    /**
     * 使用异常结束当前连接生命周期。
     *
     * @param exception 失败原因
     */
    public void failConnection(Exception exception) {
        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone()) {
            future.completeExceptionally(exception);
        }
        notifyConnectionClosedOnError(exception);
    }

    private List<SrvRecord> resolveSrvRecords(String domain) {
        try (DnsResolver resolver = new DnsResolver()) {
            return resolver.resolveXmppService(domain);
        } catch (XmppDnsException e) {
            log.error("DNS SRV lookup failed: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 关闭当前会话并释放相关资源。
     */
    @Override
    public void disconnect() {
        if (pingManager != null) {
            pingManager.stopKeepAlive();
        }

        if (reconnectionManager != null) {
            reconnectionManager.disable();
        }

        cleanupCollectors();

        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new XmppNetworkException("Connection closed before session became ready"));
        }

        if (channel != null) {
            channel.close();
        }

        shutdownWorkerGroup();
    }

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
     * 判断底层通道是否仍处于活动状态。
     *
     * @return 如果通道仍然活跃则返回 {@code true}
     */
    @Override
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * 判断当前连接是否已经完成认证。
     *
     * @return 如果连接已完成认证则返回 {@code true}
     */
    @Override
    public boolean isAuthenticated() {
        return nettyHandler != null && nettyHandler.isAuthenticated();
    }

    /**
     * 在通道可用时发送 XMPP stanza。
     *
     * @param stanza 待发送的协议元素
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
     * 获取当前连接绑定的客户端配置。
     *
     * @return 当前连接配置
     */
    @Override
    public XmppClientConfig getConfig() {
        return config;
    }

    /**
     * 重置底层 Netty 处理器的状态机。
     */
    @Override
    public void resetHandlerState() {
        if (nettyHandler != null) {
            nettyHandler.resetState();
        }
    }
}
