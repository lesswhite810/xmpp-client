package com.example.xmpp;

import com.example.xmpp.XmppConstants;
import com.example.xmpp.protocol.AsyncStanzaCollector;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.StanzaListener;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
 * <p>提供节监听器管理、连接监听器管理、IQ 请求发送等公共功能。</p>
 *
 * @since 2026-02-09
 */
public abstract class AbstractXmppConnection implements XmppConnection {

    private static final Logger log = LoggerFactory.getLogger(AbstractXmppConnection.class);

    private static final long DEFAULT_IQ_TIMEOUT_MS = XmppConstants.DEFAULT_IQ_TIMEOUT_MS;

    /** 异步节监听器映射表 */
    protected final Map<StanzaListener, StanzaFilter> asyncListeners = new ConcurrentHashMap<>();

    /** 节收集器队列 */
    protected final Queue<AsyncStanzaCollector> collectors = new ConcurrentLinkedQueue<>();

    /** 连接监听器集合 */
    protected final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();

    /**
     * 添加异步节监听器。
     *
     * @param listener 节监听器
     * @param filter   节过滤器
     */
    @Override
    public void addAsyncStanzaListener(StanzaListener listener, StanzaFilter filter) {
        Validate.notNull(listener, "StanzaListener must not be null");
        asyncListeners.put(listener, filter != null ? filter : stanza -> true);
    }

    /**
     * 移除节监听器。
     *
     * @param listener 要移除的监听器
     */
    @Override
    public void removeAsyncStanzaListener(StanzaListener listener) {
        asyncListeners.remove(listener);
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
    protected void notifyConnectionClosed() {
        fireEvent(new ConnectionEvent.ConnectionClosedEvent(this));
    }

    /**
     * 通知所有监听器连接因错误关闭。
     *
     * @param e 导致关闭的异常
     */
    protected void notifyConnectionClosedOnError(Exception e) {
        fireEvent(new ConnectionEvent.ConnectionClosedOnErrorEvent(this, e));
    }

    /**
     * 通知所有监听器连接已建立。
     */
    protected void notifyConnected() {
        fireEvent(new ConnectionEvent.ConnectedEvent(this));
    }

    /**
     * 通知所有监听器认证完成。
     *
     * @param resumed 是否为恢复的会话
     */
    protected void notifyAuthenticated(boolean resumed) {
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
            boolean idMatch = iqId.equals(stanza.getId());
            boolean typeMatch = responseIq.getType() == Iq.Type.result
                    || responseIq.getType() == Iq.Type.error;

            log.debug("Filter check: stanzaId={}, expectedId={}, type={}, idMatch={}, typeMatch={}",
                    stanza.getId(), iqId, responseIq.getType(), idMatch, typeMatch);

            return idMatch && typeMatch;
        };

        AsyncStanzaCollector collector = createStanzaCollector(filter);
        sendStanza(iq);

        return collector.getFuture()
                .orTimeout(timeout, unit)
                .whenComplete((result, ex) -> {
                    collectors.remove(collector);
                    if (ex != null) {
                        log.warn("IQ request failed or timed out: id={}, error={}", iqId, ex.getMessage());
                    }
                });
    }

    /**
     * 清理所有收集器。
     */
    protected void cleanupCollectors() {
        collectors.forEach(collector -> collector.getFuture().cancel(true));
        collectors.clear();
        log.debug("All collectors cleaned up");
    }

    /**
     * 处理接收到的节，分发给收集器和监听器。
     *
     * @param stanza 接收到的节
     */
    protected void invokeStanzaCollectorsAndListeners(XmppStanza stanza) {
        dispatchToCollectors(stanza);
        dispatchToAsyncListeners(stanza);
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

    /**
     * 将节分发给异步监听器。
     *
     * @param stanza 接收到的节
     */
    private void dispatchToAsyncListeners(XmppStanza stanza) {
        asyncListeners.forEach((listener, filter) -> {
            if (filter.accept(stanza)) {
                listener.processStanza(stanza);
            }
        });
    }
}
