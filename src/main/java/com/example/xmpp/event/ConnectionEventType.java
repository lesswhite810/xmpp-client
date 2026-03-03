package com.example.xmpp.event;

/**
 * XMPP 连接事件类型枚举。
 *
 * <p>定义所有可能的连接事件类型，用于事件订阅和发布。</p>
 *
 * @since 2026-03-03
 */
public enum ConnectionEventType {

    /**
     * 连接已建立。
     *
     * <p>TCP 连接成功建立并且 XMPP 流初始化完成时触发。</p>
     */
    CONNECTED,

    /**
     * 认证成功。
     *
     * <p>SASL 认证完成并且资源绑定成功时触发。</p>
     */
    AUTHENTICATED,

    /**
     * 连接正常关闭。
     *
     * <p>主动断开连接时触发。</p>
     */
    CLOSED,

    /**
     * 连接因错误关闭。
     *
     * <p>网络错误、认证失败或其他异常导致连接意外断开时触发。</p>
     */
    ERROR
}
