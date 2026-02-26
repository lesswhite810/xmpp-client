package com.example.xmpp;

import com.example.xmpp.protocol.model.Iq;

/**
 * IQ 请求处理器接口。
 *
 * <p>用于处理从 XMPP 服务器接收的 IQ 请求（get/set 类型）。
 * 实现此接口可自定义处理特定类型的 IQ 请求，如 Ping、Disco 等。</p>
 *
 * <p>参考 Smack 的 IQRequestHandler 设计模式。</p>
 *
 * @since 2026-02-26
 */
public interface IqRequestHandler {

    /**
     * 处理 IQ 请求并返回响应。
     *
     * @param iqRequest 收到的 IQ 请求节
     *
     * @return 响应 IQ 节，如果不需要响应则返回 null
     */
    Iq handleIqRequest(Iq iqRequest);

    /**
     * 获取此处理器感兴趣的 IQ 子元素名称。
     *
     * @return 元素名称，如 "ping"
     */
    String getElement();

    /**
     * 获取此处理器感兴趣的 IQ 子元素命名空间。
     *
     * @return 命名空间 URI，如 "urn:xmpp:ping"
     */
    String getNamespace();

    /**
     * 获取此处理器处理的 IQ 类型。
     *
     * @return IQ 类型（get 或 set）
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
     *
     * <ul>
     * <li>{@link #SYNC} - 同步模式，在 Netty EventLoop 中直接处理</li>
     * <li>{@link #ASYNC} - 异步模式，提交到单独线程池处理</li>
     * </ul>
     */
    enum Mode {
        /**
         * 同步模式。
         *
         * <p>在 Netty I/O 线程中直接处理，适用于快速、非阻塞操作。</p>
         * <p>注意：不要在此模式下执行耗时操作，否则会阻塞网络 I/O。</p>
         */
        SYNC,

        /**
         * 异步模式。
         *
         * <p>提交到单独的线程池处理，适用于需要执行耗时操作的场景。</p>
         */
        ASYNC
    }
}
