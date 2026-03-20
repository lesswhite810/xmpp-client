package com.example.xmpp.event;

/**
 * XMPP 连接事件类型枚举。
 *
 * @since 2026-03-03
 */
public enum ConnectionEventType {

    /**
     * 连接已建立。
     */
    CONNECTED,

    /**
     * 认证成功。
     */
    AUTHENTICATED,

    /**
     * 连接正常关闭。
     */
    CLOSED,

    /**
     * 连接因错误关闭。
     */
    ERROR
}
