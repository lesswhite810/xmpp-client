package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmlStringBuilder;

/**
 * SASL 挑战元素，用于 XMPP SASL 握手流程。
 * <p>
 * 服务端发送 Challenge 元素向客户端请求额外的认证数据。
 * 客户端需响应 SaslResponse 元素。该元素通常包含 Base64 编码的挑战数据。
 *
 * @since 2026-02-09
 */
public record SaslChallenge(String content) implements ExtensionElement {

    /**
     * 元素名称。
     */
    public static final String ELEMENT = "challenge";

    /**
     * SASL 命名空间。
     */
    public static final String NAMESPACE = XmppConstants.NS_XMPP_SASL;

    /**
     * 创建 SaslChallenge 实例。
     *
     * @param content 挑战数据，通常为 Base64 编码的字符串，可为 null
     * @return 新创建的 SaslChallenge 实例
     */
    public static SaslChallenge of(String content) {
        return new SaslChallenge(content);
    }

    /**
     * 获取元素名称。
     *
     * @return 固定返回 challenge
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return SASL 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 转换为 XML 字符串。
     *
     * @return 挑战元素 XML 字符串
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .wrapElement(ELEMENT, NAMESPACE, content)
                .toString();
    }
}
