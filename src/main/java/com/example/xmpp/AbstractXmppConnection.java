package com.example.xmpp;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.handler.IqRequestHandler;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.AsyncStanzaCollector;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.XmppStanza;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * XMPP 连接抽象基类。
 *
 * <p>提供 IQ 请求发送等公共功能。连接事件通过 {@link XmppEventBus} 发布。</p>
 *
 * <p>子类需要实现以下方法：
 * <ul>
 *   <li>{@link #connect()} - 建立连接</li>
 *   <li>{@link #disconnect()} - 断开连接</li>
 *   <li>{@link #isConnected()} - 检查连接状态</li>
 *   <li>{@link #isAuthenticated()} - 检查认证状态</li>
 *   <li>{@link #sendStanza(XmppStanza)} - 发送节</li>
 *   <li>{@link #getConfig()} - 获取配置</li>
 *   <li>{@link #resetHandlerState()} - 重置状态</li>
 * </ul>
 * </p>
 *
 * @since 2026-02-09
 */
@Slf4j
public abstract class AbstractXmppConnection implements XmppConnection {

    private static final long DEFAULT_IQ_TIMEOUT_MS = XmppConstants.DEFAULT_IQ_TIMEOUT_MS;

    /**
     * 节收集器队列
     */
    protected final Queue<AsyncStanzaCollector> collectors = new ConcurrentLinkedQueue<>();

    /**
     * IQ 请求处理器映射表，键为 (element, namespace, iqType) 组合
     */
    private final Map<IqHandlerKey, IqRequestHandler> iqRequestHandlers = new ConcurrentHashMap<>();

    /**
     * 注册 IQ 请求处理器。
     *
     * <p>将处理器添加到内部映射表中，用于处理收到的 IQ 请求。
     * 同一连接只能注册一个相同键的处理器。</p>
     *
     * @param handler IQ 请求处理器，不能为 {@code null}
     * @throws IllegalArgumentException 如果 handler 为 null，或已存在相同键的处理器
     */
    @Override
    public void registerIqRequestHandler(IqRequestHandler handler) {
        Validate.notNull(handler, "Handler must not be null");
        IqHandlerKey key = new IqHandlerKey(
                handler.getElement(),
                handler.getNamespace(),
                handler.getIqType()
        );
        IqRequestHandler existing = iqRequestHandlers.putIfAbsent(key, handler);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "IQ request handler already registered for: " + key);
        }
        log.debug("Registered IQ request handler: {} -> {}", key, handler.getClass().getSimpleName());
    }

    /**
     * 注销 IQ 请求处理器。
     *
     * <p>从内部映射表中移除指定的处理器。如果处理器不存在，不做任何事情。</p>
     *
     * @param handler 要注销的处理器，不能为 {@code null}
     * @return 如果处理器存在并被移除返回 {@code true}
     * @throws IllegalArgumentException 如果 handler 为 null
     */
    @Override
    public boolean unregisterIqRequestHandler(IqRequestHandler handler) {
        Validate.notNull(handler, "Handler must not be null");
        IqHandlerKey key = new IqHandlerKey(
                handler.getElement(),
                handler.getNamespace(),
                handler.getIqType()
        );
        boolean removed = iqRequestHandlers.remove(key, handler);
        if (removed) {
            log.debug("Unregistered IQ request handler: {}", key);
        }
        return removed;
    }

    /**
     * 查找并处理 IQ 请求。
     *
     * <p>根据 IQ 的子元素和类型查找匹配的处理器并调用。
     * 仅处理 GET 和 SET 类型的 IQ 请求。</p>
     *
     * @param iq IQ 请求节，不能为 {@code null}
     * @return 如果找到处理器并处理返回 {@code true}；否则返回 {@code false}
     */
    public boolean handleIqRequest(Iq iq) {
        if (iq.getType() != Iq.Type.GET && iq.getType() != Iq.Type.SET) {
            return false;
        }

        Object childElement = iq.getChildElement();
        if (childElement == null) {
            return false;
        }

        String elementName = getChildElementName(childElement);
        String namespace = getChildElementNamespace(childElement);

        if (elementName == null || namespace == null) {
            return false;
        }

        IqHandlerKey key = new IqHandlerKey(elementName, namespace, iq.getType());
        IqRequestHandler handler = iqRequestHandlers.get(key);

        if (handler == null) {
            return false;
        }

        log.debug("Handling IQ request with handler: {} for IQ id={}",
                handler.getClass().getSimpleName(), iq.getId());

        Iq response = handler.handleIqRequest(iq);
        if (response != null) {
            sendStanza(response);
        }
        return true;
    }

    /**
     * 获取子元素的元素名称。
     *
     * <p>如果子元素实现了 {@link ExtensionElement} 接口，
     * 则从接口获取元素名称；否则使用类名的简单名称（小写）作为元素名。</p>
     *
     * @param childElement 子元素对象，不能为 {@code null}
     * @return 元素名称，如果无法获取返回 null
     */
    private String getChildElementName(Object childElement) {
        /* 子元素可能是扩展对象或字符串 */
        if (childElement instanceof ExtensionElement ext) {
            return ext.getElementName();
        }
        return childElement.getClass().getSimpleName().toLowerCase();
    }

    /**
     * 获取子元素的命名空间。
     *
     * <p>如果子元素实现了 {@link ExtensionElement} 接口，
     * 则从接口获取命名空间；否则返回 null。</p>
     *
     * @param childElement 子元素对象，不能为 {@code null}
     * @return 命名空间，如果无法获取返回 null
     */
    private String getChildElementNamespace(Object childElement) {
        if (childElement instanceof ExtensionElement ext) {
            return ext.getNamespace();
        }
        return null;
    }

    /**
     * IQ 处理器键，用于唯一标识一个 IQ 请求处理器。
     */
    private static final class IqHandlerKey {
        private final String element;
        private final String namespace;
        private final Iq.Type iqType;

        IqHandlerKey(String element, String namespace, Iq.Type iqType) {
            if (element == null || iqType == null) {
                throw new IllegalArgumentException("element and iqType must not be null");
            }
            this.element = element;
            this.namespace = namespace;  // namespace 可以为 null（某些 IQ 无命名空间）
            this.iqType = iqType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            IqHandlerKey that = (IqHandlerKey) o;
            return Objects.equals(element, that.element)
                    && Objects.equals(namespace, that.namespace)
                    && iqType == that.iqType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(element, namespace, iqType);
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, %s)", element, namespace, iqType);
        }
    }

    /**
     * 通知所有监听器连接已关闭。
     */
    public void notifyConnectionClosed() {
        fireEvent(ConnectionEventType.CLOSED);
    }

    /**
     * 通知所有监听器连接因错误关闭。
     *
     * @param e 导致关闭的异常
     */
    public void notifyConnectionClosedOnError(Exception e) {
        fireEvent(ConnectionEventType.ERROR, e);
    }

    /**
     * 通知所有监听器连接已建立。
     */
    public void notifyConnected() {
        fireEvent(ConnectionEventType.CONNECTED);
    }

    /**
     * 通知所有监听器认证完成。
     *
     * @param resumed 是否为恢复的会话
     */
    public void notifyAuthenticated(boolean resumed) {
        fireEvent(ConnectionEventType.AUTHENTICATED);
    }

    /**
     * 分发连接事件到所有监听器（事件驱动核心方法）。
     *
     * <p>统一的事件分发入口，所有连接事件都通过 {@link XmppEventBus} 发布。</p>
     *
     * @param eventType 事件类型
     */
    protected void fireEvent(ConnectionEventType eventType) {
        fireEvent(eventType, null);
    }

    /**
     * 分发连接事件到所有监听器（带错误信息）。
     *
     * <p>将事件发布到事件总线，由订阅者异步处理。</p>
     *
     * @param eventType 事件类型，不能为 {@code null}
     * @param error     错误信息（可选，仅 ERROR 类型需要）
     */
    protected void fireEvent(ConnectionEventType eventType, Exception error) {
        /* 通过 XmppEventBus 发布事件 */
        XmppEventBus.getInstance().publish(this, eventType, error);
    }

    /**
     * 创建节收集器。
     *
     * @param filter 节过滤器
     * @return 新创建的收集器
     */
    @Override
    public AsyncStanzaCollector createStanzaCollector(StanzaFilter filter) {
        AsyncStanzaCollector collector = new AsyncStanzaCollector(filter);
        collectors.add(collector);
        return collector;
    }

    /**
     * 移除节收集器。
     *
     * <p>从连接的收集器队列中移除指定的收集器。</p>
     *
     * @param collector 要移除的收集器，不能为 {@code null}
     * @return 如果收集器存在并被移除返回 {@code true}
     */
    @Override
    public boolean removeStanzaCollector(AsyncStanzaCollector collector) {
        return collectors.remove(collector);
    }

    /**
     * 发送 IQ 请求并异步等待响应（使用默认超时）。
     *
     * <p>默认超时时间为 {@link XmppConstants#DEFAULT_IQ_TIMEOUT_MS} 毫秒。</p>
     *
     * @param iq 要发送的 IQ 节，不能为 {@code null}，必须包含非空 ID
     * @return CompletableFuture，完成时包含响应节；超时或出错时 exceptionally 完成
     * @throws IllegalArgumentException 如果 iq 为 null、unit 为 null、timeout 不为正数，或 IQ ID 为空
     */
    @Override
    public CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq) {
        return sendIqPacketAsync(iq, DEFAULT_IQ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 发送 IQ 请求并异步等待响应。
     *
     * <p>创建一个异步收集器来等待匹配的响应，然后发送 IQ 请求。
     * 响应超时后将触发 Future 异常完成。</p>
     *
     * @param iq      要发送的 IQ 节，不能为 {@code null}，必须包含非空 ID
     * @param timeout 超时时间，必须为正数
     * @param unit    时间单位，不能为 {@code null}
     * @return CompletableFuture，完成时包含响应节；超时或出错时 exceptionally 完成
     * @throws IllegalArgumentException 如果 iq 为 null、unit 为 null、timeout 不为正数，或 IQ ID 为空
     */
    public CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq, long timeout, TimeUnit unit) {
        Validate.notNull(iq, "IQ must not be null");
        Validate.notNull(unit, "TimeUnit must not be null");
        Validate.isTrue(timeout > 0, "Timeout must be positive");
        Validate.notBlank(iq.getId(), "IQ must have a non-blank ID");

        final String iqId = iq.getId();

        StanzaFilter filter = stanza -> {
            if (!(stanza instanceof Iq responseIq)) {
                return false;
            }
            return iqId.equals(stanza.getId())
                    && (responseIq.getType() == Iq.Type.RESULT || responseIq.getType() == Iq.Type.ERROR);
        };

        AsyncStanzaCollector collector = createStanzaCollector(filter);
        sendStanza(iq);

        return collector.getFuture()
                .orTimeout(timeout, unit)
                .whenComplete((result, ex) -> {
                    collectors.remove(collector);
                    if (ex != null) {
                        collector.getFuture().cancel(true);
                    }
                });
    }

    /**
     * 清理已完成的收集器。
     *
     * <p>移除所有 Future 已完成（正常完成、异常或取消）的收集器，避免内存泄漏。</p>
     */
    protected void cleanupCollectors() {
        collectors.removeIf(collector -> {
            if (collector.getFuture().isDone()) {
                collector.getFuture().cancel(true);
                return true;
            }
            return false;
        });
        log.debug("Completed collectors cleaned up, remaining: {}", collectors.size());
    }

    /**
     * 通知接收到节，分发给收集器。
     *
     * @param stanza 接收到的节
     */
    public void notifyStanzaReceived(XmppStanza stanza) {
        dispatchToCollectors(stanza);
    }

    /**
     * 将节分发给收集器。
     *
     * @param stanza 接收到的节
     */
    private void dispatchToCollectors(XmppStanza stanza) {
        collectors.forEach(collector -> {
            if (collector.processStanza(stanza)) {
                log.debug("Stanza collected by collector");
            }
        });
    }
}
