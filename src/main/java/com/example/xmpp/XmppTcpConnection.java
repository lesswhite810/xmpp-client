package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.exception.XmppProtocolException;
import com.example.xmpp.handler.PingIqRequestHandler;
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
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 TCP 的 XMPP 连接实现。
 *
 * @since 2026-02-09
 */
@Slf4j
@Getter
public class XmppTcpConnection extends AbstractXmppConnection {

    private enum TerminalEventState {
        NONE,
        CLOSED_ONLY,
        ERROR_AND_CLOSED
    }

    private final XmppClientConfig config;

    private EventLoopGroup workerGroup;

    private Channel channel;

    private XmppNettyHandler nettyHandler;

    private volatile CompletableFuture<Void> connectionReadyFuture = new CompletableFuture<>();

    private final AtomicReference<XmppNetworkException> pipelineInitializationFailure = new AtomicReference<>();

    private TerminalEventState terminalEventState = TerminalEventState.NONE;

    /**
     * 创建 TCP XMPP 连接。
     *
     * @param config 客户端配置
     * @throws IllegalArgumentException 如果 config 为 null
     */
    public XmppTcpConnection(XmppClientConfig config) {
        this.config = Objects.requireNonNull(config, "XmppClientConfig must not be null");
        registerIqRequestHandler(new PingIqRequestHandler());
    }

    /**
     * 同步建立连接。
     *
     * @throws XmppException 如果连接失败
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
     * 异步建立连接。
     *
     * @return 当前会话的就绪 Future
     * @throws XmppException 如果连接初始化失败
     */
    public synchronized CompletableFuture<Void> connectAsync() throws XmppException {
        CompletableFuture<Void> reusableFuture = findReusableReadyFuture();
        if (reusableFuture != null) {
            return reusableFuture;
        }

        List<ConnectionTarget> targets = resolveConnectionTargets();
        if (targets.isEmpty()) {
            XmppNetworkException exception = new XmppNetworkException("No connection targets available");
            connectionReadyFuture.completeExceptionally(exception);
            shutdownWorkerGroup();
            throw exception;
        }

        resetForNewConnectionAttempt();
        try {
            initializeConnectionInfrastructure();
            this.channel = connectToTargets(targets);
            return connectionReadyFuture;
        } catch (XmppException e) {
            failConnectionAttempt(e);
            throw e;
        } catch (RuntimeException e) {
            XmppNetworkException exception = new XmppNetworkException("Failed to establish XMPP connection", e);
            failConnectionAttempt(exception);
            throw exception;
        }
    }

    private CompletableFuture<Void> findReusableReadyFuture() {
        CompletableFuture<Void> currentFuture = connectionReadyFuture;
        Channel currentChannel = channel;
        if (currentFuture == null || currentFuture.isCompletedExceptionally() || currentFuture.isCancelled()) {
            return null;
        }
        if (currentChannel != null && currentChannel.isActive()) {
            return currentFuture;
        }
        if (!currentFuture.isDone() && workerGroup != null) {
            return currentFuture;
        }
        return null;
    }

    private void resetForNewConnectionAttempt() {
        Channel previousChannel = channel;
        channel = null;
        clearHandlerState();
        EventLoopGroup previousWorkerGroup = workerGroup;
        workerGroup = null;
        closeChannel(previousChannel);
        shutdownWorkerGroup(previousWorkerGroup);
        terminalEventState = TerminalEventState.NONE;
        connectionReadyFuture = new CompletableFuture<>();
    }

    private void failConnectionAttempt(Exception exception) {
        connectionReadyFuture.completeExceptionally(exception);
        shutdownWorkerGroup();
    }

    private void initializeConnectionInfrastructure() {
        workerGroup = new NioEventLoopGroup();
        nettyHandler = new XmppNettyHandler(config, this);
    }

    private record ConnectionTarget(String host, InetAddress address, int port) {

        static Optional<ConnectionTarget> of(InetAddress address, String host, int port) {
            if (address == null && StringUtils.isBlank(host)) {
                return Optional.empty();
            }
            return Optional.of(new ConnectionTarget(host, address, port));
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
        int port = config.getPort();
        return ConnectionTarget.of(config.getHostAddress(), config.getHost(), port)
                .or(() -> ConnectionTarget.of(null, config.getXmppServiceDomain(), port))
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
                    try {
                        SslHandler sslHandler = SslUtils.createSslHandler(config);
                        ch.pipeline().addLast(sslHandler);
                    } catch (XmppNetworkException e) {
                        pipelineInitializationFailure.set(e);
                        throw new IllegalStateException("Failed to initialize Direct TLS pipeline");
                    }
                }
                ch.pipeline().addLast(new XmppStreamDecoder());
                ch.pipeline().addLast(nettyHandler);
            }
        });
        return bootstrap;
    }

    private Channel connectToTargets(List<ConnectionTarget> targets) throws XmppNetworkException {
        XmppNetworkException primaryFailure = null;
        for (ConnectionTarget target : targets) {
            try {
                return attemptConnectionTarget(target);
            } catch (XmppNetworkException e) {
                log.warn("Connection failed for target {} - ErrorType: {}", target, e.getClass().getSimpleName());
                if (primaryFailure == null) {
                    primaryFailure = e;
                } else {
                    primaryFailure.addSuppressed(e);
                }
            }
        }
        if (primaryFailure != null) {
            throw primaryFailure;
        }
        throw new XmppNetworkException("All connection attempts failed");
    }

    private Channel attemptConnectionTarget(ConnectionTarget target) throws XmppNetworkException {
        pipelineInitializationFailure.set(null);
        try {
            log.debug("Attempting connection target {}", target);
            Channel connectedChannel = ConnectionUtils.connectSync(createBootstrap(), target.toSocketAddress());
            log.debug("Connection target {} established", target);
            return connectedChannel;
        } catch (XmppNetworkException e) {
            XmppNetworkException initializationFailure = pipelineInitializationFailure.getAndSet(null);
            if (initializationFailure != null) {
                throw initializationFailure;
            }
            throw e;
        }
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
     * 将当前会话标记为就绪。
     */
    public synchronized void markConnectionReady() {
        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone() && future.complete(null)) {
            notifyConnected();
        }
    }

    /**
     * 以异常结束当前连接。
     *
     * @param exception 失败原因
     */
    public synchronized void failConnection(Exception exception) {
        failReadyFuture(exception);
        failPendingCollectors(exception);
        clearHandlerState();
        publishTerminalError(exception);
        closeCurrentChannelOrShutdownWorkerGroup();
    }

    /**
     * 仅处理当前活动通道上的失败事件。
     *
     * @param sourceChannel 触发失败的通道
     * @param exception 失败原因
     */
    public synchronized void failConnection(Channel sourceChannel, Exception exception) {
        if (!isCurrentChannel(sourceChannel)) {
            log.debug("Ignoring failConnection from stale channel: {}", sourceChannel);
            return;
        }
        failConnection(exception);
    }

    /**
     * 仅处理当前活动通道上的失活事件。
     *
     * @param inactiveChannel 失活的通道
     */
    public synchronized void handleChannelInactive(Channel inactiveChannel) {
        if (!isCurrentChannel(inactiveChannel)) {
            log.debug("Ignoring inactive event from stale channel: {}", inactiveChannel);
            return;
        }

        boolean closedByClient = terminalEventState == TerminalEventState.CLOSED_ONLY;
        XmppNetworkException closeException = new XmppNetworkException(
                closedByClient ? "Connection closed" : "Connection closed unexpectedly");
        failPendingCollectors(closeException);
        cleanupCollectors();
        failReadyFuture(new XmppNetworkException(
                closedByClient
                        ? "Connection closed before session became ready"
                        : "Connection closed unexpectedly before session became ready"));

        channel = null;
        clearHandlerState();
        shutdownWorkerGroup();
        if (closedByClient) {
            publishClosedEvent(null);
            return;
        }
        publishTerminalError(closeException);
    }

    /**
     * 判断通道是否属于当前连接。
     *
     * @param candidate 待检查的通道
     * @return 如果是当前活动通道则返回 true
     */
    public synchronized boolean isCurrentChannel(Channel candidate) {
        return candidate != null && candidate == channel;
    }

    /**
     * 将新激活的通道绑定到当前连接。
     *
     * @param candidate 待绑定的活动通道
     * @return 如果绑定成功或通道本就属于当前连接则返回 true
     */
    public synchronized boolean bindActiveChannel(Channel candidate) {
        if (candidate == null) {
            return false;
        }
        if (terminalEventState != TerminalEventState.NONE) {
            return false;
        }
        if (channel == null) {
            channel = candidate;
            return true;
        }
        return channel == candidate;
    }

    /**
     * 关闭当前连接并释放资源。
     */
    @Override
    public synchronized void disconnect() {
        failPendingCollectors(new XmppNetworkException("Connection closed"));
        cleanupCollectors();
        failReadyFuture(new XmppNetworkException("Connection closed before session became ready"));
        clearHandlerState();
        publishClosedEvent(null);
        closeCurrentChannelOrShutdownWorkerGroup();
    }

    private void failReadyFuture(Exception exception) {
        CompletableFuture<Void> future = connectionReadyFuture;
        if (future != null && !future.isDone()) {
            future.completeExceptionally(exception);
        }
    }

    private void clearHandlerState() {
        if (nettyHandler != null) {
            nettyHandler.invalidateStateContext();
        }
    }

    private void publishTerminalError(Exception exception) {
        if (terminalEventState != TerminalEventState.NONE) {
            return;
        }
        terminalEventState = TerminalEventState.ERROR_AND_CLOSED;
        notifyConnectionClosedOnError(exception);
        notifyConnectionClosed(exception);
    }

    private void publishClosedEvent(Exception exception) {
        if (terminalEventState != TerminalEventState.NONE) {
            return;
        }
        terminalEventState = TerminalEventState.CLOSED_ONLY;
        if (exception == null) {
            notifyConnectionClosed();
            return;
        }
        notifyConnectionClosed(exception);
    }

    private void closeCurrentChannelOrShutdownWorkerGroup() {
        Channel currentChannel = channel;
        if (currentChannel == null) {
            shutdownWorkerGroup();
            return;
        }
        if (currentChannel.isActive()) {
            currentChannel.close();
            return;
        }
        channel = null;
        shutdownWorkerGroup();
    }

    private void closeChannel(Channel currentChannel) {
        if (currentChannel != null && currentChannel.isOpen()) {
            currentChannel.close();
        }
    }

    private void shutdownWorkerGroup() {
        EventLoopGroup currentWorkerGroup = workerGroup;
        workerGroup = null;
        shutdownWorkerGroup(currentWorkerGroup);
    }

    private void shutdownWorkerGroup(EventLoopGroup group) {
        if (group == null) {
            return;
        }
        group.shutdownGracefully(
                XmppConstants.SHUTDOWN_QUIET_PERIOD_SECONDS,
                XmppConstants.SHUTDOWN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * 判断底层通道是否仍处于活动状态。
     *
     * @return 如果通道仍然活跃则返回 true
     */
    @Override
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * 判断当前连接是否已经完成认证。
     *
     * @return 如果连接已完成认证则返回 true
     */
    @Override
    public boolean isAuthenticated() {
        return nettyHandler != null && nettyHandler.isAuthenticated();
    }

    /**
     * 发送 XMPP stanza。
     *
     * @param stanza 待发送的协议元素
     */
    @Override
    public void sendStanza(XmppStanza stanza) {
        if (stanza == null) {
            log.warn("Stanza is null, ignoring send request");
            return;
        }

        dispatchStanza(stanza).whenComplete((unused, error) -> {
            if (error != null) {
                logSendFailure(error);
            }
        });
    }

    private void logSendFailure(Throwable error) {
        Throwable cause = unwrapCompletionError(error);
        if (cause instanceof XmppNetworkException) {
            log.warn("Failed to send stanza: {}", cause.getMessage());
            return;
        }
        log.error("Failed to send stanza: {}", cause.getMessage(), cause);
    }

    private Throwable unwrapCompletionError(Throwable error) {
        if (error instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return error;
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
     * 获取当前连接配置。
     *
     * @return 当前连接配置
     */
    @Override
    public XmppClientConfig getConfig() {
        return config;
    }

}
