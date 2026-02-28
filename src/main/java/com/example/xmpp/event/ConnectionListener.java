package com.example.xmpp.event;

/**
 * XMPP 连接状态监听器。
 *
 * <p>监听连接生命周期的各个阶段，包括建立、认证、断开等事件。
 * 实现此接口以接收连接状态变化的通知。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * connection.addConnectionListener(event -> {
 *     switch (event) {
 *         case ConnectionEvent.ConnectedEvent e ->
 *             log.info("Connected to server");
 *         case ConnectionEvent.AuthenticatedEvent e ->
 *             log.info("Authenticated: resumed={}", e.resumed());
 *         case ConnectionEvent.ConnectionClosedEvent e ->
 *             log.info("Connection closed");
 *         case ConnectionEvent.ConnectionClosedOnErrorEvent e ->
 *             log.error("Connection error: ", e.error());
 *     }
 * });
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
