package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;

/**
 * XMPP Ping 扩展元素，实现 XEP-0199 (Ping)。
 * <p>
 * 用于检测 XMPP 连接的活跃状态。客户端或服务器均可发送 ping 请求，
 * 对方需返回 Iq 响应。该类提供空元素的序列化表示。
 *
 * @since 2026-02-09
 */
public final class Ping implements ExtensionElement {
    public static final String ELEMENT = "ping";
    public static final String NAMESPACE = XmppConstants.NS_XMPP_PING;

    public static final Ping INSTANCE = new Ping();

    /**
     * 构造 Ping 实例。
     */
    public Ping() {
    }

    /**
     * 获取元素名称。
     *
     * @return 元素名称 "ping"
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间 "urn:xmpp:ping"
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 序列化为 XML 字符串。
     *
     * @return XML 字符串表示
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder().wrapElement(ELEMENT, NAMESPACE, "").toString();
    }
}
