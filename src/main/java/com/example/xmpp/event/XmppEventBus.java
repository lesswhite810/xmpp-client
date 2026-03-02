package com.example.xmpp.event;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * XMPP 事件总线。
 * 
 * <p>提供事件订阅/发布功能，替代传统的Listener模式。
 * 支持同步和异步事件处理。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 订阅事件
 * eventBus.subscribe(AuthenticatedEvent.class, event -> {
 *     log.info("User authenticated: {}", event.getConnection().getUser());
 * });
 * 
 * // 发布事件
 * eventBus.publish(new AuthenticatedEvent(connection));
 * }</pre>
 * 
 * @since 2026-03-02
 */
@Slf4j
public final class XmppEventBus {

    /** 单例实例 */
    private static final XmppEventBus INSTANCE = new XmppEventBus();

    /** 事件处理器映射 */
    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /** 私有构造函数 */
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
     * 订阅事件。
     *
     * @param <T>      事件类型
     * @param eventType 事件类
     * @param handler  事件处理器
     * @return 取消订阅的 Runnable
     */
    @SuppressWarnings("unchecked")
    public <T> Runnable subscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<?>> handlers = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        synchronized (handlers) {
            handlers.add((Consumer<Object>) handler);
        }
        log.debug("Subscribed handler for event: {}", eventType.getSimpleName());

        // 返回取消订阅的回调
        return () -> unsubscribe(eventType, handler);
    }

    /**
     * 取消订阅事件。
     *
     * @param <T>      事件类型
     * @param eventType 事件类
     * @param handler  事件处理器
     */
    @SuppressWarnings("unchecked")
    public <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<?>> handlers = listeners.get(eventType);
        if (handlers != null) {
            synchronized (handlers) {
                handlers.remove((Consumer<Object>) handler);
            }
            log.debug("Unsubscribed handler for event: {}", eventType.getSimpleName());
        }
    }

    /**
     * 发布事件（同步处理）。
     *
     * @param <T>   事件类型
     * @param event 事件对象
     */
    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        Class<?> eventType = event.getClass();
        List<Consumer<?>> handlers = listeners.get(eventType);
        
        if (handlers == null || handlers.isEmpty()) {
            log.trace("No handlers for event: {}", eventType.getSimpleName());
            return;
        }

        // 复制列表避免并发问题
        List<Consumer<?>> handlersCopy;
        synchronized (handlers) {
            handlersCopy = List.copyOf(handlers);
        }

        // 同步调用所有处理器
        for (Consumer<?> handler : handlersCopy) {
            try {
                ((Consumer<T>) handler).accept(event);
            } catch (Exception e) {
                log.error("Error handling event {}: {}", eventType.getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 检查是否有订阅者。
     *
     * @param eventType 事件类型
     * @return 如果有订阅者返回 true
     */
    public boolean hasSubscribers(Class<?> eventType) {
        List<Consumer<?>> handlers = listeners.get(eventType);
        return handlers != null && !handlers.isEmpty();
    }

    /**
     * 获取订阅者数量。
     *
     * @param eventType 事件类型
     * @return 订阅者数量
     */
    public int getSubscriberCount(Class<?> eventType) {
        List<Consumer<?>> handlers = listeners.get(eventType);
        return handlers != null ? handlers.size() : 0;
    }

    /**
     * 清除所有订阅者。
     */
    public void clear() {
        listeners.clear();
        log.debug("All event subscribers cleared");
    }
}
