package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

/**
 * SASL 认证元素，用于 XMPP SASL 握手流程。
 * <p>
 * 客户端通过发送 Auth 元素发起 SASL 认证，包含要使用的 SASL 机制名称
 * (如 PLAIN, SCRAM-SHA-1, SCRAM-SHA-256) 以及可选的初始认证响应数据。
 *
 * @since 2026-02-09
 */
@Getter
public final class Auth implements ExtensionElement {

    public static final String ELEMENT = "auth";
    public static final String NAMESPACE = XmppConstants.NS_XMPP_SASL;

    private final String mechanism;
    private final String content;

    /**
     * 构造 Auth 实例。
     *
     * @param mechanism SASL 机制名称，如 "PLAIN"、"SCRAM-SHA-1"、"SCRAM-SHA-256"
     * @param content 初始认证响应数据，可为 null（表示不带初始响应）
     */
    public Auth(String mechanism, String content) {
        this.mechanism = mechanism;
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
                .element(ELEMENT, NAMESPACE)
                .attribute("mechanism", mechanism)
                .rightAngleBracket()
                .append(content != null ? content : "")
                .closeElement(ELEMENT)
                .toString();
    }
}
