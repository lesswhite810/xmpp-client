package com.example.xmpp.event;

import com.example.xmpp.XmppConnection;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * XMPP 事件总线。
 *
 * <p>提供基于 XmppConnection 的事件订阅/发布功能。
 * 订阅时需要指定连接实例和事件类型，发布时自动分发给对应连接的订阅者。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * XmppEventBus eventBus = XmppEventBus.getInstance();
 *
 * // 订阅特定连接的事件
 * Runnable unsubscribe = eventBus.subscribe(myConnection, ConnectionEventType.AUTHENTICATED, event -> {
 *     log.info("认证成功: {}", event.connection().getUser());
 * });
 *
 * // 发布事件
 * eventBus.publish(connection, ConnectionEventType.CONNECTED);
 *
 * // 取消订阅
 * unsubscribe.run();
 * }</pre>
 *
 * @since 2026-03-02
 */
@Slf4j
public final class XmppEventBus {

    /**
     * 订阅记录，用于取消订阅
     */
    private record Subscription(ConnectionEventType eventType, Consumer<ConnectionEvent> handler) {}

    /**
     * 单例实例
     */
    private static final XmppEventBus INSTANCE = new XmppEventBus();

    /**
     * 连接专属订阅者：connection -> (eventType -> handlers)
     */
    private final Map<XmppConnection, Map<ConnectionEventType, List<Consumer<ConnectionEvent>>>> listeners = new ConcurrentHashMap<>();

    /**
     * 私有构造函数
     */
    private XmppEventBus() {
    }

    /**
     * 获取单例实例。
     *
     * @return 事件总线实例
     */
    public static XmppEventBus getInstance() {
        return INSTANCE;
    }

    /**
     * 订阅特定连接的事件。
     *
     * @param connection 连接实例
     * @param eventType  事件类型
     * @param handler    事件处理器
     * @return 取消订阅的 Runnable
     */
    public Runnable subscribe(XmppConnection connection, ConnectionEventType eventType,
                              Consumer<ConnectionEvent> handler) {
        List<Consumer<ConnectionEvent>> handlers = listeners
                .computeIfAbsent(connection, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());

        handlers.add(handler);

        log.debug("Subscribed handler for connection {} event: {}", connection, eventType);

        return () -> unsubscribe(connection, eventType, handler);
    }

    /**
     * 批量订阅多个事件类型。
     *
     * <p>一次调用订阅多个事件类型，返回的 Runnable 可一次性取消所有订阅。</p>
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * Runnable unsubscribe = eventBus.subscribeAll(connection, Map.of(
     *     ConnectionEventType.CONNECTED, event -> log.info("Connected"),
     *     ConnectionEventType.AUTHENTICATED, event -> log.info("Authenticated"),
     *     ConnectionEventType.ERROR, event -> log.error("Error", event.error())
     * ));
     *
     * // 取消所有订阅
     * unsubscribe.run();
     * }</pre>
     *
     * @param connection 连接实例
     * @param handlers   事件类型到处理器的映射
     * @return 取消所有订阅的 Runnable
     * @throws IllegalArgumentException 如果 connection 或 handlers 为 null
     */
    public Runnable subscribeAll(XmppConnection connection,
                                 Map<ConnectionEventType, Consumer<ConnectionEvent>> handlers) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection must not be null");
        }
        if (handlers == null || handlers.isEmpty()) {
            return () -> {};
        }

        List<Subscription> subscriptions = new ArrayList<>(handlers.size());

        for (Map.Entry<ConnectionEventType, Consumer<ConnectionEvent>> entry : handlers.entrySet()) {
            ConnectionEventType eventType = entry.getKey();
            Consumer<ConnectionEvent> handler = entry.getValue();

            List<Consumer<ConnectionEvent>> handlerList = listeners
                    .computeIfAbsent(connection, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());

            handlerList.add(handler);
            subscriptions.add(new Subscription(eventType, handler));
        }

        log.debug("Batch subscribed {} handlers for connection {}", handlers.size(), connection);

        return () -> {
            for (Subscription sub : subscriptions) {
                unsubscribeSilent(connection, sub.eventType(), sub.handler());
            }
            log.debug("Batch unsubscribed {} handlers for connection {}", handlers.size(), connection);
        };
    }

    /**
     * 取消订阅特定连接的事件（仅内部使用）。
     * 使用 subscribe 返回的 Runnable 来取消更方便。
     *
     * @param connection 连接实例
     * @param eventType  事件类型
     * @param handler    事件处理器
     */
    private void unsubscribe(XmppConnection connection, ConnectionEventType eventType,
                            Consumer<ConnectionEvent> handler) {
        Map<ConnectionEventType, List<Consumer<ConnectionEvent>>> connectionHandlers = listeners.get(connection);
        if (connectionHandlers != null) {
            List<Consumer<ConnectionEvent>> handlers = connectionHandlers.get(eventType);
            if (handlers != null) {
                handlers.removeIf(h -> h == handler);
                log.debug("Unsubscribed handler for connection {} event: {}", connection, eventType);

                if (handlers.isEmpty()) {
                    connectionHandlers.remove(eventType);
                }
            }
            if (connectionHandlers.isEmpty()) {
                listeners.remove(connection);
            }
        }
    }
    /**
     * 静默取消订阅（不打印日志，用于批量取消）。
     *
     * @param connection 连接实例
     * @param eventType  事件类型
     * @param handler    事件处理器
     */
    private void unsubscribeSilent(XmppConnection connection, ConnectionEventType eventType,
                                   Consumer<ConnectionEvent> handler) {
        Map<ConnectionEventType, List<Consumer<ConnectionEvent>>> connectionHandlers = listeners.get(connection);
        if (connectionHandlers != null) {
            List<Consumer<ConnectionEvent>> handlerList = connectionHandlers.get(eventType);
            if (handlerList != null) {
                handlerList.removeIf(h -> h == handler);

                if (handlerList.isEmpty()) {
                    connectionHandlers.remove(eventType);
                }
            }
            if (connectionHandlers.isEmpty()) {
                listeners.remove(connection);
            }
        }
    }

    /**
     * 取消特定连接的所有订阅。
     *
     * @param connection 连接实例
     */
    public void unsubscribeAll(XmppConnection connection) {
        listeners.remove(connection);
        log.debug("Unsubscribed all handlers for connection: {}", connection);
    }

    /**
     * 发布事件。
     *
     * @param connection 关联的连接
     * @param eventType  事件类型
     */
    public void publish(XmppConnection connection, ConnectionEventType eventType) {
        publish(connection, eventType, null);
    }

    /**
     * 发布事件（带错误信息）。
     *
     * @param connection 关联的连接
     * @param eventType  事件类型
     * @param error      错误信息（可选，仅 ERROR 类型需要）
     */
    public void publish(XmppConnection connection, ConnectionEventType eventType, Exception error) {
        ConnectionEvent event = new ConnectionEvent(connection, eventType, error);
        publishEvent(event);
    }

    private void publishEvent(ConnectionEvent event) {
        XmppConnection connection = event.connection();
        Map<ConnectionEventType, List<Consumer<ConnectionEvent>>> connectionHandlers = listeners.get(connection);
        if (connectionHandlers == null) {
            return;
        }

        List<Consumer<ConnectionEvent>> handlers = connectionHandlers.get(event.eventType());
        if (handlers == null || handlers.isEmpty()) {
            log.trace("No handlers for event: {}@{}", event.eventType(), connection);
            return;
        }

        for (Consumer<ConnectionEvent> handler : List.copyOf(handlers)) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.error("Error handling event {}@{}: {}",
                        event.eventType(), connection, e.getMessage(), e);
            }
        }
    }

    /**
     * 检查是否有订阅者。
     *
     * @param connection 连接实例
     * @param eventType  事件类型
     * @return 如果有订阅者返回 true
     */
    public boolean hasSubscribers(XmppConnection connection, ConnectionEventType eventType) {
        Map<ConnectionEventType, List<Consumer<ConnectionEvent>>> connectionHandlers = listeners.get(connection);
        if (connectionHandlers == null) {
            return false;
        }
        List<Consumer<ConnectionEvent>> handlers = connectionHandlers.get(eventType);
        return handlers != null && !handlers.isEmpty();
    }

    /**
     * 获取订阅者数量。
     *
     * @param connection 连接实例
     * @param eventType  事件类型
     * @return 订阅者数量
     */
    public int getSubscriberCount(XmppConnection connection, ConnectionEventType eventType) {
        Map<ConnectionEventType, List<Consumer<ConnectionEvent>>> connectionHandlers = listeners.get(connection);
        if (connectionHandlers == null) {
            return 0;
        }
        List<Consumer<ConnectionEvent>> handlers = connectionHandlers.get(eventType);
        return handlers != null ? handlers.size() : 0;
    }

    /**
     * 获取特定连接的总订阅者数量。
     *
     * @param connection 连接实例
     * @return 总订阅者数量
     */
    public int getTotalSubscriberCount(XmppConnection connection) {
        Map<ConnectionEventType, List<Consumer<ConnectionEvent>>> connectionHandlers = listeners.get(connection);
        if (connectionHandlers == null) {
            return 0;
        }
        return connectionHandlers.values().stream()
                .mapToInt(List::size)
                .sum();
    }
}
