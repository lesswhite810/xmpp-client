package com.example.xmpp.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

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

    private static final String MASK = "*****";

    /**
     * 预编译的敏感元素匹配模式（性能优化）。
     *
     * <p>匹配 auth、response、challenge、success、password 元素。</p>
     *
     * <p>模式说明：</p>
     * <ul>
     *   <li>(?:\w+:)? - 可选的命名空间前缀（如 sasl:auth）</li>
     *   <li>\s* - 标签名后可能的空白</li>
     *   <li>[^>]* - 属性部分</li>
     *   <li>\s*> - 结束尖括号前可能的空白</li>
     * </ul>
     *
     * <p>注意：此正则用于日志过滤，不是安全边界。对于安全敏感的操作，
     * 应使用 XML 解析器而非正则表达式。</p>
     */
    private static final Pattern SENSITIVE_ELEMENTS_PATTERN = Pattern.compile(
            "<(?:\\w+:)?(auth|response|challenge|success|password)([^>]*)\\s*>([^<]*)</(?:\\w+:)?\\1\\s*>",
            Pattern.CASE_INSENSITIVE
    );

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
     * @return 字节数组
     */
    public static byte[] toBytes(char[] chars) {
        if (chars == null) {
            return null;
        }
        ByteBuffer buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * 过滤 XML 中的敏感信息用于日志输出（性能优化版本）。
     *
     * <p>过滤以下元素的 content：</p>
     * <ul>
     *   <li>auth - SASL 认证</li>
     *   <li>response - SASL 响应</li>
     *   <li>challenge - SASL 挑战</li>
     *   <li>success - SASL 成功</li>
     *   <li>password - 密码元素</li>
     * </ul>
     *
     * @param xml 原始 XML 字符串
     * @return 过滤后的安全字符串
     */
    public static String filterSensitiveXml(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }
        return SENSITIVE_ELEMENTS_PATTERN.matcher(xml)
                .replaceAll("<$1$2>" + MASK + "</$1>");
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
     * @return 转义后的字符串，如果输入为 null 则返回 null
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
