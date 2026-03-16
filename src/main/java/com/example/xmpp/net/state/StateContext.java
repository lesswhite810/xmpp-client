package com.example.xmpp.net.state;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.net.XmppStreamDecoder;
import com.example.xmpp.protocol.model.XmlSerializable;
import com.example.xmpp.mechanism.SaslNegotiator;
import com.example.xmpp.util.NettyUtils;
import com.example.xmpp.util.XmlStringBuilder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

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
            return currentState.isSessionActive();
        }
    }

    /**
     * 生成唯一 ID。
     *
     * @param prefix ID 前缀
     * @return 唯一 ID
     */
    public String generateId(String prefix) {
        return prefix + "_" + UUID.randomUUID();
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
            if (currentState == null) {
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
        if (currentState == null) {
            log.debug("Ignoring inbound message because state context is cleared");
            return;
        }
        currentState.handleMessage(this, ctx, msg);
    }

    /**
     * 重置状态。
     *
     * <p>该方法通常在连接重建或处理器状态机重置时调用。</p>
     *
     * @param connectingState 新的初始连接状态
     */
    public void reset(XmppHandlerState connectingState) {
        this.currentState = connectingState;
        this.saslNegotiator = null;
    }

    /**
     * 清除状态上下文。
     *
     * <p>清除后会忽略后续状态切换与入站消息，避免旧连接上的异步回调继续推进状态机。</p>
     */
    public void terminate() {
        this.currentState = null;
        this.saslNegotiator = null;
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
     * @param cause 错误原因，可以是异常对象或错误描述
     */
    public void closeConnectionOnError(ChannelHandlerContext ctx, Object cause) {
        Exception exception = toException(cause);
        if (connection != null) {
            connection.failConnection(ctx.channel(), exception);
        }
        ctx.close();
    }

    private Exception toException(Object cause) {
        if (cause instanceof Exception exception) {
            if (exception instanceof XmppException xmppException) {
                return xmppException;
            }
            return new XmppException("Connection error: " + exception.getClass().getSimpleName(), exception);
        }
        if (cause instanceof Throwable throwable) {
            return new XmppException("Connection error: " + throwable.getClass().getSimpleName(), throwable);
        }
        return new XmppException(String.valueOf(cause));
    }

    /**
     * 打开 XMPP 流。
     *
     * <p>该方法会发送初始或重开后的 stream 头，用于进入功能协商阶段。</p>
     *
     * @param ctx 通道上下文
     */
    public ChannelFuture openStream(ChannelHandlerContext ctx) {
        XmlStringBuilder xml = new XmlStringBuilder(XmppConstants.DEFAULT_XML_BUILDER_CAPACITY);
        xml.append("<?xml version='1.0'?>");
        xml.element("stream", "stream", null)
           .attribute("to", config.getXmppServiceDomain())
           .attribute("xmlns", XmppConstants.NS_JABBER_CLIENT)
           .attribute("xmlns:stream", XmppConstants.NS_XMPP_STREAMS)
           .attribute("version", XmppConstants.XMPP_VERSION);
        if (config.getXmlLang() != null) {
            xml.attribute("xml:lang", config.getXmlLang());
        }
        xml.rightAngleBracket();

        return NettyUtils.writeAndFlushStringAsync(ctx, xml.toString());
    }

    /**
     * 重置解码器并打开流。
     *
     * <p>当前实现仅重新发送 stream 头，保留该方法用于后续扩展解码器重置逻辑。</p>
     *
     * @param ctx 通道上下文
     */
    public ChannelFuture openStreamAndResetDecoder(ChannelHandlerContext ctx) {
        return openStream(ctx);
    }
}
