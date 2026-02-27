package com.example.xmpp;

import com.example.xmpp.event.ConnectionListener;
import com.example.xmpp.handler.IqRequestHandler;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
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
     * @throws XmppException 如果连接过程中发生错误（如网络失败、认证失败等）
     */
    void connect() throws XmppException;

    /**
     * 断开与 XMPP 服务器的连接。
     *
     * <p>释放连接相关资源，关闭网络通道。</p>
     */
    void disconnect();

    /**
     * 检查连接是否已建立。
     *
     * @return 如果连接已建立返回 {@code true}，否则返回 {@code false}
     */
    boolean isConnected();

    /**
     * 检查是否已完成身份认证。
     *
     * @return 如果已认证返回 {@code true}，否则返回 {@code false}
     */
    boolean isAuthenticated();

    /**
     * 发送 XMPP 节。
     *
     * <p>如果连接未激活或节为 {@code null}，此方法静默返回。</p>
     *
     * @param stanza 要发送的节
     */
    void sendStanza(XmppStanza stanza);

    /**
     * 发送 IQ 请求并异步等待响应。
     *
     * @param iq 要发送的 IQ 节
     * @return {@link CompletableFuture}，完成时包含响应节
     */
    CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq);

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
     * 注册 IQ 请求处理器。
     *
     * <p>当收到匹配的 IQ 请求时，将调用处理器的
     * {@link IqRequestHandler#handleIqRequest(Iq)} 方法。</p>
     *
     * @param handler IQ 请求处理器
     * @throws IllegalArgumentException 如果已存在相同 (element, namespace, iqType) 的处理器
     * @since 2026-02-26
     */
    void registerIqRequestHandler(IqRequestHandler handler);

    /**
     * 注销 IQ 请求处理器。
     *
     * @param handler 要注销的处理器
     * @return 如果处理器存在并被移除返回 {@code true}，否则返回 {@code false}
     * @since 2026-02-26
     */
    boolean unregisterIqRequestHandler(IqRequestHandler handler);

    /**
     * 获取连接配置。
     *
     * @return 客户端配置对象
     */
    XmppClientConfig getConfig();

    /**
     * 重置连接处理器状态。
     */
    void resetHandlerState();
}
