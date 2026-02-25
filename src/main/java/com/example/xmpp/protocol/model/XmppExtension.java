package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;

/**
 * XMPP 扩展元素标记接口。
 *
 * 所有 XMPP 扩展元素（Packet Extensions）都应实现此接口。
 * 此接口继承 {@link ExtensionElement}，为 XMPP 特定扩展提供通用类型。
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
