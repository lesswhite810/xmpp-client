package com.example.xmpp.handler;

import com.example.xmpp.protocol.model.Iq;

/**
 * IQ 请求处理器接口。
 *
 * @since 2026-02-26
 */
public interface IqRequestHandler {

    /**
     * 处理 IQ 请求并返回响应。
     *
     * @param iqRequest 收到的 IQ 请求
     * @return 响应 IQ，或 null
     */
    Iq handleIqRequest(Iq iqRequest);

    /**
     * 获取此处理器感兴趣的 IQ 子元素名称。
     *
     * @return 元素名称
     */
    String getElement();

    /**
     * 获取此处理器感兴趣的 IQ 子元素命名空间。
     *
     * @return 命名空间
     */
    String getNamespace();

    /**
     * 获取此处理器处理的 IQ 类型。
     *
     * @return IQ 类型
     */
    Iq.Type getIqType();

    /**
     * 获取处理模式。
     *
     * @return 处理模式
     */
    Mode getMode();

    /**
     * 处理模式枚举。
     */
    enum Mode {
        /**
         * 同步模式。
         */
        SYNC,

        /**
         * 异步模式。
         */
        ASYNC
    }
}
