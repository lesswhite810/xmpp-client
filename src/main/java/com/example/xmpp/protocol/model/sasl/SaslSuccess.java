package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * SASL 成功元素，表示 SASL 认证流程成功完成。
 * <p>
 * 服务端发送 Success 元素表示客户端提供的认证凭证有效，认证成功。
 * 可能包含可选的额外响应数据 (Base64 编码)。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class SaslSuccess implements ExtensionElement {

    /**
     * 元素名称。
     */
    public static final String ELEMENT = "success";

    /**
     * SASL 命名空间。
     */
    public static final String NAMESPACE = XmppConstants.NS_XMPP_SASL;

    /**
     * 可选的成功响应内容，通常为 Base64 编码的字符串。
     */
    private final String content;

    /**
     * 获取元素名称。
     *
     * @return 固定返回 {@code success}
     */
    @Override
    public String getElementName() {
        return "success";
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
     * @return 成功元素 XML 字符串
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .openElement("success", NAMESPACE)
                .escapedContent(content)
                .closeElement("success")
                .toString();
    }
}
