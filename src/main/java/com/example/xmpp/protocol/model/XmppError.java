package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

/**
 * XMPP 错误节元素。
 *
 * <p>表示 XMPP 错误条件，包括错误类型、条件、可选的文本描述和可选的应用特定扩展。</p>
 *
 * @since 2026-02-09
 */
@Getter
public class XmppError implements XmppExtension {

    public static final String ELEMENT = "error";
    public static final String NAMESPACE = XmppConstants.NS_XMPP_STANZAS;

    private final Condition condition;
    private final String text;
    private final Type type;
    private final XmppExtension extension;

    private XmppError(Builder builder) {
        this.condition = builder.condition;
        this.text = builder.text;
        this.type = builder.type != null ? builder.type
                : (condition != null ? condition.getDefaultType() : null);
        this.extension = builder.extension;
    }

    /**
     * 获取元素名称。
     *
     * @return "error"
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return XMPP 节命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 序列化为 XML 字符串。
     *
     * @return XML 字符串
     */
    @Override
    public String toXml() {
        XmlStringBuilder xml = new XmlStringBuilder().element("error");
        if (type != null) {
            xml.attribute("type", type);
        }
        xml.rightAngleBracket();

        if (condition != null) {
            xml.append('<').append(condition.getElementName())
                    .append(" xmlns=\"").append(NAMESPACE).append("\"/>");
        }

        xml.optTextElement("text", NAMESPACE, text);

        if (extension != null) {
            xml.append(extension.toXml());
        }

        return xml.closeElement("error").toString();
    }

    /**
     * 构建器，用于构造 XmppError 实例。
     *
     */
    public static class Builder {
        private Condition condition;
        private String text;
        private Type type;
        private XmppExtension extension;

        /**
         * 创建构建器。
         *
         * @param condition 错误条件
         */
        public Builder(Condition condition) {
            this.condition = condition;
        }

        /**
         * 设置错误描述。
         *
         * @param text 错误描述
         * @return this
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * 设置错误类型。
         *
         * @param type 错误类型
         * @return this
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * 设置应用特定扩展。
         *
         * @param extension 扩展元素
         * @return this
         */
        public Builder extension(XmppExtension extension) {
            this.extension = extension;
            return this;
        }

        /**
         * 构建 XmppError。
         *
         * @return XmppError 实例
         */
        public XmppError build() {
            return new XmppError(this);
        }
    }

    /**
     * 错误条件枚举，定义了 XMPP 协议中常用的错误条件。
     *
     */
    public enum Condition {
        bad_request("bad-request", Type.MODIFY),
        conflict("conflict", Type.CANCEL),
        feature_not_implemented("feature-not-implemented", Type.CANCEL),
        forbidden("forbidden", Type.AUTH),
        gone("gone", Type.MODIFY),
        internal_server_error("internal-server-error", Type.WAIT),
        item_not_found("item-not-found", Type.CANCEL),
        jid_malformed("jid-malformed", Type.MODIFY),
        not_acceptable("not-acceptable", Type.MODIFY),
        not_allowed("not-allowed", Type.CANCEL),
        not_authorized("not-authorized", Type.AUTH),
        policy_violation("policy-violation", Type.MODIFY),
        recipient_unavailable("recipient-unavailable", Type.WAIT),
        redirect("redirect", Type.MODIFY),
        registration_required("registration-required", Type.AUTH),
        remote_server_not_found("remote-server-not-found", Type.CANCEL),
        remote_server_timeout("remote-server-timeout", Type.WAIT),
        resource_constraint("resource-constraint", Type.WAIT),
        service_unavailable("service-unavailable", Type.CANCEL),
        subscription_required("subscription-required", Type.AUTH),
        undefined_condition("undefined-condition", Type.WAIT),
        unexpected_request("unexpected-request", Type.WAIT);

        private final String elementName;
        private final Type defaultType;

        Condition(String elementName, Type defaultType) {
            this.elementName = elementName;
            this.defaultType = defaultType;
        }

        /**
         * 获取元素名称。
         *
         * @return 元素名称
         */
        public String getElementName() {
            return elementName;
        }

        /**
         * 获取默认类型。
         *
         * @return 默认类型
         */
        public Type getDefaultType() {
            return defaultType;
        }

        /**
         * 从字符串解析错误条件。
         *
         * @param name 条件名称
         * @return 错误条件枚举值
         */
        public static Condition fromString(String name) {
            if (name == null) {
                return null;
            }
            String normalized = name.toLowerCase().replace('-', '_');
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException e) {
                return undefined_condition;
            }
        }
    }

    /**
     * 错误类型枚举，表示错误的状态和性质。
     *
     */
    public enum Type {
        AUTH,
        CANCEL,
        CONTINUE_,
        MODIFY,
        WAIT;

        @Override
        public String toString() {
            return name().toLowerCase().replace("_", "-");
        }
    }
}
