package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

/**
 * XMPP 错误节元素。
 *
 * 表示 XMPP 错误条件，包括错误类型、条件、可选的文本描述和可选的应用特定扩展。
 *
 * @since 2026-02-09
 */
@Getter
public class XmppError implements XmppExtension {

    /** 错误元素名称 */
    public static final String ELEMENT = "error";
    /** XMPP 节命名空间 */
    public static final String NAMESPACE = XmppConstants.NS_XMPP_STANZAS;

    /** 错误条件 */
    private final Condition condition;
    /** 错误描述 */
    private final String text;
    /** 错误类型 */
    private final Type type;
    /** 应用特定扩展 */
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
     * 构建器。
     *
     * @since 2026-02-09
     */
    public static class Builder {
        /** 错误条件 */
        private Condition condition;
        /** 错误描述 */
        private String text;
        /** 错误类型 */
        private Type type;
        /** 应用特定扩展 */
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
     *
     * @since 2026-02-09
     */
    public enum Condition {
        /** 错误请求 */
        bad_request("bad-request", Type.MODIFY),
        /** 冲突 */
        conflict("conflict", Type.CANCEL),
        /** 功能未实现 */
        feature_not_implemented("feature-not-implemented", Type.CANCEL),
        /** 禁止 */
        forbidden("forbidden", Type.AUTH),
        /** 已离开 */
        gone("gone", Type.MODIFY),
        /** 内部服务器错误 */
        internal_server_error("internal-server-error", Type.WAIT),
        /** 项目未找到 */
        item_not_found("item-not-found", Type.CANCEL),
        /** JID 格式错误 */
        jid_malformed("jid-malformed", Type.MODIFY),
        /** 不可接受 */
        not_acceptable("not-acceptable", Type.MODIFY),
        /** 不允许 */
        not_allowed("not-allowed", Type.CANCEL),
        /** 未授权 */
        not_authorized("not-authorized", Type.AUTH),
        /** 策略违规 */
        policy_violation("policy-violation", Type.MODIFY),
        /** 接收者不可用 */
        recipient_unavailable("recipient-unavailable", Type.WAIT),
        /** 重定向 */
        redirect("redirect", Type.MODIFY),
        /** 需要注册 */
        registration_required("registration-required", Type.AUTH),
        /** 远程服务器未找到 */
        remote_server_not_found("remote-server-not-found", Type.CANCEL),
        /** 远程服务器超时 */
        remote_server_timeout("remote-server-timeout", Type.WAIT),
        /** 资源限制 */
        resource_constraint("resource-constraint", Type.WAIT),
        /** 服务不可用 */
        service_unavailable("service-unavailable", Type.CANCEL),
        /** 需要订阅 */
        subscription_required("subscription-required", Type.AUTH),
        /** 未定义条件 */
        undefined_condition("undefined-condition", Type.WAIT),
        /** 意外请求 */
        unexpected_request("unexpected-request", Type.WAIT);

        /** 元素名称 */
        private final String elementName;
        /** 默认类型 */
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
     * 错误类型枚举。
     *
     * @since 2026-02-09
     */
    public enum Type {
        /** 认证错误 */
        AUTH,
        /** 取消错误 */
        CANCEL,
        /** 继续错误 */
        CONTINUE_,
        /** 修改错误 */
        MODIFY,
        /** 等待错误 */
        WAIT;

        @Override
        public String toString() {
            return name().toLowerCase().replace("_", "-");
        }
    }
}
