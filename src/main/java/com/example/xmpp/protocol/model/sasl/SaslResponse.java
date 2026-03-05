package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

/**
 * SASL 响应元素，用于 XMPP SASL 握手流程。
 * <p>
 * 客户端通过发送 Response 元素响应服务端的 Challenge。
 * 该元素包含认证过程中所需的响应数据，通常为 Base64 编码。
 *
 * @since 2026-02-09
 */
@Getter
public final class SaslResponse implements ExtensionElement {

    public static final String ELEMENT = "response";
    public static final String NAMESPACE = XmppConstants.NS_XMPP_SASL;

    private final String content;

    /**
     * 构造 SaslResponse 实例。
     *
     * @param content 响应数据，通常为 Base64 编码的认证响应，可为 null
     */
    public SaslResponse(String content) {
        this.content = content;
    }

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
     * 转换为 XML 字符串。
     *
     * @return XML 字符串
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .openElement(ELEMENT, NAMESPACE)
                .append(content != null ? content : "")
                .closeElement(ELEMENT)
                .toString();
    }
}
