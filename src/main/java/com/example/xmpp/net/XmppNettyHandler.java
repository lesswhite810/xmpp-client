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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

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
     */
    public void resetState() {
        log.debug("Resetting handler state");
        if (stateContext != null) {
            stateContext.reset(XmppHandlerState.CONNECTING);
        }
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

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Channel active - Remote: {}", ctx.channel().remoteAddress());
        initStateContext(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel inactive - Connection closed");
        connection.notifyConnectionClosed();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception caught - State: {}, Remote: {}, Error: {}",
                stateContext != null ? stateContext.getCurrentStateName() : "unknown",
                ctx.channel().remoteAddress(),
                cause.getMessage(),
                cause);

        Exception connectionException = cause instanceof XmppException xmppException
                ? xmppException
                : new XmppException("Connection error: " + cause.getClass().getSimpleName(), cause);
        connection.failConnection(connectionException);
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof StreamHeader) {
            log.debug("Stream header received, waiting for features");
            return;
        }

        if (msg instanceof StreamError streamError) {
            log.error("Received stream error - condition: {}, text: {}",
                    streamError.getCondition(), streamError.getText());
            connection.failConnection(new com.example.xmpp.exception.XmppStreamErrorException(streamError));
            ctx.close();
            return;
        }

        stateContext.handleMessage(ctx, msg);
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
        if (event.isSuccess()) {
            if (stateContext == null) {
                log.error("StateContext is null during SSL handshake completion");
                ctx.close();
                return;
            }
            log.debug("SSL handshake completed successfully");
            stateContext.openStreamAndResetDecoder(ctx);
            stateContext.transitionTo(XmppHandlerState.AWAITING_FEATURES, ctx);
        } else {
            log.error("SSL handshake failed: ", event.cause());
            connection.failConnection(new XmppException("SSL handshake failed", event.cause()));
            ctx.close();
        }
    }

    /**
     * 向服务器发送可序列化的 XMPP 报文。
     *
     * @param ctx Netty 通道上下文
     * @param packet 待发送的数据包
     */
    public void sendStanza(ChannelHandlerContext ctx, Object packet) {
        Optional.ofNullable(packet)
                .filter(XmlSerializable.class::isInstance)
                .map(XmlSerializable.class::cast)
                .map(XmlSerializable::toXml)
                .filter(xml -> !xml.isEmpty())
                .ifPresent(xml -> {
                    log.debug("Sending stanza: {}", SecurityUtils.filterSensitiveXml(xml));
                    NettyUtils.writeAndFlushString(ctx, xml);
                });
    }
}
