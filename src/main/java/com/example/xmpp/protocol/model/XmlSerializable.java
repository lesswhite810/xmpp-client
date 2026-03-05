package com.example.xmpp.protocol.model;

/**
 * XML 可序列化接口。
 *
 * 此接口定义了 XMPP 协议中所有可序列化对象的通用契约。
 * 实现此接口的类必须能够将其内部状态转换为符合 XMPP 规范的 XML 字符串。
 *
 * 该接口主要用于：
 * <ul>
 *   <li>XMPP 节的序列化和反序列化</li>
 *   <li>扩展元素的 XML 表示</li>
 *   <li>协议特性的流式输出</li>
 * </ul>
 *
 * @since 2026-02-24
 */
public interface XmlSerializable {

    /**
     * 序列化为 XML 字符串。
     *
     * @return XML 字符串
     */
    String toXml();
}
