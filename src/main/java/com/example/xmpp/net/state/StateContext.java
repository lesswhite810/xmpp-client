package com.example.xmpp.net.state;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
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

/**
 * 状态上下文（状态模式）。
 *
 * <p>持有处理器状态和共享数据，为各状态实现提供状态切换、报文发送和错误关闭等公共能力。</p>
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
     * @param config     客户端配置
     * @param connection 连接引用
     * @param ctx        Netty 通道上下文
     */
    public StateContext(XmppClientConfig config, XmppTcpConnection connection, ChannelHandlerContext ctx) {
        this.config = config;
        this.connection = connection;
        transitionTo(XmppHandlerState.CONNECTING, ctx);
    }

    /**
     * 检查是否已认证。
     *
     * <p>使用同步块保护，确保线程安全。</p>
     *
     * @return 如果已认证返回 true
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
     * @param ctx      通道上下文
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

            currentState.validateTransition(newState);

            String connectionId = config.getXmppServiceDomain();
            log.debug("[{}] State transition: {} -> {}", connectionId, currentState.name(), newState.name());

            currentState.onExit(this, ctx);
            currentState = newState;
            newState.onEnter(this, ctx);
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
     *
     * <p>清除后会忽略后续状态切换与入站消息，避免旧连接上的异步回调继续推进状态机。</p>
     */
    public void invalidate() {
        this.saslNegotiator = null;
        this.terminated = true;
    }

    /**
     * 发送 Stanza 到服务器。
     *
     * @param ctx    通道上下文
     * @param packet 要发送的对象；仅支持实现了 {@link XmlSerializable} 的协议对象
     */
    public ChannelFuture sendStanza(ChannelHandlerContext ctx, Object packet) {
        if (packet instanceof XmlSerializable serializable) {
            String xmlStr = serializable.toXml();
            if (!xmlStr.isEmpty()) {
                return NettyUtils.writeAndFlushStringAsync(ctx, xmlStr);
            }
        } else {
            log.warn("Unknown packet type: {}", packet != null ? packet.getClass().getName() : "null");
        }
        return null;
    }

    /**
     * 关闭连接并记录错误。
     *
     * @param ctx   通道上下文
     * @param exception XMPP 异常
     */
    public void closeConnectionOnError(ChannelHandlerContext ctx, XmppException exception) {
        if (connection != null) {
            connection.failConnection(ctx.channel(), exception);
        }
        ctx.close();
    }

    /**
     * 使用固定错误文案关闭连接。
     *
     * @param ctx     通道上下文
     * @param message 安全的固定错误文案
     */
    public void closeConnectionOnError(ChannelHandlerContext ctx, String message) {
        closeConnectionOnError(ctx, new XmppException(message));
    }

    /**
     * 打开 XMPP 流。
     *
     * <p>该方法会发送初始或重开后的 stream 头，用于进入功能协商阶段。</p>
     *
     * @param ctx 通道上下文
     */
    public ChannelFuture openStream(ChannelHandlerContext ctx) {
        StreamHeader streamHeader = StreamHeader.builder()
                .to(config.getXmppServiceDomain())
                .version(XmppConstants.XMPP_VERSION)
                .lang(config.getXmlLang())
                .namespace(XmppConstants.NS_JABBER_CLIENT)
                .build();

        XmlStringBuilder xml = new XmlStringBuilder(XmppConstants.DEFAULT_XML_BUILDER_CAPACITY);
        xml.append("<?xml version='1.0'?>")
                .append(streamHeader.toXml());

        return NettyUtils.writeAndFlushStringAsync(ctx, xml.toString());
    }

}
