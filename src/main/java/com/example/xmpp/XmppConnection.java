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

/**
 * XMPP 连接管理接口。
 *
 * <p>定义 XMPP 连接的基本操作，包括连接建立、断开、状态检查、消息发送等功能。</p>
 *
 * <p>实现类应确保线程安全，支持并发访问。</p>
 *
 * @since 2026-02-09
 */
public interface XmppConnection {

    /**
     * 建立到 XMPP 服务器的连接。
     *
     * <p>根据配置解析服务器地址，建立 TCP 连接，完成 TLS 协商（如果需要），
     * 并进行 SASL 认证和资源绑定。</p>
     *
     * @throws XmppNetworkException 如果网络连接失败
     * @throws XmppAuthException 如果认证失败
     * @throws XmppException 如果发生其他 XMPP 相关错误
     */
    void connect() throws XmppException;

    /**
     * 断开与 XMPP 服务器的连接。
     *
     * <p>释放连接相关资源并触发底层网络通道关闭。
     * 关闭过程可能在底层 I/O 线程中异步完成，因此该方法本身不承诺阻塞到通道完全关闭。</p>
     */
    void disconnect();

    /**
     * 检查连接是否已建立。
     *
     * <p>注意：此方法仅检查 TCP 连接状态，不保证 XMPP 流已打开。</p>
     *
     * @return 如果连接已建立并激活返回 {@code true}，否则返回 {@code false}
     */
    boolean isConnected();

    /**
     * 检查是否已完成身份认证。
     *
     * <p>只有当 SASL 认证成功且资源绑定完成后才返回 true。</p>
     *
     * @return 如果已认证返回 {@code true}，否则返回 {@code false}
     */
    boolean isAuthenticated();

    /**
     * 发送 XMPP 节。
     *
     * <p>实现类会尽力发送该节。
     * 如果节为 {@code null} 或连接不可用，通常会拒绝发送并记录日志；该方法本身不向调用方返回发送结果。</p>
     *
     * @param stanza 要发送的节，不能为 {@code null}
     */
    void sendStanza(XmppStanza stanza);

    /**
     * 发送 IQ 请求并异步等待响应。
     *
     * <p>调用方需要为 IQ 节预先设置非空 ID。
     * 该方法会等待服务器返回 RESULT 或 ERROR 类型的响应。</p>
     *
     * @param iq 要发送的 IQ 节，不能为 {@code null}，必须包含非空 ID
     * @return {@link CompletableFuture}，完成时包含响应节；
     *         如果超时或出错则 exceptionally 完成
     * @throws IllegalArgumentException 如果 iq 为 null 或 ID 为空
     */
    CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq);

    /**
     * 发送 IQ 请求并在指定超时时间内异步等待响应。
     *
     * <p>调用方需要为 IQ 节预先设置非空 ID。
     * 该方法会等待服务器返回 RESULT 或 ERROR 类型的响应。</p>
     *
     * @param iq 要发送的 IQ 节，不能为 {@code null}，必须包含非空 ID
     * @param timeout 超时时间，必须大于 0
     * @param unit 超时时间单位，不能为 {@code null}
     * @return {@link CompletableFuture}，完成时包含响应节；
     *         如果超时或出错则 exceptionally 完成
     * @throws IllegalArgumentException 如果 iq 为 null、ID 为空、timeout 非法或 unit 为 null
     */
    CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq, long timeout, java.util.concurrent.TimeUnit unit);

    /**
     * 注册 IQ 请求处理器。
     *
     * <p>当收到匹配的 IQ 请求时，将调用处理器的
     * {@link IqRequestHandler#handleIqRequest(Iq)} 方法。</p>
     *
     * <p>每个连接最多只能注册一个相同 (element, namespace, iqType) 组合的处理器。</p>
     *
     * @param handler IQ 请求处理器，不能为 {@code null}
     * @throws IllegalArgumentException 如果已存在相同 (element, namespace, iqType) 的处理器
     */
    void registerIqRequestHandler(IqRequestHandler handler);

    /**
     * 注销 IQ 请求处理器。
     *
     * <p>如果处理器不存在，此方法不会抛出异常。</p>
     *
     * @param handler 要注销的处理器，不能为 {@code null}
     * @return 如果处理器存在并被移除返回 {@code true}，否则返回 {@code false}
     */
    boolean unregisterIqRequestHandler(IqRequestHandler handler);

    /**
     * 获取连接配置。
     *
     * @return 客户端配置对象，永不为 {@code null}
     */
    XmppClientConfig getConfig();

    /**
     * 创建节收集器。
     *
     * <p>创建一个带有指定过滤器的异步节收集器，用于等待匹配的响应节。
     * 收集器会被自动注册到连接的收集器队列中，以便接收传入的节。</p>
     *
     * @param filter 节过滤器，不能为 {@code null}
     * @return 新创建的收集器
     */
    AsyncStanzaCollector createStanzaCollector(StanzaFilter filter);

    /**
     * 移除节收集器。
     *
     * <p>从连接的收集器队列中移除指定的收集器。</p>
     *
     * @param collector 要移除的收集器，不能为 {@code null}
     * @return 如果收集器存在并被移除返回 {@code true}
     */
    boolean removeStanzaCollector(AsyncStanzaCollector collector);
}
