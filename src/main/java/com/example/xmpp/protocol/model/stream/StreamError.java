package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * XMPP 流错误元素，实现 RFC 6120 §4.9 Stream Errors。
 * <p>
 * 当 XMPP 流发生错误时，服务端发送 stream:error 元素报告错误。
 * 包含预定义的条件代码 (如 bad-format、not-authorized、policy-violation 等)
 * 以及可选的错误描述文本和涉及的主机信息。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class StreamError implements ExtensionElement {
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:xmpp-streams";

    private final Condition condition;
    private final String text;
    private final String by;

    /**
     * 获取元素名称。
     *
     * @return 元素名称 "error"
     */
    @Override
    public String getElementName() {
        return "error";
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
     * @return XML 字符串表示
     */
    @Override
    public String toXml() {
        XmlStringBuilder xml = new XmlStringBuilder().openElement("stream:error");
        if (condition != null) {
            xml.append("<").append(condition.getElementName())
                    .append(" xmlns=\"").append(NAMESPACE).append("\"/>");
        }
        xml.optTextElement("text", NAMESPACE, text)
                .optTextElement("by", NAMESPACE, by);
        return xml.closeElement("stream:error").toString();
    }

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

        Condition(String elementName) {
            this.elementName = elementName;
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
         * 从字符串解析 Condition。
         *
         * @param name 条件名称，如 "bad-format"、"not-authorized"
         * @return 对应的 Condition 枚举值，如果无法解析则返回 UNDEFINED_CONDITION；如果 name 为 null 则返回 null
         */
        public static Condition fromString(String name) {
            if (name == null) {
                return null;
            }
            String normalized = name.toUpperCase().replace('-', '_');
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException e) {
                return UNDEFINED_CONDITION;
            }
        }
    }
}
