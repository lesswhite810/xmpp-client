package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * XMPP 错误节元素。
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
     * 序列化为 XML 字符串。
     *
     * @return XML 字符串
     */
    @Override
    public String toXml() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("type", type);
        return new XmlStringBuilder()
                .wrapElement(ELEMENT, attributes, xml -> {
                    if (condition != null) {
                        xml.wrapElement(condition.getElementName(), NAMESPACE, "");
                    }
                    if (text != null) {
                        xml.wrapElement("text", NAMESPACE, text);
                    }
                    if (extension != null) {
                        xml.append(extension.toXml());
                    }
                })
                .toString();
    }

    /**
     * 构建器。
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
     * 错误条件枚举。
     */
    @Getter
    public enum Condition {
        BAD_REQUEST("bad-request", Type.MODIFY),
        CONFLICT("conflict", Type.CANCEL),
        FEATURE_NOT_IMPLEMENTED("feature-not-implemented", Type.CANCEL),
        FORBIDDEN("forbidden", Type.AUTH),
        GONE("gone", Type.MODIFY),
        INTERNAL_SERVER_ERROR("internal-server-error", Type.WAIT),
        ITEM_NOT_FOUND("item-not-found", Type.CANCEL),
        JID_MALFORMED("jid-malformed", Type.MODIFY),
        NOT_ACCEPTABLE("not-acceptable", Type.MODIFY),
        NOT_ALLOWED("not-allowed", Type.CANCEL),
        NOT_AUTHORIZED("not-authorized", Type.AUTH),
        POLICY_VIOLATION("policy-violation", Type.MODIFY),
        RECIPIENT_UNAVAILABLE("recipient-unavailable", Type.WAIT),
        REDIRECT("redirect", Type.MODIFY),
        REGISTRATION_REQUIRED("registration-required", Type.AUTH),
        REMOTE_SERVER_NOT_FOUND("remote-server-not-found", Type.CANCEL),
        REMOTE_SERVER_TIMEOUT("remote-server-timeout", Type.WAIT),
        RESOURCE_CONSTRAINT("resource-constraint", Type.WAIT),
        SERVICE_UNAVAILABLE("service-unavailable", Type.CANCEL),
        SUBSCRIPTION_REQUIRED("subscription-required", Type.AUTH),
        UNDEFINED_CONDITION("undefined-condition", Type.WAIT),
        UNEXPECTED_REQUEST("unexpected-request", Type.WAIT);

        private final String elementName;
        private final Type defaultType;

        Condition(String elementName, Type defaultType) {
            this.elementName = elementName;
            this.defaultType = defaultType;
        }

        /**
         * 从字符串解析错误条件。
         *
         * @param name 条件名称
         * @return 错误条件
         */
        public static Condition fromString(String name) {
            if (name == null) {
                return UNDEFINED_CONDITION;
            }
            String normalized = name.toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException e) {
                return UNDEFINED_CONDITION;
            }
        }
    }

    /**
     * 错误类型枚举。
     */
    public enum Type {
        AUTH,
        CANCEL,
        CONTINUE_,
        MODIFY,
        WAIT;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replace("_", "-");
        }
    }
}
