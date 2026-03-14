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
import io.netty.channel.ChannelFuture;
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

    private volatile boolean disconnectRequested;

    /**
     * 创建 TCP XMPP 连接。
     *
     * @param config 客户端配置
     * @throws IllegalArgumentException 如果 {@code config} 为 {@code null}
     */
    public XmppTcpConnection(XmppClientConfig config) {
        this.config = Objects.requireNonNull(config, "XmppClientConfig must not be null");
        registerIqRequestHandler(new PingIqRequestHandler());
    }

    /**
     * 同步建立连接并等待会话就绪。
     *
     * <p>该方法会等待 TCP 建连、认证、资源绑定和会话激活全部完成。</p>
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
     * <p>返回的 Future 在资源绑定完成且会话进入可用状态后结束。</p>
     *
     * @return 当前会话的就绪 Future
     * @throws XmppException 如果连接目标解析或连接初始化失败
     */
    public synchronized CompletableFuture<Void> connectAsync() throws XmppException {
        CompletableFuture<Void> currentFuture = connectionReadyFuture;
        Channel currentChannel = channel;
        if (currentFuture != null && !currentFuture.isCompletedExceptionally() && !currentFuture.isCancelled()) {
            if (currentChannel != null && currentChannel.isActive()) {
                return currentFuture;
            }
            if (!currentFuture.isDone() && workerGroup != null) {
                return currentFuture;
            }
        }
        if (currentFuture != null && (currentFuture.isCompletedExceptionally() || currentFuture.isCancelled())
                && (currentChannel != null && currentChannel.isActive() || workerGroup != null)) {
            disconnect();
        }

        resetConnectionLifecycleEvents();
        disconnectRequested = false;
        connectionReadyFuture = new CompletableFuture<>();
        workerGroup = new NioEventLoopGroup();
        nettyHandler = new XmppNettyHandler(config, this);

        List<ConnectionTarget> targets = resolveConnectionTargets();
        if (targets.isEmpty()) {
            XmppNetworkException exception = new XmppNetworkException("No connection targets available");
            connectionReadyFuture.completeExceptionally(exception);
            shutdownLifecycleManagers();
            shutdownWorkerGroup();
            throw exception;
        }

        initializeLifecycleManagersIfNeeded();

        try {
            this.channel = connectToTargets(targets)
                    .orElseThrow(() -> new XmppNetworkException("All connection attempts failed"));
            return connectionReadyFuture;
        } catch (XmppException e) {
            connectionReadyFuture.completeExceptionally(e);
            shutdownLifecycleManagers();
            shutdownWorkerGroup();
            throw e;
        } catch (RuntimeException e) {
            XmppNetworkException exception = new XmppNetworkException("Failed to establish XMPP connection", e);
            connectionReadyFuture.completeExceptionally(exception);
            shutdownLifecycleManagers();
            shutdownWorkerGroup();
            throw exception;
        }
    }

    private void initializeLifecycleManagersIfNeeded() {
        if (config.isPingEnabled() && pingManager == null) {
            pingManager = new PingManager(this);
        }
        if (config.isReconnectionEnabled() && reconnectionManager == null) {
            reconnectionManager = new ReconnectionManager(this);
        }
    }

    private void shutdownLifecycleManagers() {
        if (pingManager != null) {
            pingManager.shutdown();
            pingManager = null;
        }
        if (reconnectionManager != null) {
            reconnectionManager.shutdown();
            reconnectionManager = null;
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

    private Bootstrap createBootstrap(ConnectionTarget target) {
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
                    String tlsPeerHost = resolveTlsPeerHost(target);
                    try {
                        SslHandler sslHandler = SslUtils.createSslHandler(tlsPeerHost, target.port(), config);
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

    private String resolveTlsPeerHost(ConnectionTarget target) {
        if (target.host() != null && !target.host().isBlank()) {
            return target.host();
        }
        if (config.getHost() != null && !config.getHost().isBlank()) {
            return config.getHost();
        }
        return config.getXmppServiceDomain();
    }

    private Optional<Channel> connectToTargets(List<ConnectionTarget> targets) {
        for (ConnectionTarget target : targets) {
            try {
                log.info("Connecting to {}...", target);
                Bootstrap bootstrap = createBootstrap(target);
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
    public synchronized void markConnectionReady() {
        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    /**
     * 使用异常结束当前连接生命周期。
     *
     * <p>该方法会使连接就绪 Future 失败，并向连接事件总线发布错误事件。</p>
     *
     * @param exception 失败原因
     */
    public synchronized void failConnection(Exception exception) {
        disconnectRequested = false;
        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone()) {
            future.completeExceptionally(exception);
        }
        failPendingCollectors(exception);
        notifyConnectionClosedOnError(exception);
    }

    /**
     * 处理底层通道关闭后的资源收尾。
     *
     * <p>该方法在 Netty 通知通道失活时调用，用于释放旧通道和事件循环资源，
     * 并确保所有等待中的请求立即失败，而不是悬挂到超时。</p>
     */
    public synchronized void handleChannelInactive() {
        boolean manualDisconnect = disconnectRequested;
        disconnectRequested = false;

        XmppNetworkException closeException = new XmppNetworkException(
                manualDisconnect ? "Connection closed" : "Connection closed unexpectedly");
        failPendingCollectors(closeException);
        cleanupCollectors();

        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new XmppNetworkException(
                    manualDisconnect
                            ? "Connection closed before session became ready"
                            : "Connection closed unexpectedly before session became ready"));
        }

        channel = null;
        shutdownWorkerGroup();
        if (manualDisconnect) {
            notifyConnectionClosed();
        } else {
            notifyConnectionClosedOnError(closeException);
        }
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
     *
     * <p>该方法会停止心跳与重连逻辑、清理 collector，并关闭底层 Netty 资源。</p>
     */
    @Override
    public synchronized void disconnect() {
        disconnectRequested = true;
        shutdownLifecycleManagers();

        failPendingCollectors(new XmppNetworkException("Connection closed"));
        cleanupCollectors();

        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new XmppNetworkException("Connection closed before session became ready"));
        }

        if (channel != null) {
            channel.close();
            channel = null;
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
     * @param stanza 待发送的协议元素；如果为 {@code null} 或通道不可用，则仅记录日志
     */
    @Override
    public void sendStanza(XmppStanza stanza) {
        if (stanza == null) {
            log.warn("Stanza is null, ignoring send request");
            return;
        }

        dispatchStanza(stanza).whenComplete((unused, error) -> {
            if (error != null) {
                log.error("Failed to send stanza: {}", error.getMessage());
            }
        });
    }

    @Override
    protected CompletableFuture<Void> dispatchStanza(XmppStanza stanza) {
        if (stanza == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Stanza must not be null"));
        }

        Channel currentChannel = channel;
        if (currentChannel == null || !currentChannel.isActive()) {
            return CompletableFuture.failedFuture(new XmppNetworkException("Channel is not active"));
        }

        var context = currentChannel.pipeline().lastContext();
        if (context == null) {
            return CompletableFuture.failedFuture(new XmppNetworkException("Channel pipeline has no active context"));
        }

        ChannelFuture writeFuture = nettyHandler.sendStanza(context, stanza);
        if (writeFuture == null) {
            return CompletableFuture.failedFuture(new XmppNetworkException("Failed to serialize stanza for sending"));
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        writeFuture.addListener(future -> {
            if (future.isSuccess()) {
                result.complete(null);
            } else {
                Throwable cause = future.cause();
                result.completeExceptionally(new XmppNetworkException("Failed to send stanza", cause));
            }
        });
        return result;
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
