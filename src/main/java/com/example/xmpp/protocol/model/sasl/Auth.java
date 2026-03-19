package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmlStringBuilder;

import java.util.Map;

/**
 * SASL 认证元素，用于 XMPP SASL 握手流程。
 * <p>
 * 客户端通过发送 Auth 元素发起 SASL 认证，包含要使用的 SASL 机制名称
 * (如 PLAIN, SCRAM-SHA-1, SCRAM-SHA-256) 以及可选的初始认证响应数据。
 *
 * @since 2026-02-09
 */
public record Auth(String mechanism, String content) implements ExtensionElement {

    public static final String ELEMENT = "auth";
    public static final String NAMESPACE = XmppConstants.NS_XMPP_SASL;

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
     * @throws IllegalArgumentException 如果 mechanism 为 null 或空白
     */
    @Override
    public String toXml() {
        if (mechanism == null || mechanism.isBlank()) {
            throw new IllegalArgumentException("mechanism must not be null or blank");
        }

        return new XmlStringBuilder()
                .wrapElement(ELEMENT, NAMESPACE, Map.of("mechanism", mechanism), xml -> {
                    if (content != null) {
                        xml.append(content);
                    }
                })
                .toString();
    }
}
