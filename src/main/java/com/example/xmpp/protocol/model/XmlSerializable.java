package com.example.xmpp.protocol.model;

/**
 * XML 可序列化接口。
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
