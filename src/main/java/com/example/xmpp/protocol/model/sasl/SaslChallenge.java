package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * SASL 挑战元素。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class SaslChallenge implements ExtensionElement {

    public static final String NAMESPACE = XmppConstants.NS_XMPP_SASL;

    private final String content;

    /**
     * 创建 SaslChallenge 实例。
     *
     * @param content 挑战数据
     * @return SaslChallenge 实例
     */
    public static SaslChallenge of(String content) {
        return new SaslChallenge(content);
    }

    /**
     * 获取元素名称。
     *
     * @return 元素名称
     */
    @Override
    public String getElementName() {
        return "challenge";
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
                .openElement("challenge", NAMESPACE)
                .escapedContent(content)
                .closeElement("challenge")
                .toString();
    }
}
