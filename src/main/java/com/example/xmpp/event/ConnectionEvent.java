package com.example.xmpp.event;

import com.example.xmpp.XmppConnection;

/**
 * 连接事件 sealed interface。
 *
 * <p>表示 XMPP 连接生命周期中发生的各种事件。
 * 使用 sealed interface 确保只有定义的事件类型可以被创建。</p>
 *
 * @since 2026-02-25
 */
public sealed interface ConnectionEvent {

    /**
     * 获取关联的连接对象。
     *
     * @return XMPP 连接实例
     */
    XmppConnection connection();

    /**
     * 连接建立事件。
     *
     * <p>当 TCP 连接成功建立并且 XMPP 流初始化完成时触发。</p>
     *
     * @param connection 已建立的连接对象
     */
    record ConnectedEvent(XmppConnection connection) implements ConnectionEvent {
    }

    /**
     * 认证成功事件。
     *
     * <p>当 SASL 认证完成并且资源绑定成功时触发。</p>
     *
     * @param connection 已认证的连接对象
     * @param resumed    是否为恢复的会话
     */
    record AuthenticatedEvent(XmppConnection connection, boolean resumed) implements ConnectionEvent {
    }

    /**
     * 连接正常关闭事件。
     *
     * <p>当主动断开连接时触发。</p>
     *
     * @param connection 被关闭的连接对象
     */
    record ConnectionClosedEvent(XmppConnection connection) implements ConnectionEvent {
    }

    /**
     * 连接因错误关闭事件。
     *
     * <p>当连接由于网络错误、认证失败或其他异常而意外断开时触发。</p>
     *
     * @param connection 被关闭的连接对象
     * @param error      导致连接关闭的异常
     */
    record ConnectionClosedOnErrorEvent(XmppConnection connection, Exception error) implements ConnectionEvent {
    }
}
