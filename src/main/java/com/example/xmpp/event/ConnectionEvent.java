package com.example.xmpp.event;

import com.example.xmpp.XmppConnection;

/**
 * XMPP 连接事件。
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

    @Override
    public String toString() {
        if (error != null) {
            return "ConnectionEvent{connection=%s, type=%s, errorType=%s}".formatted(
                    connection, eventType, error.getClass().getSimpleName());
        }
        return "ConnectionEvent{connection=%s, type=%s}".formatted(connection, eventType);
    }
}
