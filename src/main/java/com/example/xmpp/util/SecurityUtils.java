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
     * 安全清理字符数组（防止 JVM 优化）。
     *
     * <p>使用 volatile 读来防止 JVM 优化掉内存清零操作。</p>
     *
     * @param chars 要清理的字符数组
     */
    public static void clear(char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
            // Volatile 读防止 JVM 优化掉上面的 fill 操作
            dummyRead(chars);
        }
    }

    /**
     * 安全清理字节数组（防止 JVM 优化）。
     *
     * <p>使用 volatile 读来防止 JVM 优化掉内存清零操作。</p>
     *
     * @param bytes 要清理的字节数组
     */
    public static void clear(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
            // Volatile 读防止 JVM 优化掉上面的 fill 操作
            dummyRead(bytes);
        }
    }

    /**
     * Dummy read to prevent JVM from optimizing away the memory clear operation.
     * The volatile read creates a memory barrier that ensures the fill operation is not elided.
     */
    private static void dummyRead(char[] arr) {
        // 实际读取数组的长度和第一个元素，防止 JVM 优化掉内存清零操作
        if (arr.length > 0) {
            // 读取第一个元素（虽然值已被清零），确保内存可见性
            volatileBarrier(arr[0]);
        }
    }

    private static void dummyRead(byte[] arr) {
        if (arr.length > 0) {
            volatileBarrier(arr[0]);
        }
    }

    private static volatile int barrier = 0;

    private static void volatileBarrier(char c) {
        // volatile 读写会创建内存屏障，同时使用参数确保不会被优化掉
        barrier = barrier + c;
    }

    private static void volatileBarrier(byte b) {
        barrier = barrier + b;
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
     * 将字符串转换为字符数组。
     *
     * @param str 字符串
     * @return 字符数组（副本）
     */
    public static char[] toCharArray(String str) {
        if (str == null) {
            return null;
        }
        return str.toCharArray();
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
        // 使用预编译正则一次性替换所有敏感元素（性能优化）
        return SENSITIVE_ELEMENTS_PATTERN.matcher(xml)
                .replaceAll("<$1$2>" + MASK + "</$1>");
    }

    /**
     * 检查字符串是否为 null 或空。
     *
     * @param str 要检查的字符串
     * @return 如果为 null 或空返回 true
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 转义 XML 元素内容中的特殊字符，防止 XML 注入攻击。
     *
     * <p>转义以下字符：</p>
     * <ul>
     *   <li>&amp; -> &amp;amp;</li>
     *   <li>&lt; -> &amp;lt;</li>
     *   <li>&gt; -> &amp;gt;</li>
     * </ul>
     *
     * @param input 原始字符串
     * @return 转义后的字符串，如果输入为 null 则返回 null
     */
    public static String escapeXml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder sb = null;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            String replacement = switch (c) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                default -> null;
            };

            if (replacement != null) {
                if (sb == null) {
                    sb = new StringBuilder(input.length() + 16);
                    sb.append(input, 0, i);
                }
                sb.append(replacement);
            } else if (sb != null) {
                sb.append(c);
            }
        }

        return sb != null ? sb.toString() : input;
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
     * <p>与 {@link #escapeXml(String)} 的区别是额外转义引号，
     * 因为属性值通常用引号包围。</p>
     *
     * @param input 原始字符串
     * @return 转义后的字符串，如果输入为 null 则返回 null
     */
    public static String escapeXmlAttribute(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder sb = null;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            String replacement = switch (c) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '\'' -> "&apos;";
                default -> null;
            };

            if (replacement != null) {
                if (sb == null) {
                    sb = new StringBuilder(input.length() + 16);
                    sb.append(input, 0, i);
                }
                sb.append(replacement);
            } else if (sb != null) {
                sb.append(c);
            }
        }

        return sb != null ? sb.toString() : input;
    }

    /**
     * 安全比较两个字符数组（常量时间）。
     *
     * <p>即使长度不等也会进行完整的比较操作，以防止计时攻击。</p>
     *
     * @param a 第一个数组
     * @param b 第二个数组
     * @return 如果相等返回 true
     */
    public static boolean constantTimeEquals(char[] a, char[] b) {
        if (a == null || b == null) {
            return a == b;
        }

        // 即使长度不等也继续比较，保持常量时间
        int maxLen = Math.max(a.length, b.length);
        int result = a.length ^ b.length; // 长度差异记录在 result 中

        for (int i = 0; i < maxLen; i++) {
            // 使用安全索引，超出范围时与自身比较
            char charA = i < a.length ? a[i] : 0;
            char charB = i < b.length ? b[i] : 0;
            result |= charA ^ charB;
        }
        return result == 0;
    }

    /**
     * 安全比较两个字节数组（常量时间）。
     *
     * <p>即使长度不等也会进行完整的比较操作，以防止计时攻击。</p>
     *
     * @param a 第一个数组
     * @param b 第二个数组
     * @return 如果相等返回 true
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }

        // 即使长度不等也继续比较，保持常量时间
        int maxLen = Math.max(a.length, b.length);
        int result = a.length ^ b.length; // 长度差异记录在 result 中

        for (int i = 0; i < maxLen; i++) {
            // 使用安全索引，超出范围时与自身比较
            byte byteA = i < a.length ? a[i] : 0;
            byte byteB = i < b.length ? b[i] : 0;
            result |= byteA ^ byteB;
        }
        return result == 0;
    }
}
