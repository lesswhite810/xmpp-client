package com.example.xmpp.net.state;

import io.netty.channel.ChannelHandlerContext;

/**
 * XMPP 处理器状态接口。
 *
 * @since 2026-02-20
 */
public interface HandlerState {

    /**
     * 获取状态名称。
     *
     * @return 状态名称
     */
    String getName();

    /**
     * 处理接收到的消息。
     *
     * @param context 状态上下文
     * @param ctx Netty 通道上下文
     * @param msg 接收到的消息
     */
    default void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
    }

    /**
     * 进入此状态时调用。
     *
     * @param context 状态上下文
     * @param ctx Netty 通道上下文
     */
    default void onEnter(StateContext context, ChannelHandlerContext ctx) {
    }

    /**
     * 离开此状态时调用。
     *
     * @param context 状态上下文
     * @param ctx Netty 通道上下文
     */
    default void onExit(StateContext context, ChannelHandlerContext ctx) {
    }

    /**
     * 是否为会话激活状态。
     *
     * @return 是否激活
     */
    default boolean isSessionActive() {
        return false;
    }
}
