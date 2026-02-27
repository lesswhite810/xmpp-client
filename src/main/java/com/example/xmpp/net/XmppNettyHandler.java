package com.example.xmpp.net;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.net.handler.state.StateContext;
import com.example.xmpp.net.handler.state.XmppHandlerState;
import com.example.xmpp.protocol.model.XmlSerializable;
import com.example.xmpp.protocol.model.stream.StreamHeader;
import com.example.xmpp.util.NettyUtils;
import com.example.xmpp.util.SecurityUtils;

import java.util.Optional;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * XMPP 协议处理器（状态模式重构版）。
 *
 * <p>使用状态模式将 XMPP 状态机的逻辑分散到各个状态类中，提高代码可读性和可维护性。</p>
 *
 * <h2>状态流转</h2>
 * <ol>
 *   <li>CONNECTING - 初始连接状态</li>
 *   <li>AWAITING_FEATURES - 等待流特性（可用于多个阶段）</li>
 *   <li>TLS_NEGOTIATING - TLS 协商和握手中</li>
 *   <li>SASL_AUTH - SASL 认证中</li>
 *   <li>BINDING - 资源绑定中</li>
 *   <li>SESSION_ACTIVE - 会话已建立</li>
 * </ol>
 *
 * <h2>状态模式优势</h2>
 * <ul>
 *   <li>消除冗长的 switch-case 语句</li>
 *   <li>每个状态的逻辑独立封装，易于理解</li>
 *   <li>新增状态只需添加新类，符合开闭原则</li>
 *   <li>状态转换逻辑集中管理</li>
 * </ul>
 *
 * @since 2026-02-09
 */
@Slf4j
@RequiredArgsConstructor
public class XmppNettyHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * 状态上下文（状态模式）
     */
    private StateContext stateContext;

    /**
     * 客户端配置，不可变
     */
    @NonNull
    private final XmppClientConfig config;

    /**
     * 连接引用，用于回调
     */
    @NonNull
    private final XmppTcpConnection connection;

    /**
     * 初始化状态上下文。
     *
     * @param ctx Netty 通道上下文
     */
    private void initStateContext(ChannelHandlerContext ctx) {
        // 构造函数中已初始化状态到 CONNECTING
        this.stateContext = new StateContext(config, connection, ctx);
    }

    /**
     * 重置状态。
     *
     * <p>当连接断开后重新连接时调用此方法。下次 channelActive 时会正确初始化状态。</p>
     */
    public void resetState() {
        log.debug("Resetting handler state");
        if (stateContext != null) {
            stateContext.reset(XmppHandlerState.CONNECTING);
        }
    }

    /**
     * 检查连接是否已认证。
     *
     * @return 如果已认证并处于 SESSION_ACTIVE 状态则返回 true
     */
    public boolean isAuthenticated() {
        return stateContext != null && stateContext.isAuthenticated();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception caught - State: {}, Remote: {}, Error: {}",
                stateContext != null ? stateContext.getCurrentStateName() : "unknown",
                ctx.channel().remoteAddress(),
                cause.getMessage(),
                cause);

        connection.notifyConnectionClosedOnError(
                new XmppException("Connection error: " + cause.getClass().getSimpleName()));
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof StreamHeader) {
            log.debug("Stream header received, waiting for features");
            return;
        }

        // 状态模式：委托给当前状态处理
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
            connection.notifyConnectionClosedOnError(
                    new XmppException("SSL handshake failed"));
            ctx.close();
        }
    }

    /**
     * 发送 Stanza 到服务器。
     *
     * @param ctx Netty 通道上下文
     * @param packet 要发送的数据包
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
