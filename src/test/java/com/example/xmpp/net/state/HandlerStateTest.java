package com.example.xmpp.net.state;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HandlerState 接口约束测试。
 *
 * @since 2026-03-25
 */
class HandlerStateTest {

    @Test
    @DisplayName("handleMessage 必须由状态显式实现")
    void testHandleMessageMustBeExplicitlyImplemented() throws NoSuchMethodException {
        Method handleMessage = HandlerState.class.getMethod("handleMessage",
                StateContext.class,
                ChannelHandlerContext.class,
                Object.class);

        assertFalse(handleMessage.isDefault());
    }

    @Test
    @DisplayName("handleUserEvent 默认实现可以省略")
    void testHandleUserEventCanRemainDefault() throws NoSuchMethodException {
        Method handleUserEvent = HandlerState.class.getMethod("handleUserEvent",
                StateContext.class,
                ChannelHandlerContext.class,
                Object.class);

        assertTrue(handleUserEvent.isDefault());
    }
}
