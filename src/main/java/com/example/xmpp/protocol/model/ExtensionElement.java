package com.example.xmpp.protocol.model;

import javax.xml.namespace.QName;

/**
 * XMPP 扩展元素接口。
 *
 * XMPP 扩展元素（Packet Extensions）是 XMPP 协议的可选扩展，
 * 用于在基本协议之上添加额外功能。例如：
 * <ul>
 *   <li>XEP-0004: Data Forms - 表单数据</li>
 *   <li>XEP-0054: vcard-temp - 电子名片</li>
 *   <li>XEP-0199: XMPP Ping - Ping/Pong 机制</li>
 * </ul>
 *
 * 实现此接口的类需要提供元素的本地名称（local name）和命名空间（namespace），
 * 以便正确序列化和解析 XML 数据。
 *
 * @since 2026-02-12
 */
public interface ExtensionElement extends XmlSerializable {

    /**
     * 获取扩展元素的根元素名称。
     *
     * @return 元素名称
     */
    String getElementName();

    /**
     * 获取扩展元素的 XML 命名空间。
     *
     * @return 命名空间
     */
    String getNamespace();

    /**
     * 获取扩展元素的 QName。
     *
     * <p>QName（Qualified Name）由命名空间 URI 和本地名称组成，
     * 用于在 XML 解析时唯一标识元素。</p>
     *
     * @return QName，格式为 {namespacelocalName}
     */
    default QName getQName() {
        return new QName(getNamespace(), getElementName());
    }
}
