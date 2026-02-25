package com.example.xmpp.protocol;

/**
 * 协议扩展 Provider 接口（SPI）。
 *
 * <p>实现此接口并在 META-INF/services/com.example.xmpp.protocol.ProtocolProvider 中注册，
 * 即可自动扩展 XMPP 协议解析器。ProviderRegistry 会通过 Java ServiceLoader 自动发现并加载。</p>
 *
 * @param <T> Provider 处理的对象类型
 * @since 2026-02-15
 */
public interface ProtocolProvider<T> {

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
     * 获取优先级。
     *
     * <p>当多个 Provider 处理相同元素/命名空间时，优先级高的优先使用，数值越大优先级越高。
     * 建议的优先级范围：0-99 为内置默认 Provider，100-199 为标准扩展 Provider，200+ 为自定义高优先级 Provider。</p>
     *
     * @return 优先级数值，默认 100
     */
    default int getPriority() {
        return 100;
    }

    /**
     * 创建 Provider 实例。
     *
     * @return 新的 Provider 实例
     */
    Provider<T> createProvider();
}
