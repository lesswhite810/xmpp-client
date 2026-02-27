package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

/**
 * SASL 响应元素。
 *
 * @since 2026-02-09
 */
@Getter
public final class SaslResponse implements ExtensionElement {

    public static final String ELEMENT = "response";
    public static final String NAMESPACE = XmppConstants.NS_XMPP_SASL;

    private final String content;

    /**
     * 创建 SaslResponse 实例。
     *
     * @param content 响应内容
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
