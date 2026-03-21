package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

import java.util.Locale;

/**
 * XMPP 流错误元素。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class StreamError implements ExtensionElement {
    public static final String ELEMENT = "error";

    /**
     * 流错误命名空间。
     */
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:xmpp-streams";

    /**
     * 流错误条件。
     */
    private final Condition condition;

    /**
     * 可选的错误描述文本。
     */
    private final String text;

    /**
     * 可选的错误来源主机。
     */
    private final String by;

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
        return new XmlStringBuilder()
                .wrapElement("stream:error", xml -> {
                    if (condition != null) {
                        xml.wrapElement(condition.getElementName(), NAMESPACE, "");
                    }
                    if (text != null) {
                        xml.wrapElement("text", NAMESPACE, text);
                    }
                    if (by != null) {
                        xml.wrapElement("by", NAMESPACE, by);
                    }
                })
                .toString();
    }

    /**
     * 流错误条件枚举。
     */
    @Getter
    public enum Condition {
        BAD_FORMAT("bad-format"),
        BAD_NAMESPACE_PREFIX("bad-namespace-prefix"),
        CONFLICT("conflict"),
        CONNECTION_TIMEOUT("connection-timeout"),
        HOST_GONE("host-gone"),
        HOST_UNKNOWN("host-unknown"),
        IMPROPER_ADDRESSING("improper-addressing"),
        INTERNAL_SERVER_ERROR("internal-server-error"),
        INVALID_FROM("invalid-from"),
        INVALID_ID("invalid-id"),
        INVALID_NAMESPACE("invalid-namespace"),
        INVALID_XML("invalid-xml"),
        NOT_AUTHORIZED("not-authorized"),
        POLICY_VIOLATION("policy-violation"),
        REMOTE_CONNECTION_FAILED("remote-connection-failed"),
        RESET("reset"),
        RESOURCE_CONSTRAINT("resource-constraint"),
        RESTRICTED_XML("restricted-xml"),
        SEE_OTHER_HOST("see-other-host"),
        SYSTEM_SHUTDOWN("system-shutdown"),
        UNDEFINED_CONDITION("undefined-condition"),
        UNSUPPORTED_ENCODING("unsupported-encoding"),
        UNSUPPORTED_FEATURE("unsupported-feature"),
        UNSUPPORTED_NAMESPACE("unsupported-namespace"),
        UNSUPPORTED_STANZA_TYPE("unsupported-stanza-type"),
        UNSUPPORTED_VERSION("unsupported-version");

        private final String elementName;

        /**
         * 创建流错误条件枚举实例。
         *
         * @param elementName 条件对应的元素名称
         */
        Condition(String elementName) {
            this.elementName = elementName;
        }

        /**
         * 从字符串解析 Condition。
         *
         * @param name 条件名称，如 "bad-format"、"not-authorized"
         * @return 对应的 Condition 枚举值；如果无法解析或参数为 null，则返回 {@link #UNDEFINED_CONDITION}
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
}
