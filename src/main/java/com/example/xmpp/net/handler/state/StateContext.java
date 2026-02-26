package com.example.xmpp.net.handler.state;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.net.XmppStreamDecoder;
import com.example.xmpp.protocol.model.XmlSerializable;
import com.example.xmpp.sasl.SaslNegotiator;
import com.example.xmpp.util.ExceptionUtils;
import com.example.xmpp.util.NettyUtils;
import com.example.xmpp.util.XmlStringBuilder;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 状态上下文（状态模式）。
 *
 * <p>持有处理器状态和共享数据，提供状态转换方法。</p>
 *
 * @since 2026-02-20
 */
@Getter
public class StateContext {

    private static final Logger log = LoggerFactory.getLogger(StateContext.class);

    /** 客户端配置 */
    private final XmppClientConfig config;

    /** 连接引用 */
    private final XmppTcpConnection connection;

    /** 当前状态 */
    private volatile XmppHandlerState currentState = XmppHandlerState.INITIAL;

    /** SASL 协商器 */
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
        // 初始化到 CONNECTING 状态
        transitionTo(XmppHandlerState.CONNECTING, ctx);
    }

    /**
     * 检查是否已认证。
     *
     * @return 如果已认证返回 true
     */
    public boolean isAuthenticated() {
        return currentState.isSessionActive();
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

    /** 状态转换锁，保护状态转换的原子性 */
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
            if (currentState == newState) {
                return;
            }

            // 验证状态转换
            currentState.validateTransition(newState);

            // 记录状态转换日志
            String connectionId = config.getXmppServiceDomain();
            log.debug("[{}] State transition: {} -> {}", connectionId, currentState.name(), newState.name());

            // 调用退出和进入回调
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
        currentState.handleMessage(this, ctx, msg);
    }

    /**
     * 重置状态。
     *
     * @param connectingState 连接状态实例
     */
    public void reset(XmppHandlerState connectingState) {
        this.currentState = connectingState;
        this.saslNegotiator = null;
    }

    // --- 公共方法 ---

    /**
     * 发送 Stanza 到服务器。
     *
     * @param ctx    通道上下文
     * @param packet 要发送的对象
     */
    public void sendStanza(ChannelHandlerContext ctx, Object packet) {
        if (packet instanceof XmlSerializable serializable) {
            String xmlStr = serializable.toXml().toString();
            if (!xmlStr.isEmpty()) {
                NettyUtils.writeAndFlushString(ctx, xmlStr);
            }
        } else {
            log.warn("Unknown packet type: {}", packet != null ? packet.getClass().getName() : "null");
        }
    }

    /**
     * 关闭连接并记录错误。
     *
     * @param ctx   通道上下文
     * @param cause 错误原因
     */
    public void closeConnectionOnError(ChannelHandlerContext ctx, Object cause) {
        if (connection != null) {
            connection.fireConnectionClosedOnError(ExceptionUtils.toException(cause));
        }
        ctx.close();
    }

    /**
     * 打开 XMPP 流。
     *
     * @param ctx 通道上下文
     */
    public void openStream(ChannelHandlerContext ctx) {
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

        NettyUtils.writeAndFlushString(ctx, xml.toString());
    }

    /**
     * 重置解码器并打开流。
     *
     * @param ctx 通道上下文
     */
    public void openStreamAndResetDecoder(ChannelHandlerContext ctx) {
        openStream(ctx);
    }
}
