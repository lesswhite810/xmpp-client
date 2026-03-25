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

    private volatile XmppHandlerState currentState = XmppHandlerState.CONNECTING;

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
        startConnectionFlow(ctx);
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
        synchronized (stateLock) {
            return currentState.getName();
        }
    }

    /**
     * 处理消息。
     *
     * @param ctx Netty 通道上下文
     * @param msg 接收到的消息
     */
    public void handleMessage(ChannelHandlerContext ctx, Object msg) {
        dispatchWithStateLock("Ignoring inbound message because state context is cleared",
                state -> state.handleMessage(this, ctx, msg));
    }

    /**
     * 处理用户事件。
     *
     * @param ctx Netty 通道上下文
     * @param evt 用户事件
     */
    public void handleUserEvent(ChannelHandlerContext ctx, Object evt) {
        dispatchWithStateLock("Ignoring user event because state context is cleared",
                state -> state.handleUserEvent(this, ctx, evt));
    }

    /**
     * 清除状态上下文。
     */
    public void invalidate() {
        synchronized (stateLock) {
            this.saslNegotiator = null;
            this.terminated = true;
        }
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
                Throwable cause = future.cause();
                log.error("Failed to close channel after error - ErrorType: {}",
                        cause != null ? cause.getClass().getSimpleName() : "unknown");
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

    private void doTransition(XmppHandlerState newState, ChannelHandlerContext ctx) {
        currentState.validateTransition(newState);

        String connectionId = config.getXmppServiceDomain();
        log.info("[{}] State transition: {} -> {}", connectionId, currentState.name(), newState.name());

        currentState = newState;
    }

    private void dispatchWithStateLock(String terminatedMessage, StateDispatcher dispatcher) {
        synchronized (stateLock) {
            if (terminated) {
                log.debug(terminatedMessage);
                return;
            }
            dispatcher.dispatch(currentState);
        }
    }

    private void startConnectionFlow(ChannelHandlerContext ctx) {
        if (config.isUsingDirectTLS()) {
            log.info("Using Direct TLS, waiting for SSL handshake to complete");
            return;
        }
        log.info("Opening initial XMPP stream");
        transitionBeforeWrite(XmppHandlerState.AWAITING_FEATURES,
                ctx,
                "open initial XMPP stream",
                () -> openStream(ctx));
    }

    <E extends Exception> void transitionBeforeWrite(XmppHandlerState nextState,
                                                     ChannelHandlerContext ctx,
                                                     String action,
                                                     CheckedWriteAction<E> writeAction) throws E {
        transitionTo(nextState, ctx);
        failOnWriteFailure(ctx, writeAction.execute(), action);
    }

    void transitionAfterSuccessfulWrite(ChannelHandlerContext ctx,
                                        ChannelFuture future,
                                        String action,
                                        Runnable onSuccess) {
        if (future == null) {
            closeConnectionOnError(ctx, "Failed to " + action);
            return;
        }
        future.addListener(result -> {
            if (!result.isSuccess()) {
                closeConnectionOnError(ctx,
                        new XmppNetworkException("Failed to " + action, result.cause()));
                return;
            }
            if (terminated) {
                log.debug("Skipping state follow-up for {} because state context is cleared", action);
                return;
            }
            if (!ctx.channel().isActive()) {
                log.debug("Skipping state follow-up for {} because channel is inactive", action);
                return;
            }
            onSuccess.run();
        });
    }

    void failOnWriteFailure(ChannelHandlerContext ctx, ChannelFuture future, String action) {
        if (future == null) {
            closeConnectionOnError(ctx, "Failed to " + action);
            return;
        }
        future.addListener(result -> {
            if (!result.isSuccess()) {
                closeConnectionOnError(ctx,
                        new XmppNetworkException("Failed to " + action, result.cause()));
            }
        });
    }

    void activateSession(ChannelHandlerContext ctx) {
        transitionTo(XmppHandlerState.SESSION_ACTIVE, ctx);
        connection.markConnectionReady();
        connection.notifyAuthenticated();
    }

    @FunctionalInterface
    interface CheckedWriteAction<E extends Exception> {
        ChannelFuture execute() throws E;
    }

    @FunctionalInterface
    private interface StateDispatcher {
        void dispatch(XmppHandlerState state);
    }

    private ChannelFuture createFailedSendFuture(ChannelHandlerContext ctx, String message) {
        return ctx.newFailedFuture(new XmppNetworkException(message));
    }

}
