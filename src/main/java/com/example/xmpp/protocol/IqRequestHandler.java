package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;

/**
 * IQ 请求处理器接口，用于处理特定的 IQ 请求类型（GET 或 SET）。
 *
 * <p>处理器根据元素名称、命名空间和类型匹配传入的 IQ 请求。
 * 参考 Smack 的 IqRequestHandler 设计。</p>
 *
 * @see AbstractIqRequestHandler
 * @since 2026-02-09
 */
public interface IqRequestHandler {

    /**
     * 处理器的处理模式。
     */
    enum Mode {
        /** 同步处理 */
        sync,
        /** 异步处理 */
        async
    }

    /**
     * 获取处理模式。
     *
     * @return 处理模式
     */
    Mode getMode();

    /**
     * 获取要匹配的元素名称。
     *
     * @return 元素名称
     */
    String getElement();

    /**
     * 获取要匹配的命名空间。
     *
     * @return 命名空间
     */
    String getNamespace();

    /**
     * 获取要匹配的 IQ 类型。
     *
     * @return IQ 类型
     */
    Iq.Type getType();

    /**
     * 处理匹配的 IQ 请求。
     *
     * @param iq 传入的 IQ 请求
     *
     * @return 响应节，通常是 IQ result 或 IQ error
     */
    XmppStanza handleIqRequest(Iq iq);
}
