package com.example.xmpp.util;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.sasl.Auth;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import lombok.experimental.UtilityClass;



/**
 * 安全工具类。
 *
 * <p>提供敏感数据处理的安全方法，包括：</p>
 * <ul>
 *   <li>字符数组与字节数组的安全转换</li>
 *   <li>敏感数据的内存清理</li>
 *   <li>日志输出的敏感信息过滤</li>
 * </ul>
 *
 * @since 2026-02-13
 */
@UtilityClass
public class SecurityUtils {

    /**
     * 安全清理字符数组。
     *
     * @param chars 要清理的字符数组
     */
    public static void clear(char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }

    /**
     * 安全清理字节数组。
     *
     * @param bytes 要清理的字节数组
     */
    public static void clear(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /**
     * 将字符数组转换为字节数组（UTF-8）。
     *
     * <p>避免创建中间 String 对象，提高安全性。</p>
     *
     * @param chars 字符数组
     * @return 字节数组；如果输入为 null，则返回空字节数组
     */
    public static byte[] toBytes(char[] chars) {
        if (chars == null) {
            return new byte[0];
        }
        ByteBuffer buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * 生成扩展元素日志摘要。
     *
     * @param element 扩展元素
     * @return 摘要字符串
     */
    public static String summarizeExtensionElement(ExtensionElement element) {
        if (element == null) {
            return null;
        }

        StringBuilder summary = new StringBuilder(element.getElementName());
        appendField(emptyToNull(element.getNamespace()), summary, "xmlns");
        if (element instanceof Auth auth) {
            appendField(auth.mechanism(), summary, "mechanism");
        }
        return summary.toString();
    }

    /**
     * 生成 Stanza 日志摘要。
     *
     * @param stanza XMPP 节
     * @return 摘要字符串
     */
    public static String summarizeStanza(XmppStanza stanza) {
        if (stanza == null) {
            return null;
        }

        return switch (stanza) {
            case Iq iq -> summarizeStanza(iq);
            case Message message -> summarizeBasicStanza(message);
            case Presence presence -> summarizeBasicStanza(presence);
            default -> stanza.getClass().getSimpleName();
        };
    }

    private static String summarizeStanza(Iq iq) {
        StringBuilder summary = new StringBuilder(iq.getElementName());
        appendField(iq.getType(), summary, "type");
        appendField(iq.getId(), summary, "id");
        appendField(iq.getFrom(), summary, "from");
        appendField(iq.getTo(), summary, "to");
        var child = iq.getChildElement();
        if (child != null) {
            appendField(child.getElementName(), summary, "child");
            appendField(emptyToNull(child.getNamespace()), summary, "childNs");
        }
        return summary.toString();
    }

    private static String summarizeBasicStanza(Message message) {
        return summarizeBasicStanza(message.getElementName(),
                typeToString(message.getType()), message.getId(),
                message.getFrom(), message.getTo());
    }

    private static String summarizeBasicStanza(Presence presence) {
        return summarizeBasicStanza(presence.getElementName(),
                typeToString(presence.getType()), presence.getId(),
                presence.getFrom(), presence.getTo());
    }

    private static String summarizeBasicStanza(String elementName, String type, String id, String from, String to) {
        StringBuilder summary = new StringBuilder(elementName);
        appendField(type, summary, "type");
        appendField(id, summary, "id");
        appendField(from, summary, "from");
        appendField(to, summary, "to");
        return summary.toString();
    }

    private static <T> void appendField(T value, StringBuilder summary, String name) {
        if (value != null) {
            summary.append(' ').append(name).append('=').append(value);
        }
    }

    private static String typeToString(Enum<?> type) {
        return type != null ? type.toString() : null;
    }

    private static String emptyToNull(String value) {
        return StringUtils.defaultIfEmpty(value, null);
    }

    /**
     * 转义 XML 属性值中的特殊字符，防止 XML 注入攻击。
     *
     * <p>转义以下字符：</p>
     * <ul>
     *   <li>&amp; -> &amp;amp;</li>
     *   <li>&lt; -> &amp;lt;</li>
     *   <li>&gt; -> &amp;gt;</li>
     *   <li>&quot; -> &amp;quot;</li>
     *   <li>&apos; -> &amp;apos;</li>
     * </ul>
     *
     * @param input 原始字符串
     * @return 转义后的字符串；如果输入为 null 则返回 null
     */
    public static String escapeXmlAttribute(String input) {
        if (StringUtils.isEmpty(input)) {
            return input;
        }

        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            sb.append(switch (c) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '\'' -> "&apos;";
                default -> String.valueOf(c);
            });
        }

        return sb.toString();
    }
}
