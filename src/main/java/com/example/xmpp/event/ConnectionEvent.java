package com.example.xmpp.event;

import com.example.xmpp.XmppConnection;

/**
 * XMPP 连接事件。
 *
 * <p>表示 XMPP 连接生命周期中发生的各种事件。
 * 使用 {@link ConnectionEventType} 枚举区分事件类型。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 创建事件
 * ConnectionEvent event = new ConnectionEvent(connection, ConnectionEventType.CONNECTED);
 *
 * // 订阅特定连接的事件
 * eventBus.subscribe(connection, ConnectionEventType.CONNECTED, e -> {
 *     log.info("Connected: {}", e.connection().getConfig().getUser());
 * });
 *
 * // 订阅全局事件（所有连接）
 * eventBus.subscribe(ConnectionEventType.AUTHENTICATED, e -> {
 *     log.info("User authenticated: {}", e.connection().getUser());
 * });
 *
 * // 发布事件
 * eventBus.publish(connection, new ConnectionEvent(connection, ConnectionEventType.CONNECTED));
 * }</pre>
 *
 * @since 2026-03-03
 */
public class ConnectionEvent {

    private final XmppConnection connection;
    private final ConnectionEventType eventType;
    private final Exception error;

    /**
     * 创建连接事件（无错误）。
     *
     * @param connection 关联的连接
     * @param eventType  事件类型
     */
    public ConnectionEvent(XmppConnection connection, ConnectionEventType eventType) {
        this(connection, eventType, null);
    }

    /**
     * 创建连接事件（带错误，用于 ERROR 类型）。
     *
     * @param connection 关联的连接
     * @param eventType  事件类型
     * @param error     错误信息（可选，仅 ERROR 类型需要）
     */
    public ConnectionEvent(XmppConnection connection, ConnectionEventType eventType, Exception error) {
        this.connection = connection;
        this.eventType = eventType;
        this.error = error;
    }

    /**
     * 获取关联的连接。
     *
     * @return XMPP 连接实例
     */
    public XmppConnection connection() {
        return connection;
    }

    /**
     * 获取事件类型。
     *
     * @return 事件类型枚举
     */
    public ConnectionEventType eventType() {
        return eventType;
    }

    /**
     * 获取错误信息。
     *
     * @return 错误异常，仅 ERROR 类型有效
     */
    public Exception error() {
        return error;
    }

    @Override
    public String toString() {
        if (error != null) {
            return "ConnectionEvent{connection=%s, type=%s, error=%s}".formatted(
                    connection, eventType, error.getMessage());
        }
        return "ConnectionEvent{connection=%s, type=%s}".formatted(connection, eventType);
    }
}
