package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;

/**
 * XMPP 扩展元素标记接口。
 *
 * @since 2026-02-12
 */
public interface XmppExtension extends ExtensionElement {

    /**
     * 获取扩展元素名称。
     *
     * @return 元素名称
     */
    String getElementName();

    /**
     * 获取扩展元素命名空间。
     *
     * @return 命名空间
     */
    String getNamespace();
}
