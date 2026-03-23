package com.example.xmpp.net.state;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.protocol.model.XmlSerializable;
import com.example.xmpp.protocol.model.stream.StreamHeader;
import com.example.xmpp.mechanism.SaslNegotiator;
import com.example.xmpp.util.NettyUtils;
import com.example.xmpp.util.XmlStringBuilder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 状态上下文。
 *
 * @since 2026-02-20
 */
@Slf4j
@Getter
public class StateContext {

    private final XmppClientConfig config;

    private final XmppTcpConnection connection;

    private volatile XmppHandlerState currentState = XmppHandlerState.INITIAL;

    private volatile boolean terminated;

    @Setter
    private SaslNegotiator saslNegotiator;

    /**
     * 创建状态上下文。
     *
     * @param config 客户端配置
     * @param connection 连接引用
     * @param ctx Netty 通道上下文
     */
    public StateContext(XmppClientConfig config, XmppTcpConnection connection, ChannelHandlerContext ctx) {
        this.config = config;
        this.connection = connection;
        transitionTo(XmppHandlerState.CONNECTING, ctx);
    }

    /**
     * 检查是否已认证。
     *
     * @return 是否已认证
     */
    public boolean isAuthenticated() {
        synchronized (stateLock) {
            return !terminated && currentState.isSessionActive();
        }
    }

    private final Object stateLock = new Object();

    /**
     * 转换到新状态。
     *
     * @param newState 新状态
     * @param ctx 通道上下文
     * @throws IllegalStateException 如果状态转换不合法
     */
    public void transitionTo(XmppHandlerState newState, ChannelHandlerContext ctx) {
        synchronized (stateLock) {
            if (terminated) {
                log.debug("Ignoring transition to {} because state context is cleared", newState);
                return;
            }
            if (currentState == newState) {
                return;
            }

            doTransition(newState, ctx);
        }
    }

    /**
     * 获取当前状态名称。
     *
     * @return 当前状态名称
     */
    public String getCurrentStateName() {
        return currentState.getName();
    }

    /**
     * 处理消息。
     *
     * @param ctx Netty 通道上下文
     * @param msg 接收到的消息
     */
    public void handleMessage(ChannelHandlerContext ctx, Object msg) {
        if (terminated) {
            log.debug("Ignoring inbound message because state context is cleared");
            return;
        }
        currentState.handleMessage(this, ctx, msg);
    }

    /**
     * 清除状态上下文。
     */
    public void invalidate() {
        this.saslNegotiator = null;
        this.terminated = true;
    }

    /**
     * 发送 Stanza 到服务器。
     *
     * @param ctx 通道上下文
     * @param packet 要发送的对象
     * @return 写出 future；如果发送前校验失败则返回失败 future
     */
    public ChannelFuture sendStanza(ChannelHandlerContext ctx, Object packet) {
        if (packet == null) {
            return createFailedSendFuture(ctx, "Packet must not be null");
        }
        if (!(packet instanceof XmlSerializable serializable)) {
            log.warn("Unknown packet type: {}", packet.getClass().getName());
            return createFailedSendFuture(ctx, "Unsupported packet type");
        }
        String xmlStr = serializable.toXml();
        if (StringUtils.isBlank(xmlStr)) {
            return createFailedSendFuture(ctx, "Failed to serialize stanza for sending");
        }
        return NettyUtils.writeAndFlushStringAsync(ctx, xmlStr);
    }

    /**
     * 关闭连接并记录错误。
     *
     * @param ctx 通道上下文
     * @param exception XMPP 异常
     */
    public void closeConnectionOnError(ChannelHandlerContext ctx, XmppException exception) {
        if (connection != null) {
            connection.failConnection(ctx.channel(), exception);
        }
        ctx.close().addListener(future -> {
            if (!future.isSuccess()) {
                log.debug("Failed to close channel after error: {}", future.cause().getMessage());
            }
        });
    }

    /**
     * 使用固定错误文案关闭连接。
     *
     * @param ctx 通道上下文
     * @param message 错误文案
     */
    public void closeConnectionOnError(ChannelHandlerContext ctx, String message) {
        closeConnectionOnError(ctx, new XmppException(message));
    }

    /**
     * 打开 XMPP 流。
     *
     * @param ctx 通道上下文
     * @return 写出 future
     */
    public ChannelFuture openStream(ChannelHandlerContext ctx) {
        StreamHeader streamHeader = StreamHeader.builder()
                .to(config.getXmppServiceDomain())
                .version(XmppConstants.XMPP_VERSION)
                .lang(config.getXmlLang())
                .namespace(XmppConstants.NS_JABBER_CLIENT)
                .build();

        XmlStringBuilder xml = new XmlStringBuilder(XmppConstants.DEFAULT_XML_BUILDER_CAPACITY);
        xml.append("<?xml version='1.0' encoding='UTF-8'?>")
                .append(streamHeader.toXml());

        return NettyUtils.writeAndFlushStringAsync(ctx, xml.toString());
    }

    /**
     * 在 TLS 握手成功后恢复 XMPP 协议流程。
     *
     * <p>该方法会重新打开 XMPP 流，并在写成功后推进到等待服务端 features 的状态。</p>
     *
     * @param ctx 通道上下文
     */
    public void resumeAfterTlsHandshake(ChannelHandlerContext ctx) {
        ChannelFuture openStreamFuture = openStream(ctx);
        if (openStreamFuture == null) {
            closeConnectionOnError(ctx, new XmppException("Failed to reopen stream after TLS handshake"));
            return;
        }

        openStreamFuture.addListener(result -> {
            if (!result.isSuccess()) {
                closeConnectionOnError(ctx,
                        new XmppNetworkException("Failed to reopen stream after TLS handshake", result.cause()));
                return;
            }
            if (terminated) {
                log.debug("Skipping post-handshake state transition because state context is cleared");
                return;
            }
            if (!ctx.channel().isActive()) {
                log.debug("Skipping post-handshake state transition because channel is inactive");
                return;
            }
            transitionToIfCurrentState(XmppHandlerState.CONNECTING, XmppHandlerState.AWAITING_FEATURES, ctx);
        });
    }

    private void doTransition(XmppHandlerState newState, ChannelHandlerContext ctx) {
        currentState.validateTransition(newState);

        String connectionId = config.getXmppServiceDomain();
        log.debug("[{}] State transition: {} -> {}", connectionId, currentState.name(), newState.name());

        currentState.onExit(this, ctx);
        currentState = newState;
        newState.onEnter(this, ctx);
    }

    private void transitionToIfCurrentState(XmppHandlerState expectedState, XmppHandlerState newState,
                                            ChannelHandlerContext ctx) {
        synchronized (stateLock) {
            if (terminated) {
                log.debug("Ignoring transition to {} because state context is cleared", newState);
                return;
            }
            if (currentState != expectedState) {
                log.debug("Skipping transition to {} because current state is {}, expected {}",
                        newState, currentState, expectedState);
                return;
            }
            if (currentState == newState) {
                return;
            }

            doTransition(newState, ctx);
        }
    }

    private ChannelFuture createFailedSendFuture(ChannelHandlerContext ctx, String message) {
        return ctx.newFailedFuture(new XmppNetworkException(message));
    }

}
