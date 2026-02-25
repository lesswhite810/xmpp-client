package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.StanzaListener;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.Iq;
import java.util.concurrent.CompletableFuture;

/**
 * XMPP 连接管理接口。
 *
 * <p>定义 XMPP 连接的基本操作，包括连接建立、断开、状态检查、消息发送等功能。</p>
 *
 * @since 2026-02-09
 */
public interface XmppConnection {

    /**
     * 建立到 XMPP 服务器的连接。
     *
     * @throws XmppNetworkException 如果网络连接失败
     * @throws XmppException        如果连接过程中发生其他错误
     */
    void connect() throws XmppException;

    /**
     * 断开与 XMPP 服务器的连接。
     */
    void disconnect();

    /**
     * 检查连接是否已建立。
     *
     * @return 如果连接已建立返回 true，否则返回 false
     */
    boolean isConnected();

    /**
     * 检查是否已完成身份认证。
     *
     * @return 如果已认证返回 true，否则返回 false
     */
    boolean isAuthenticated();

    /**
     * 发送 XMPP 节。
     *
     * @param stanza 要发送的节
     */
    void sendStanza(XmppStanza stanza);

    /**
     * 发送 IQ 请求并异步等待响应。
     *
     * @param iq 要发送的 IQ 节
     *
     * @return CompletableFuture，完成时包含响应节
     */
    CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq);

    /**
     * 添加异步节监听器。
     *
     * @param listener 节监听器
     * @param filter   节过滤器
     */
    void addAsyncStanzaListener(StanzaListener listener, StanzaFilter filter);

    /**
     * 移除节监听器。
     *
     * @param listener 要移除的监听器
     */
    void removeAsyncStanzaListener(StanzaListener listener);

    /**
     * 添加连接状态监听器。
     *
     * @param listener 连接监听器
     */
    void addConnectionListener(ConnectionListener listener);

    /**
     * 移除连接状态监听器。
     *
     * @param listener 要移除的监听器
     */
    void removeConnectionListener(ConnectionListener listener);

    /**
     * 获取连接配置。
     *
     * @return 客户端配置对象
     */
    XmppClientConfig getConfig();

    /**
     * 重置连接处理器状态。
     *
     * @since 2026-02-09
     */
    void resetHandlerState();
}
