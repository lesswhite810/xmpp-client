package com.example.xmpp;

import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.handler.IqRequestHandler;
import com.example.xmpp.protocol.AsyncStanzaCollector;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.util.XmppConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * XMPP 连接的抽象基类。
 *
 * <p>提供 IQ 处理器注册、collector 管理以及连接生命周期事件分发等公共能力。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public abstract class AbstractXmppConnection implements XmppConnection {

    private static final long DEFAULT_IQ_TIMEOUT_MS = XmppConstants.DEFAULT_IQ_TIMEOUT_MS;


    protected final Queue<AsyncStanzaCollector> collectors = new ConcurrentLinkedQueue<>();

    private final Map<IqHandlerKey, IqRequestHandler> iqRequestHandlers = new ConcurrentHashMap<>();

    /**
     * 注册 IQ 请求处理器。
     *
     * @param handler 待注册的处理器
     */
    @Override
    public final void registerIqRequestHandler(IqRequestHandler handler) {
        Validate.notNull(handler, "Handler must not be null");
        IqHandlerKey key = new IqHandlerKey(
                handler.getElement(),
                handler.getNamespace(),
                handler.getIqType()
        );
        IqRequestHandler existing = iqRequestHandlers.putIfAbsent(key, handler);
        if (existing != null) {
            throw new IllegalArgumentException("IQ request handler already registered for: " + key);
        }
        log.debug("Registered IQ request handler: {} -> {}", key, handler.getClass().getSimpleName());
    }

    /**
     * 注销 IQ 请求处理器。
     *
     * @param handler 待移除的处理器
     * @return 如果处理器被移除则返回 {@code true}
     */
    @Override
    public final boolean unregisterIqRequestHandler(IqRequestHandler handler) {
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
     * 将收到的 IQ 请求分发给已注册的处理器。
     *
     * @param iq 入站 IQ stanza
     * @return 如果有处理器处理了该请求则返回 {@code true}
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
        Optional<String> namespace = findChildElementNamespace(childElement);
        if (elementName == null || namespace.isEmpty()) {
            return false;
        }

        IqHandlerKey key = new IqHandlerKey(elementName, namespace.get(), iq.getType());
        IqRequestHandler handler = iqRequestHandlers.get(key);
        if (handler == null) {
            XmppError.Condition condition = resolveUnsupportedIqCondition(key);
            log.debug("No IQ request handler found for key: {}, returning {}", key, condition);
            sendUnsupportedIqError(iq, condition);
            return true;
        }

        log.debug("Handling IQ request with handler: {} for IQ id={}",
                handler.getClass().getSimpleName(), iq.getId());

        Iq response = handler.handleIqRequest(iq);
        if (response != null) {
            sendStanza(response);
        }
        return true;
    }

    private XmppError.Condition resolveUnsupportedIqCondition(IqHandlerKey key) {
        boolean knownNamespace = iqRequestHandlers.keySet().stream()
                .anyMatch(registeredKey -> registeredKey.iqType() == key.iqType()
                        && registeredKey.namespace().equals(key.namespace()));
        return knownNamespace ? XmppError.Condition.FEATURE_NOT_IMPLEMENTED
                : XmppError.Condition.SERVICE_UNAVAILABLE;
    }

    private void sendUnsupportedIqError(Iq iq, XmppError.Condition condition) {
        XmppError error = new XmppError.Builder(condition).build();
        sendStanza(Iq.createErrorResponse(iq, error));
    }

    private String getChildElementName(Object childElement) {
        if (childElement instanceof ExtensionElement ext) {
            return ext.getElementName();
        }
        return childElement.getClass().getSimpleName().toLowerCase();
    }

    private Optional<String> findChildElementNamespace(Object childElement) {
        if (childElement instanceof ExtensionElement ext) {
            return Optional.ofNullable(ext.getNamespace());
        }
        return Optional.empty();
    }

    private record IqHandlerKey(String element, String namespace, Iq.Type iqType) {
        IqHandlerKey {
            if (element == null || iqType == null) {
                throw new IllegalArgumentException("element and iqType must not be null");
            }
        }
    }

    /**
     * 发布正常关闭连接事件。
     */
    public void notifyConnectionClosed() {
        fireEvent(ConnectionEventType.CLOSED);
    }

    /**
     * 发布连接异常关闭事件。
     *
     * @param e 连接异常
     */
    public void notifyConnectionClosedOnError(Exception e) {
        fireEvent(ConnectionEventType.ERROR, e);
    }

    /**
     * 发布连接建立事件。
     */
    public void notifyConnected() {
        fireEvent(ConnectionEventType.CONNECTED);
    }

    /**
     * 发布认证完成事件。
     */
    public void notifyAuthenticated() {
        fireEvent(ConnectionEventType.AUTHENTICATED);
    }

    /**
     * 触发不带异常信息的连接事件。
     *
     * @param eventType 事件类型
     */
    protected void fireEvent(ConnectionEventType eventType) {
        fireEvent(eventType, null);
    }

    /**
     * 触发带异常信息的连接事件。
     *
     * @param eventType 事件类型
     * @param error 附带异常
     */
    protected void fireEvent(ConnectionEventType eventType, Exception error) {
        XmppEventBus.getInstance().publish(this, eventType, error);
    }

    /**
     * 创建并注册 stanza collector。
     *
     * @param filter stanza 过滤器
     * @return 创建后的 collector
     */
    @Override
    public AsyncStanzaCollector createStanzaCollector(StanzaFilter filter) {
        AsyncStanzaCollector collector = new AsyncStanzaCollector(filter);
        collectors.add(collector);
        return collector;
    }

    /**
     * 移除 stanza collector。
     *
     * @param collector 待移除的 collector
     * @return 如果 collector 被移除则返回 {@code true}
     */
    @Override
    public boolean removeStanzaCollector(AsyncStanzaCollector collector) {
        return collectors.remove(collector);
    }

    /**
     * 异步发送 IQ stanza 并等待匹配响应。
     *
     * @param iq 待发送的 IQ stanza
     * @return 响应 Future，若服务端返回 IQ error，则以异常形式结束
     */
    @Override
    public CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq) {
        return sendIqPacketAsync(iq, DEFAULT_IQ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 发送带超时时间的 IQ stanza。
     *
     * @param iq 待发送的 IQ stanza
     * @param timeout 超时时间
     * @param unit 超时时间单位
     * @return 响应 Future，成功时返回匹配的结果节，失败时返回异常
     */
    public CompletableFuture<XmppStanza> sendIqPacketAsync(Iq iq, long timeout, TimeUnit unit) {
        validateIqSendArguments(iq, timeout, unit);
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new XmppNetworkException("Channel is not active"));
        }

        AsyncStanzaCollector collector = createIqResponseCollector(iq);
        CompletableFuture<XmppStanza> responseFuture = createIqResponseFuture(collector, timeout, unit);
        CompletableFuture<Void> dispatchFuture = dispatchStanza(iq);
        propagateDispatchFailure(dispatchFuture, collector);
        return finalizeIqResponseFuture(responseFuture, collector);
    }

    private void validateIqSendArguments(Iq iq, long timeout, TimeUnit unit) {
        if (iq == null) {
            throw new IllegalArgumentException("IQ must not be null");
        }
        if (unit == null) {
            throw new IllegalArgumentException("TimeUnit must not be null");
        }
        Validate.isTrue(timeout > 0, "Timeout must be positive");
        Validate.notBlank(iq.getId(), "IQ must have a non-blank ID");
    }

    private AsyncStanzaCollector createIqResponseCollector(Iq iq) {
        String iqId = iq.getId();
        StanzaFilter filter = stanza -> matchesIqResponse(stanza, iqId);
        return createStanzaCollector(filter);
    }

    private boolean matchesIqResponse(XmppStanza stanza, String iqId) {
        if (!(stanza instanceof Iq responseIq)) {
            return false;
        }
        return iqId.equals(stanza.getId())
                && (responseIq.getType() == Iq.Type.RESULT || responseIq.getType() == Iq.Type.ERROR);
    }

    private CompletableFuture<XmppStanza> createIqResponseFuture(AsyncStanzaCollector collector,
                                                                 long timeout,
                                                                 TimeUnit unit) {
        return collector.getFuture()
                .orTimeout(timeout, unit)
                .thenCompose(this::mapIqErrorResponse);
    }

    private void propagateDispatchFailure(CompletableFuture<Void> dispatchFuture, AsyncStanzaCollector collector) {
        dispatchFuture.whenComplete((ignored, dispatchError) -> {
            if (dispatchError != null) {
                collector.getFuture().completeExceptionally(dispatchError);
            }
        });
    }

    private CompletableFuture<XmppStanza> finalizeIqResponseFuture(CompletableFuture<XmppStanza> responseFuture,
                                                                   AsyncStanzaCollector collector) {
        return responseFuture
                .whenComplete((result, ex) -> {
                    collectors.remove(collector);
                    if (ex != null) {
                        collector.getFuture().cancel(true);
                    }
                });
    }

    /**
     * 将 stanza 发送到底层传输层，并返回发送结果。
     *
     * @param stanza 待发送的 stanza
     * @return 发送完成 Future；如果发送未能下发到底层通道，则以异常结束
     */
    protected abstract CompletableFuture<Void> dispatchStanza(XmppStanza stanza);

    private CompletableFuture<XmppStanza> mapIqErrorResponse(XmppStanza stanza) {
        if (stanza instanceof Iq responseIq && responseIq.getType() == Iq.Type.ERROR) {
            String message = "Received XMPP error response for IQ id=" + responseIq.getId();
            return CompletableFuture.failedFuture(new XmppStanzaErrorException(message, responseIq));
        }
        return CompletableFuture.completedFuture(stanza);
    }

    /**
     * 使用指定异常结束所有未完成的 collector。
     *
     * @param exception 失败原因
     */
    protected void failPendingCollectors(Exception exception) {
        collectors.removeIf(collector -> failCollector(collector, exception));
    }

    /**
     * 清理所有已完成的 stanza collector。
     */
    protected void cleanupCollectors() {
        collectors.removeIf(this::cleanupCollectorIfDone);
        log.debug("Completed collectors cleaned up, remaining: {}", collectors.size());
    }

    private boolean failCollector(AsyncStanzaCollector collector, Exception exception) {
        CompletableFuture<XmppStanza> future = collector.getFuture();
        if (!future.isDone()) {
            future.completeExceptionally(exception);
        }
        return true;
    }

    private boolean cleanupCollectorIfDone(AsyncStanzaCollector collector) {
        if (!collector.getFuture().isDone()) {
            return false;
        }
        collector.getFuture().cancel(true);
        return true;
    }

    /**
     * 将收到的 stanza 分发给匹配的 collector。
     *
     * @param stanza 收到的 stanza
     */
    public void notifyStanzaReceived(XmppStanza stanza) {
        collectors.forEach(collector -> {
            if (collector.processStanza(stanza)) {
                log.debug("Stanza collected by collector");
            }
        });
    }
}
