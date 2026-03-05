package com.example.xmpp;

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
     * @throws com.example.xmpp.exception.XmppNetworkException 如果网络连接失败
     * @throws com.example.xmpp.exception.XmppAuthException 如果认证失败
     * @throws com.example.xmpp.exception.XmppException 如果发生其他 XMPP 相关错误
     */
    void connect() throws XmppException;

    /**
     * 断开与 XMPP 服务器的连接。
     *
     * <p>释放连接相关资源，关闭网络通道。
     * 此方法会阻塞直到连接完全关闭。</p>
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
     * <p>如果连接未激活或节为 {@code null}，此方法静默返回。
     * 具体的发送失败行为由实现类决定（可能抛出异常或记录日志）。</p>
     *
     * @param stanza 要发送的节，不能为 {@code null}
     */
    void sendStanza(XmppStanza stanza);

    /**
     * 发送 IQ 请求并异步等待响应。
     *
     * <p>此方法会自动设置 IQ 节的 ID（如果未设置），
     * 并等待服务器返回 RESULT 或 ERROR 类型的响应。</p>
     *
     * @param iq 要发送的 IQ 节，不能为 {@code null}，必须包含非空 ID
     * @return {@link CompletableFuture}，完成时包含响应节；
     *         如果超时或出错则 exceptionally 完成
     * @throws IllegalArgumentException 如果 iq 为 null 或 ID 为空
     */
    CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq);

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
     * 重置连接处理器状态。
     *
     * <p>用于重连场景，清除内部状态以便重新开始连接流程。</p>
     */
    void resetHandlerState();
}
