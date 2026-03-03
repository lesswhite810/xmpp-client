package com.example.xmpp.event;

/**
 * XMPP 连接状态监听器。
 *
 * <p>监听连接生命周期的各个阶段，包括建立、认证、断开等事件。
 * 实现此接口以接收连接状态变化的通知。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 通过 XmppEventBus 订阅事件
 * XmppEventBus eventBus = XmppEventBus.getInstance();
 * Runnable unsubscribe = eventBus.subscribeAll(connection, Map.of(
 *     ConnectionEventType.CONNECTED, event -> log.info("Connected"),
 *     ConnectionEventType.AUTHENTICATED, event -> log.info("Authenticated"),
 *     ConnectionEventType.CLOSED, event -> log.info("Connection closed"),
 *     ConnectionEventType.ERROR, event -> log.error("Error", event.error())
 * ));
 *
 * // 或使用 ConnectionListener 函数式接口
 * ConnectionListener listener = event -> {
 *     switch (event.eventType()) {
 *         case CONNECTED -> log.info("Connected to server");
 *         case AUTHENTICATED -> log.info("Authenticated");
 *         case CLOSED -> log.info("Connection closed");
 *         case ERROR -> log.error("Connection error: ", event.error());
 *     }
 * };
 * eventBus.subscribe(connection, ConnectionEventType.CONNECTED, listener::onEvent);
 *
 * // 取消订阅
 * unsubscribe.run();
 * }</pre>
 *
 * @since 2026-02-09
 */
@FunctionalInterface
public interface ConnectionListener {

    /**
     * 处理连接事件。
     *
     * <p>当连接状态发生变化时调用，实现者应根据事件类型执行相应操作。</p>
     *
     * @param event 连接事件，包含事件类型和相关数据
     */
    void onEvent(ConnectionEvent event);
}
