package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

/**
 * SASL 失败元素。
 *
 * @since 2026-02-09
 */
public record SaslFailure(String condition, String text) implements ExtensionElement {

    /**
     * 元素名称。
     */
    public static final String ELEMENT = "failure";

    /**
     * SASL 命名空间。
     */
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
     * @return SASL 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 转换为 XML 字符串。
     *
     * @return 失败元素 XML 字符串
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .wrapElement(ELEMENT, NAMESPACE, xml -> {
                    if (condition != null) {
                        xml.wrapElement(condition, "");
                    }
                    if (text != null) {
                        xml.wrapElement("text", text);
                    }
                })
                .toString();
    }

    /**
     * SASL 失败条件枚举。
     */
    @Getter
    public enum Condition {
        ABORTED("aborted"),
        CREDENTIALS_EXPIRED("credentials-expired"),
        ENCRYPTION_REQUIRED("encryption-required"),
        INCORRECT_ENCODING("incorrect-encoding"),
        INVALID_AUTHZID("invalid-authzid"),
        INVALID_MECHANISM("invalid-mechanism"),
        INVALID_REALM("invalid-realm"),
        MECHANISM_TOO_WEAK("mechanism-too-weak"),
        NOT_AUTHORIZED("not-authorized"),
        TEMPORARY_AUTH_FAILURE("temporary-auth-failure");

        private final String value;

        /**
         * 创建条件枚举实例。
         *
         * @param value 条件字符串值
         */
        Condition(String value) {
            this.value = value;
        }

    }
}
