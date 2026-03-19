package com.example.xmpp.util;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.sasl.Auth;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import lombok.experimental.UtilityClass;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;

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
     * 生成 XML 日志摘要。
     *
     * <p>仅保留元素名、命名空间和少量结构属性，避免输出 XML 正文。</p>
     *
     * @param xml 原始 XML 字符串
     * @return 摘要字符串
     */
    public static String filterSensitiveXml(String xml) {
        return summarizeXml(xml);
    }

    /**
     * 生成 XML 日志摘要。
     *
     * <p>仅保留元素名、命名空间以及 id、type、from、to
     * 等结构信息。</p>
     *
     * @param xml 原始 XML 字符串
     * @return 摘要字符串
     */
    public static String summarizeXml(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }

        XMLEventReader reader = null;
        try {
            reader = XmlParserUtils.createInputFactory().createXMLEventReader(new StringReader(xml));
            while (reader.hasNext()) {
                var event = reader.nextEvent();
                if (event.isStartElement()) {
                    return buildSummary(event.asStartElement());
                }
            }
            return "xml";
        } catch (XMLStreamException e) {
            return "xml(unparseable)";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // Ignore close failure for log summarization.
                }
            }
        }
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
        appendSummaryField(summary, "xmlns", emptyToNull(element.getNamespace()));
        if (element instanceof Auth auth) {
            appendSummaryField(summary, "mechanism", auth.mechanism());
        }
        return summary.toString();
    }

    private static String buildSummary(StartElement element) {
        Map<String, String> attrs = XmlParserUtils.getAttributes(element);
        StringBuilder summary = new StringBuilder(element.getName().getLocalPart());
        appendSummaryField(summary, "xmlns", emptyToNull(element.getName().getNamespaceURI()));
        appendSummaryField(summary, "id", attrs.get("id"));
        appendSummaryField(summary, "type", attrs.get("type"));
        appendSummaryField(summary, "from", attrs.get("from"));
        appendSummaryField(summary, "to", attrs.get("to"));
        return summary.toString();
    }

    private static void appendSummaryField(StringBuilder summary, String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        summary.append(' ').append(name).append('=').append(value);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
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
        if (input == null || input.isEmpty()) {
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
