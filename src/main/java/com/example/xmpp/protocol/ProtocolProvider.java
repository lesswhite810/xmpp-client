package com.example.xmpp.protocol;

/**
 * 协议扩展 Provider 接口（SPI）。
 *
 * <p>实现此接口并在 META-INF/services/com.example.xmpp.protocol.ProtocolProvider 中注册，
 * 即可自动扩展 XMPP 协议解析器。ProviderRegistry 会通过 Java ServiceLoader 自动发现并加载。</p>
 *
 * @since 2026-02-15
 */
public interface ProtocolProvider {

    /**
     * 获取 XML 元素名称。
     *
     * @return 元素本地名称（如 "bind"、"ping"）
     */
    String getElementName();

    /**
     * 获取 XML 命名空间。
     *
     * @return 命名空间 URI（如 "urn:ietf:params:xml:ns:xmpp-bind"）
     */
    String getNamespace();

    /**
     * 创建 Provider 实例。
     *
     * @return 新的 Provider 实例
     */
    Provider<?> createProvider();
}
