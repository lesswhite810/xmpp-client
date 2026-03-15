package com.example.xmpp.net;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.net.state.StateContext;
import com.example.xmpp.net.state.XmppHandlerState;
import com.example.xmpp.protocol.model.XmlSerializable;
import com.example.xmpp.protocol.model.stream.StreamError;
import com.example.xmpp.protocol.model.stream.StreamHeader;
import com.example.xmpp.util.NettyUtils;
import com.example.xmpp.util.SecurityUtils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.concurrent.Future;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.io.IOException;

/**
 * XMPP Netty 入站处理器。
 *
 * <p>负责衔接 Netty 事件与 XMPP 状态机，包括连接激活、入站消息分发、SSL 握手结果处理、
 * 异常传播以及协议报文发送。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
@RequiredArgsConstructor
public class XmppNettyHandler extends SimpleChannelInboundHandler<Object> {

    private volatile StateContext stateContext;

    @NonNull
    private final XmppClientConfig config;

    @NonNull
    private final XmppTcpConnection connection;

    /**
     * 初始化当前通道对应的状态上下文。
     *
     * @param ctx Netty 通道上下文
     */
    private void initStateContext(ChannelHandlerContext ctx) {
        this.stateContext = new StateContext(config, connection, ctx);
    }

    /**
     * 重置处理器状态机。
     *
     * <p>该方法用于连接重建或复用连接对象时，将状态机恢复到初始连接阶段。</p>
     */
    public void resetState() {
        log.debug("Resetting handler state");
        if (stateContext != null) {
            stateContext.reset(XmppHandlerState.CONNECTING);
        }
    }

    /**
     * 清空当前处理器状态。
     *
     * <p>用于连接关闭后的状态回收，确保外部不会继续读取到旧会话状态。</p>
     */
    public void clearState() {
        StateContext currentStateContext = stateContext;
        if (currentStateContext != null) {
            currentStateContext.terminate();
        }
        stateContext = null;
    }

    /**
     * 判断连接是否已经完成认证。
     *
     * @return 如果已进入已认证状态则返回 {@code true}
     */
    public boolean isAuthenticated() {
        StateContext ctx = this.stateContext;
        return ctx != null && ctx.isAuthenticated();
    }

    /**
     * 判断当前通道是否已经不属于当前连接生命周期。
     *
     * @param ctx Netty 通道上下文
     * @return 如果通道已陈旧或连接正在关闭则返回 {@code true}
     */
    private boolean isStaleChannel(ChannelHandlerContext ctx) {
        return !connection.isCurrentChannel(ctx.channel());
    }

    /**
     * 获取当前状态名称。
     *
     * @param currentStateContext 当前状态上下文
     * @return 当前状态名称；如果不可用则返回 {@code unknown}
     */
    private String resolveStateName(StateContext currentStateContext) {
        return currentStateContext != null ? currentStateContext.getCurrentStateName() : "unknown";
    }

    /**
     * 判断状态上下文是否已经终止。
     *
     * @param currentStateContext 当前状态上下文
     * @param ctx Netty 通道上下文
     * @return 如果状态上下文不可用或通道已关闭则返回 {@code true}
     */
    private boolean isTerminated(StateContext currentStateContext, ChannelHandlerContext ctx) {
        return currentStateContext == null || currentStateContext.isTerminated() || !ctx.channel().isActive();
    }

    /**
     * 记录异常日志。
     *
     * @param stateName 当前状态名称
     * @param remoteAddress 远端地址
     * @param cause 捕获到的异常
     * @param terminated 当前生命周期是否已终止
     */
    private void logCaughtException(String stateName, Object remoteAddress, Throwable cause, boolean terminated) {
        if (terminated) {
            log.debug("Ignoring exception for terminated channel - State: {}, Remote: {}, Error: {}",
                    stateName, remoteAddress, cause.getMessage());
            return;
        }

        if (cause instanceof XmppException) {
            log.warn("XMPP exception caught - State: {}, Remote: {}, Error: {}",
                    stateName, remoteAddress, cause.getMessage());
            log.debug("XMPP exception detail", cause);
            return;
        }

        if (cause instanceof IOException) {
            log.warn("I/O exception caught - State: {}, Remote: {}, Error: {}",
                    stateName, remoteAddress, cause.getMessage());
            log.debug("I/O exception detail", cause);
            return;
        }

        log.error("Exception caught - State: {}, Remote: {}, Error: {}",
                stateName, remoteAddress, cause.getMessage(), cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!connection.bindActiveChannel(ctx.channel())) {
            log.debug("Ignoring channelActive for stale or closing channel: {}", ctx.channel());
            ctx.close();
            return;
        }
        log.info("Channel active - Remote: {}", ctx.channel().remoteAddress());
        initStateContext(ctx);
        connection.notifyConnected();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (isStaleChannel(ctx)) {
            log.debug("Ignoring channelInactive from stale channel: {}", ctx.channel());
            super.channelInactive(ctx);
            return;
        }
        if (connection.hasPublishedTerminalConnectionEvent()) {
            log.debug("Channel inactive after terminal event was already published");
        } else {
            log.info("Channel inactive - Connection closed");
        }
        connection.handleChannelInactive(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isStaleChannel(ctx)) {
            log.debug("Ignoring exception from stale channel - Remote: {}, Error: {}",
                    ctx.channel().remoteAddress(), cause.getMessage());
            ctx.close();
            return;
        }
        StateContext currentStateContext = stateContext;
        String stateName = resolveStateName(currentStateContext);
        Object remoteAddress = ctx.channel().remoteAddress();
        boolean terminated = isTerminated(currentStateContext, ctx);

        logCaughtException(stateName, remoteAddress, cause, terminated);

        Exception connectionException = cause instanceof XmppException xmppException
                ? xmppException
                : new XmppException("Connection error: " + cause.getClass().getSimpleName(), cause);
        connection.failConnection(ctx.channel(), connectionException);
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (isStaleChannel(ctx)) {
            log.debug("Ignoring inbound {} from stale channel", msg.getClass().getSimpleName());
            return;
        }

        StateContext currentStateContext = stateContext;
        if (currentStateContext == null) {
            log.debug("Ignoring inbound {} because state context has been cleared", msg.getClass().getSimpleName());
            return;
        }

        handleInboundMessage(ctx, msg, currentStateContext);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent sslEvent) {
            handleSslHandshakeComplete(ctx, sslEvent);
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * 处理 SSL 握手完成事件。
     *
     * @param ctx Netty 通道上下文
     * @param event SSL 握手完成事件
     */
    private void handleSslHandshakeComplete(ChannelHandlerContext ctx, SslHandshakeCompletionEvent event) {
        if (isStaleChannel(ctx)) {
            log.debug("Ignoring SSL handshake event from stale channel: {}", ctx.channel());
            return;
        }
        if (!event.isSuccess()) {
            handleTlsHandshakeFailure(ctx, event);
            return;
        }
        handleTlsHandshakeSuccess(ctx);
    }

    private void handleInboundMessage(ChannelHandlerContext ctx, Object msg, StateContext currentStateContext) {
        if (msg instanceof StreamHeader) {
            log.debug("Stream header received, waiting for features");
            return;
        }
        if (msg instanceof StreamError streamError) {
            handleStreamError(ctx, streamError);
            return;
        }
        currentStateContext.handleMessage(ctx, msg);
    }

    private void handleStreamError(ChannelHandlerContext ctx, StreamError streamError) {
        log.warn("Received stream error - condition: {}, text: {}",
                streamError.getCondition(), streamError.getText());
        connection.failConnection(ctx.channel(), new com.example.xmpp.exception.XmppStreamErrorException(streamError));
        ctx.close();
    }

    private void handleTlsHandshakeSuccess(ChannelHandlerContext ctx) {
        StateContext currentStateContext = stateContext;
        if (currentStateContext == null) {
            log.debug("Ignoring SSL handshake completion because state context has been cleared");
            return;
        }
        if (currentStateContext.isTerminated()) {
            log.debug("Ignoring SSL handshake completion because state context is terminated");
            return;
        }
        log.debug("SSL handshake completed successfully");
        ChannelFuture openStreamFuture = currentStateContext.openStreamAndResetDecoder(ctx);
        if (openStreamFuture == null) {
            failTlsHandshakeRecovery(ctx, null);
            return;
        }
        openStreamFuture.addListener(result -> handleTlsReopenResult(ctx, currentStateContext, result));
    }

    private void handleTlsReopenResult(ChannelHandlerContext ctx, StateContext currentStateContext, Future<? super Void> result) {
        if (!result.isSuccess()) {
            failTlsHandshakeRecovery(ctx, result.cause());
            return;
        }
        if (!ctx.channel().isActive()) {
            log.debug("Skipping post-handshake state transition because channel is inactive");
            return;
        }
        currentStateContext.transitionTo(XmppHandlerState.AWAITING_FEATURES, ctx);
    }

    private void failTlsHandshakeRecovery(ChannelHandlerContext ctx, Throwable cause) {
        XmppException exception = cause == null
                ? new XmppException("Failed to reopen stream after TLS handshake")
                : new XmppException("Failed to reopen stream after TLS handshake", cause);
        connection.failConnection(ctx.channel(), exception);
        ctx.close();
    }

    private void handleTlsHandshakeFailure(ChannelHandlerContext ctx, SslHandshakeCompletionEvent event) {
        log.warn("SSL handshake failed: {}", event.cause() != null ? event.cause().getMessage() : "unknown");
        log.debug("SSL handshake failure detail", event.cause());
        connection.failConnection(ctx.channel(), new XmppException("SSL handshake failed", event.cause()));
        ctx.close();
    }

    /**
     * 向服务器发送可序列化的 XMPP 报文。
     *
     * @param ctx Netty 通道上下文
     * @param packet 待发送的数据包
     */
    public ChannelFuture sendStanza(ChannelHandlerContext ctx, Object packet) {
        return Optional.ofNullable(packet)
                .filter(XmlSerializable.class::isInstance)
                .map(XmlSerializable.class::cast)
                .map(XmlSerializable::toXml)
                .filter(xml -> !xml.isEmpty())
                .map(xml -> {
                    log.debug("Sending stanza: {}", SecurityUtils.filterSensitiveXml(xml));
                    return NettyUtils.writeAndFlushStringAsync(ctx, xml);
                })
                .orElse(null);
    }
}
