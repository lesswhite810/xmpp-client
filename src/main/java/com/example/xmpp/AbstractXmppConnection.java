package com.example.xmpp;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionListener;
import com.example.xmpp.handler.IqRequestHandler;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.AsyncStanzaCollector;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

/**
 * XMPP 连接抽象基类。
 *
 * <p>提供连接监听器管理、IQ 请求发送等公共功能。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public abstract class AbstractXmppConnection implements XmppConnection {

    private static final long DEFAULT_IQ_TIMEOUT_MS = XmppConstants.DEFAULT_IQ_TIMEOUT_MS;

    /** 节收集器队列 */
    protected final Queue<AsyncStanzaCollector> collectors = new ConcurrentLinkedQueue<>();

    /** 连接监听器集合 */
    protected final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();

    /** IQ 请求处理器映射表，键为 (element, namespace, iqType) 组合 */
    private final Map<IqHandlerKey, IqRequestHandler> iqRequestHandlers = new ConcurrentHashMap<>();

    /**
     * 注册 IQ 请求处理器。
     *
     * @param handler IQ 请求处理器
     *
     * @throws IllegalArgumentException 如果已存在相同键的处理器
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
     * @param handler 要注销的处理器
     *
     * @return 如果处理器存在并被移除返回 true
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
     * <p>根据 IQ 的子元素和类型查找匹配的处理器并调用。</p>
     *
     * @param iq IQ 请求节
     *
     * @return 如果找到处理器并处理返回 true，否则返回 false
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
     * @param childElement 子元素对象
     *
     * @return 元素名称，如果无法获取返回 null
     */
    private String getChildElementName(Object childElement) {
        // 子元素可能是扩展对象或字符串
        if (childElement instanceof com.example.xmpp.protocol.model.ExtensionElement ext) {
            return ext.getElementName();
        }
        return childElement.getClass().getSimpleName().toLowerCase();
    }

    /**
     * 获取子元素的命名空间。
     *
     * @param childElement 子元素对象
     *
     * @return 命名空间，如果无法获取返回 null
     */
    private String getChildElementNamespace(Object childElement) {
        if (childElement instanceof com.example.xmpp.protocol.model.ExtensionElement ext) {
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
            // 校验 element 和 iqType 不能为 null
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
     * 添加连接状态监听器。
     *
     * @param listener 连接监听器
     */
    @Override
    public void addConnectionListener(ConnectionListener listener) {
        connectionListeners.add(listener);
    }

    /**
     * 移除连接状态监听器。
     *
     * @param listener 要移除的监听器
     */
    @Override
    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    /**
     * 通知所有监听器连接已关闭。
     */
    public void notifyConnectionClosed() {
        fireEvent(new ConnectionEvent.ConnectionClosedEvent(this));
    }

    /**
     * 通知所有监听器连接因错误关闭。
     *
     * @param e 导致关闭的异常
     */
    public void notifyConnectionClosedOnError(Exception e) {
        fireEvent(new ConnectionEvent.ConnectionClosedOnErrorEvent(this, e));
    }

    /**
     * 通知所有监听器连接已建立。
     */
    public void notifyConnected() {
        fireEvent(new ConnectionEvent.ConnectedEvent(this));
    }

    /**
     * 通知所有监听器认证完成。
     *
     * @param resumed 是否为恢复的会话
     */
    public void notifyAuthenticated(boolean resumed) {
        fireEvent(new ConnectionEvent.AuthenticatedEvent(this, resumed));
    }

    /**
     * 分发连接事件到所有监听器（事件驱动核心方法）。
     *
     * <p>统一的事件分发入口，所有连接事件都通过此方法分发。
     * 捕获监听器异常以防止一个监听器的错误影响其他监听器。</p>
     *
     * @param event 连接事件
     */
    protected void fireEvent(ConnectionEvent event) {
        connectionListeners.forEach(listener -> {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("Error in listener {} for event {}: {}",
                        listener.getClass().getSimpleName(),
                        event.getClass().getSimpleName(),
                        e.getMessage(), e);
            }
        });
    }

    /**
     * 创建节收集器。
     *
     * @param filter 节过滤器
     * @return 新创建的收集器
     */
    private AsyncStanzaCollector createStanzaCollector(StanzaFilter filter) {
        AsyncStanzaCollector collector = new AsyncStanzaCollector(filter);
        collectors.add(collector);
        return collector;
    }

    /**
     * 发送 IQ 请求并异步等待响应（使用默认超时）。
     *
     * @param iq 要发送的 IQ 节
     * @return CompletableFuture，完成时包含响应节
     */
    @Override
    public CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq) {
        return sendIqPacketAsync(iq, DEFAULT_IQ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 发送 IQ 请求并异步等待响应。
     *
     * @param iq      要发送的 IQ 节
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return CompletableFuture，完成时包含响应节
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
     * <p>移除所有 Future 已完成的收集器，避免内存泄漏。</p>
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
