package com.example.xmpp;

import com.example.xmpp.protocol.model.Iq;

/**
 * IQ 请求处理器抽象基类。
 *
 * <p>提供 IqRequestHandler 的便捷实现，子类只需实现
 * {@link #handleIqRequest(Iq)} 方法即可。</p>
 *
 * @since 2026-02-26
 */
public abstract class AbstractIqRequestHandler implements IqRequestHandler {

    /** 元素名称 */
    private final String element;

    /** 命名空间 */
    private final String namespace;

    /** IQ 类型 */
    private final Iq.Type iqType;

    /** 处理模式 */
    private final Mode mode;

    /**
     * 构造 IQ 请求处理器。
     *
     * @param element   元素名称
     * @param namespace 命名空间
     * @param iqType    IQ 类型
     * @param mode      处理模式
     */
    protected AbstractIqRequestHandler(String element, String namespace, Iq.Type iqType, Mode mode) {
        this.element = element;
        this.namespace = namespace;
        this.iqType = iqType;
        this.mode = mode;
    }

    /**
     * 构造同步模式的 IQ 请求处理器。
     *
     * @param element   元素名称
     * @param namespace 命名空间
     * @param iqType    IQ 类型
     */
    protected AbstractIqRequestHandler(String element, String namespace, Iq.Type iqType) {
        this(element, namespace, iqType, Mode.SYNC);
    }

    @Override
    public String getElement() {
        return element;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Iq.Type getIqType() {
        return iqType;
    }

    @Override
    public Mode getMode() {
        return mode;
    }
}
