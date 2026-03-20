package com.example.xmpp.protocol.model;

import javax.xml.namespace.QName;

/**
 * XMPP 扩展元素接口。
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
     * @return QName
     */
    default QName getQName() {
        return new QName(getNamespace(), getElementName());
    }
}
