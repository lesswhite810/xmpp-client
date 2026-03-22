package com.example.xmpp.protocol;

/**
 * 协议扩展 Provider 接口（SPI）。
 *
 * @param <P> Provider 类型
 * @since 2026-02-15
 */
public interface ProtocolProvider<P extends Provider<?>> {

    /**
     * 获取 XML 元素名称。
     *
     * @return 元素名称
     */
    String getElementName();

    /**
     * 获取 XML 命名空间。
     *
     * @return 命名空间
     */
    String getNamespace();

    /**
     * 创建 Provider 实例。
     *
     * @return Provider 实例
     */
    P createProvider();
}
