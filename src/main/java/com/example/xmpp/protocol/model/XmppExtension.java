package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;

/**
 * XMPP 扩展元素标记接口。
 *
 * 所有 XMPP 扩展元素（Packet Extensions）都应实现此接口。
 * 此接口继承 {@link ExtensionElement}，为 XMPP 特定扩展提供通用类型标记。
 *
 * 常见的 XMPP 扩展实现包括：
 * <ul>
 *   <li><bind> - 资源绑定（RFC 6121）</li>
 *   <li><session> - 会话建立（RFC 6121）</li>
 *   <li><ping> - Ping 请求（XEP-0199）</li>
 * </ul>
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
