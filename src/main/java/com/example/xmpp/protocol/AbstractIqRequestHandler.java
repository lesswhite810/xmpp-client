package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.Iq;
import lombok.Getter;

/**
 * IQ 请求处理器抽象基类。
 *
 * <p>提供 IqRequestHandler 接口的基础实现，封装元素名称、命名空间、类型和处理模式的管理。
 * 子类只需实现 handleIqRequest(Iq) 方法即可处理特定的 IQ 请求。</p>
 *
 * @see IqRequestHandler
 * @since 2026-02-09
 */
@Getter
public abstract class AbstractIqRequestHandler implements IqRequestHandler {

    /** 元素名称 */
    private final String element;

    /** 命名空间 */
    private final String namespace;

    /** IQ 类型 */
    private final Iq.Type type;

    /** 处理模式 */
    private final Mode mode;

    /**
     * 创建 IQ 请求处理器。
     *
     * @param element   要匹配的元素名称
     * @param namespace 要匹配的命名空间
     * @param type      要匹配的 IQ 类型
     * @param mode      处理模式
     */
    protected AbstractIqRequestHandler(String element, String namespace, Iq.Type type, Mode mode) {
        this.element = element;
        this.namespace = namespace;
        this.type = type;
        this.mode = mode;
    }
}