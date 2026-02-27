package com.example.xmpp.protocol.model.sasl;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * SASL 失败元素。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class SaslFailure implements ExtensionElement {

    public static final String NAMESPACE = XmppConstants.NS_XMPP_SASL;

    private final String condition;
    private final String text;

    /**
     * 获取元素名称。
     *
     * @return 元素名称
     */
    @Override
    public String getElementName() {
        return "failure";
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
        XmlStringBuilder xml = new XmlStringBuilder().openElement("failure", NAMESPACE);
        if (condition != null) {
            xml.emptyElement(condition);
        }
        return xml.optTextElement("text", text).closeElement("failure").toString();
    }

    /**
     * SASL 失败条件枚举。
     */
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

        /**
         * 获取条件字符串值。
         *
         * @return 条件字符串值
         */
        public String getValue() {
            return value;
        }
    }
}
