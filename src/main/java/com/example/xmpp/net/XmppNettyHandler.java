package com.example.xmpp.net;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.exception.XmppStreamErrorException;
import com.example.xmpp.net.state.StateContext;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.XmlSerializable;
import com.example.xmpp.protocol.model.stream.StreamError;
import com.example.xmpp.protocol.model.stream.StreamHeader;
import com.example.xmpp.util.NettyUtils;
import com.example.xmpp.util.SecurityUtils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * XMPP Netty 入站处理器。
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
     * 清空当前处理器状态。
     */
    public void invalidateStateContext() {
        StateContext currentStateContext = stateContext;
        if (currentStateContext != null) {
            currentStateContext.invalidate();
        }
        stateContext = null;
    }

    /**
     * 判断连接是否已经完成认证。
     *
     * @return 是否已认证
     */
    public boolean isAuthenticated() {
        StateContext ctx = this.stateContext;
        return ctx != null && ctx.isAuthenticated();
    }

    /**
     * 判断当前通道是否已经不属于当前连接生命周期。
     *
     * @param ctx Netty 通道上下文
     * @return 是否为陈旧通道
     */
    private boolean isStaleChannel(ChannelHandlerContext ctx) {
        return !connection.isCurrentChannel(ctx.channel());
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
        String errorType = cause.getClass().getSimpleName();
        if (terminated) {
            log.debug("Ignoring exception for terminated channel - State: {}, Remote: {}, ErrorType: {}",
                    stateName, remoteAddress, errorType);
            return;
        }

        if (cause instanceof XmppException) {
            log.warn("XMPP exception caught - State: {}, Remote: {}, ErrorType: {}",
                    stateName, remoteAddress, errorType);
            return;
        }

        if (cause instanceof IOException) {
            log.warn("I/O exception caught - State: {}, Remote: {}, ErrorType: {}",
                    stateName, remoteAddress, errorType);
            return;
        }

        log.error("Exception caught - State: {}, Remote: {}, ErrorType: {}",
                stateName, remoteAddress, errorType);
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
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (isStaleChannel(ctx)) {
            log.debug("Ignoring channelInactive from stale channel: {}", ctx.channel());
            super.channelInactive(ctx);
            return;
        }
        log.info("Channel inactive - Connection closed");
        connection.handleChannelInactive(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isStaleChannel(ctx)) {
            log.debug("Ignoring exception from stale channel - Remote: {}, ErrorType: {}",
                    ctx.channel().remoteAddress(), cause.getClass().getSimpleName());
            ctx.close();
            return;
        }
        StateContext currentStateContext = stateContext;
        String stateName = currentStateContext != null ? currentStateContext.getCurrentStateName() : "unknown";
        Object remoteAddress = ctx.channel().remoteAddress();
        boolean terminated = currentStateContext == null
                || currentStateContext.isTerminated()
                || !ctx.channel().isActive();

        logCaughtException(stateName, remoteAddress, cause, terminated);

        XmppException connectionException = toConnectionException(cause);
        connection.failConnection(ctx.channel(), connectionException);
        ctx.close();
    }

    private XmppException toConnectionException(Throwable cause) {
        if (cause instanceof XmppException xmppException) {
            return xmppException;
        }
        if (cause instanceof IOException) {
            return new XmppNetworkException("I/O error");
        }
        return new XmppException("Connection error: " + cause.getClass().getSimpleName());
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

        if (msg instanceof StreamHeader) {
            log.debug("Stream header received, waiting for features");
            return;
        }
        if (msg instanceof StreamError streamError) {
            log.warn("Received stream error - condition: {}",
                    streamError.getCondition());
            connection.failConnection(ctx.channel(), new XmppStreamErrorException(streamError));
            ctx.close();
            return;
        }
        currentStateContext.handleMessage(ctx, msg);
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
        currentStateContext.resumeAfterTlsHandshake(ctx);
    }

    private void handleTlsHandshakeFailure(ChannelHandlerContext ctx, SslHandshakeCompletionEvent event) {
        Throwable cause = event.cause();
        log.warn("SSL handshake failed - Remote: {}, ErrorType: {}",
                ctx.channel().remoteAddress(),
                cause != null ? cause.getClass().getSimpleName() : "unknown");
        connection.failConnection(ctx.channel(), new XmppNetworkException("SSL handshake failed"));
        ctx.close();
    }

    /**
     * 向服务器发送可序列化的 XMPP 报文。
     *
     * @param ctx Netty 通道上下文
     * @param packet 待发送的数据包
     * @return 写出 future，或 null
     */
    public ChannelFuture sendStanza(ChannelHandlerContext ctx, Object packet) {
        return Optional.ofNullable(packet)
                .filter(XmlSerializable.class::isInstance)
                .map(XmlSerializable.class::cast)
                .map(serializable -> {
                    String xml = serializable.toXml();
                    if (StringUtils.isEmpty(xml)) {
                        return null;
                    }
                    if (packet instanceof XmppStanza stanza) {
                        log.debug("Sending stanza: {}", SecurityUtils.summarizeStanza(stanza));
                    } else {
                        log.debug("Sending stanza: {}", serializable.getClass().getSimpleName());
                    }
                    return NettyUtils.writeAndFlushStringAsync(ctx, xml);
                })
                .filter(Objects::nonNull)
                .orElse(null);
    }
}
