package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * XMPP Resource Binding 扩展元素，实现 XEP-0198 (Resource Binding)。
 * <p>
 * 用于在 SASL 认证成功后，将客户端绑定到一个资源标识符 (JID)。
 * 服务器响应包含完整的 JID (包含节点、域名和资源)，客户端也可在请求中指定资源名。
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
     * @return 元素名称 "bind"
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间 "urn:ietf:params:xml:ns:xmpp-bind"
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
        return new XmlStringBuilder()
                .openElement("bind", NAMESPACE)
                .optTextElement("resource", resource)
                .optTextElement("jid", jid)
                .closeElement("bind")
                .toString();
    }
}
