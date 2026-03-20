package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * XMPP Resource Binding 扩展元素。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public final class Bind implements ExtensionElement {
    public static final String ELEMENT = "bind";
    public static final String NAMESPACE = XmppConstants.NS_XMPP_BIND;
    private final String jid;
    private final String resource;

    /**
     * 获取元素名称。
     *
     * @return 元素名称
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 序列化为 XML 字符串。
     *
     * @return XML 字符串
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .wrapElement(ELEMENT, NAMESPACE, xml -> {
                    if (resource != null) {
                        xml.wrapElement("resource", resource);
                    }
                    if (jid != null) {
                        xml.wrapElement("jid", jid);
                    }
                })
                .toString();
    }
}
