package com.example.xmpp.event;

import com.example.xmpp.XmppConnection;

/**
 * XMPP 连接事件。
 *
 * <p>表示 XMPP 连接生命周期中发生的各种事件。
 * 使用 {@link ConnectionEventType} 枚举区分事件类型。</p>
 *
 * @since 2026-03-03
 */
public record ConnectionEvent(XmppConnection connection, ConnectionEventType eventType, Exception error) {

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
     * 获取错误信息。
     *
     * @return 错误异常，ERROR 与异常关闭场景下的 CLOSED 事件可用
     */
    public Exception error() {
        return error;
    }

    @Override
    public String toString() {
        if (error != null) {
            return "ConnectionEvent{connection=%s, type=%s, errorType=%s}".formatted(
                    connection, eventType, error.getClass().getSimpleName());
        }
        return "ConnectionEvent{connection=%s, type=%s}".formatted(connection, eventType);
    }
}
