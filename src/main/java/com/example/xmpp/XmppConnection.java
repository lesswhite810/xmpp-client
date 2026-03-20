package com.example.xmpp;

import com.example.xmpp.handler.IqRequestHandler;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.protocol.AsyncStanzaCollector;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.Iq;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * XMPP 连接接口。
 *
 * @since 2026-02-09
 */
public interface XmppConnection {

    /**
     * 建立到 XMPP 服务器的连接。
     *
     * @throws XmppNetworkException 网络连接失败
     * @throws XmppAuthException 认证失败
     * @throws XmppException 其他 XMPP 错误
     */
    void connect() throws XmppException;

    /**
     * 断开与 XMPP 服务器的连接。
     */
    void disconnect();

    /**
     * 检查连接是否已建立。
     *
     * @return 如果连接已建立并激活返回 true，否则返回 false
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
     * @param stanza 要发送的节，不能为 null
     */
    void sendStanza(XmppStanza stanza);

    /**
     * 发送 IQ 请求并异步等待响应。
     *
     * @param iq 要发送的 IQ 节，不能为 null，必须包含非空 ID
     * @return 完成时包含响应节的 Future
     * @throws IllegalArgumentException 如果 iq 为 null 或 ID 为空
     */
    CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq);

    /**
     * 发送 IQ 请求并在指定超时时间内异步等待响应。
     *
     * @param iq 要发送的 IQ 节，不能为 null，必须包含非空 ID
     * @param timeout 超时时间，必须大于 0
     * @param unit 超时时间单位，不能为 null
     * @return 完成时包含响应节的 Future
     * @throws IllegalArgumentException 如果 iq 为 null、ID 为空、timeout 非法或 unit 为 null
     */
    CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq, long timeout, TimeUnit unit);

    /**
     * 注册 IQ 请求处理器。
     *
     * @param handler IQ 请求处理器，不能为 null
     * @throws IllegalArgumentException 如果已存在相同处理器
     */
    void registerIqRequestHandler(IqRequestHandler handler);

    /**
     * 注销 IQ 请求处理器。
     *
     * @param handler 要注销的处理器，不能为 null
     * @return 如果处理器存在并被移除返回 true，否则返回 false
     */
    boolean unregisterIqRequestHandler(IqRequestHandler handler);

    /**
     * 获取连接配置。
     *
     * @return 客户端配置对象，永不为 null
     */
    XmppClientConfig getConfig();

    /**
     * 创建节收集器。
     *
     * @param filter 节过滤器，不能为 null
     * @return 新创建的收集器
     */
    AsyncStanzaCollector createStanzaCollector(StanzaFilter filter);

    /**
     * 移除节收集器。
     *
     * @param collector 要移除的收集器，不能为 null
     * @return 如果收集器存在并被移除返回 true
     */
    boolean removeStanzaCollector(AsyncStanzaCollector collector);
}
