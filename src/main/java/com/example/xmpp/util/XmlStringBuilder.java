package com.example.xmpp.util;

/**
 * XML 字符串构建器。
 *
 * <p>提供流式 API 构建 XML 文档，自动处理 XML 特殊字符转义。</p>
 *
 * @since 2026-02-09
 */
public class XmlStringBuilder {

    private final StringBuilder sb;

    private static final String AMP = "&amp;";
    private static final String LT = "&lt;";
    private static final String GT = "&gt;";
    private static final String QUOT = "&quot;";
    private static final String APOS = "&apos;";

    /**
     * 构造空的 XmlStringBuilder。
     */
    public XmlStringBuilder() {
        this.sb = new StringBuilder(512);
    }

    /**
     * 构造带初始容量的 XmlStringBuilder。
     *
     * @param capacity 初始容量
     */
    public XmlStringBuilder(int capacity) {
        this.sb = new StringBuilder(capacity);
    }

    // --- 基础 append 方法 ---

    /**
     * 添加字符串。
     *
     * @param str 字符串
     * @return this
     */
    public XmlStringBuilder append(String str) {
        if (str != null) {
            sb.append(str);
        }
        return this;
    }

    /**
     * 添加另一个 XmlStringBuilder 的内容。
     *
     * @param other 另一个 XmlStringBuilder
     * @return this
     */
    public XmlStringBuilder append(XmlStringBuilder other) {
        if (other != null) {
            sb.append(other.sb);
        }
        return this;
    }

    /**
     * 添加字符。
     *
     * @param c 字符
     * @return this
     */
    public XmlStringBuilder append(char c) {
        sb.append(c);
        return this;
    }

    /**
     * 添加整数。
     *
     * @param i 整数
     * @return this
     */
    public XmlStringBuilder append(int i) {
        sb.append(i);
        return this;
    }

    /**
     * 添加长整数。
     *
     * @param l 长整数
     * @return this
     */
    public XmlStringBuilder append(long l) {
        sb.append(l);
        return this;
    }

    /**
     * 添加布尔值。
     *
     * @param b 布尔值
     * @return this
     */
    public XmlStringBuilder append(boolean b) {
        sb.append(b);
        return this;
    }

    // --- 元素构建方法 ---

    /**
     * 添加元素开标签前缀（不带右尖括号）。
     *
     * <p>用于后续添加属性：{@code xml.element("iq").attribute("id", "123").rightAngleBracket()}</p>
     *
     * @param name 元素名
     * @return this
     */
    public XmlStringBuilder element(String name) {
        sb.append('<').append(name);
        return this;
    }

    /**
     * 添加带命名空间的元素开标签前缀。
     *
     * @param name      元素名
     * @param namespace 命名空间
     * @return this
     */
    public XmlStringBuilder element(String name, String namespace) {
        sb.append('<').append(name);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        return this;
    }

    /**
     * 添加带前缀的元素开标签前缀（如 stream:stream）。
     *
     * @param prefix    前缀
     * @param name      元素名
     * @param namespace 命名空间
     * @return this
     */
    public XmlStringBuilder element(String prefix, String name, String namespace) {
        sb.append('<').append(prefix).append(':').append(name);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        return this;
    }

    /**
     * 添加右尖括号（完成开标签）。
     *
     * @return this
     */
    public XmlStringBuilder rightAngleBracket() {
        sb.append('>');
        return this;
    }

    /**
     * 添加元素闭标签。
     *
     * @param name 元素名
     * @return this
     */
    public XmlStringBuilder closeElement(String name) {
        sb.append("</").append(name).append('>');
        return this;
    }

    /**
     * 添加完整的开标签（带右尖括号）。
     *
     * @param name 元素名
     * @return this
     */
    public XmlStringBuilder openElement(String name) {
        sb.append('<').append(name).append('>');
        return this;
    }

    /**
     * 添加带命名空间的完整开标签。
     *
     * @param name      元素名
     * @param namespace 命名空间
     * @return this
     */
    public XmlStringBuilder openElement(String name, String namespace) {
        sb.append('<').append(name);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        sb.append('>');
        return this;
    }

    // --- 属性方法 ---

    /**
     * 添加属性。
     *
     * @param name  属性名
     * @param value 属性值
     * @return this
     */
    public XmlStringBuilder attribute(String name, String value) {
        if (value != null) {
            sb.append(' ').append(name).append("=\"");
            escapeXml(value);
            sb.append('"');
        }
        return this;
    }

    /**
     * 添加整数属性。
     *
     * @param name  属性名
     * @param value 属性值
     * @return this
     */
    public XmlStringBuilder attribute(String name, int value) {
        sb.append(' ').append(name).append("=\"").append(value).append('"');
        return this;
    }

    /**
     * 添加长整数属性。
     *
     * @param name  属性名
     * @param value 属性值
     * @return this
     */
    public XmlStringBuilder attribute(String name, long value) {
        sb.append(' ').append(name).append("=\"").append(value).append('"');
        return this;
    }

    /**
     * 添加布尔属性。
     *
     * @param name  属性名
     * @param value 属性值
     * @return this
     */
    public XmlStringBuilder attribute(String name, boolean value) {
        sb.append(' ').append(name).append("=\"").append(value).append('"');
        return this;
    }

    /**
     * 添加枚举属性。
     *
     * @param name  属性名
     * @param value 枚举值
     * @return this
     */
    public XmlStringBuilder attribute(String name, Enum<?> value) {
        if (value != null) {
            attribute(name, value.toString());
        }
        return this;
    }

    // --- 内容方法 ---

    /**
     * 添加转义的文本内容。
     *
     * @param content 内容
     * @return this
     */
    public XmlStringBuilder escapedContent(String content) {
        if (content != null) {
            escapeXml(content);
        }
        return this;
    }

    /**
     * 添加完整的文本元素（自动转义内容）。
     *
     * <p>等价于：{@code xml.openElement(name).escapedContent(content).closeElement(name)}</p>
     *
     * @param name    元素名
     * @param content 文本内容（会被转义）
     * @return this
     */
    public XmlStringBuilder textElement(String name, String content) {
        if (content != null) {
            sb.append('<').append(name).append('>');
            escapeXml(content);
            sb.append("</").append(name).append('>');
        }
        return this;
    }

    /**
     * 添加可选的文本元素（仅当内容不为 null 时）。
     *
     * @param name    元素名
     * @param content 文本内容（可为 null）
     * @return this
     */
    public XmlStringBuilder optTextElement(String name, String content) {
        if (content != null) {
            textElement(name, content);
        }
        return this;
    }

    /**
     * 添加带命名空间的文本元素（自动转义内容）。
     *
     * @param name      元素名
     * @param namespace 命名空间
     * @param content   文本内容（会被转义）
     * @return this
     */
    public XmlStringBuilder textElement(String name, String namespace, String content) {
        if (content != null) {
            sb.append('<').append(name);
            if (namespace != null) {
                sb.append(" xmlns=\"").append(namespace).append('"');
            }
            sb.append('>');
            escapeXml(content);
            sb.append("</").append(name).append('>');
        }
        return this;
    }

    /**
     * 添加可选的带命名空间的文本元素（仅当内容不为 null 时）。
     *
     * @param name      元素名
     * @param namespace 命名空间
     * @param content   文本内容（可为 null）
     * @return this
     */
    public XmlStringBuilder optTextElement(String name, String namespace, String content) {
        if (content != null) {
            textElement(name, namespace, content);
        }
        return this;
    }

    // --- 空元素方法 ---

    /**
     * 添加空元素（自闭合标签）。
     *
     * @param element 元素名
     * @return this
     */
    public XmlStringBuilder emptyElement(String element) {
        sb.append('<').append(element).append("/>");
        return this;
    }

    /**
     * 添加空元素（带命名空间）。
     *
     * @param element   元素名
     * @param namespace 命名空间
     * @return this
     */
    public XmlStringBuilder emptyElement(String element, String namespace) {
        sb.append('<').append(element);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        sb.append("/>");
        return this;
    }

    // --- 工具方法 ---

    /**
     * 获取字符串长度。
     *
     * @return 长度
     */
    public int length() {
        return sb.length();
    }

    /**
     * 检查是否为空。
     *
     * @return 为空返回 true
     */
    public boolean isEmpty() {
        return sb.length() == 0;
    }

    /**
     * 清空内容。
     *
     * @return this
     */
    public XmlStringBuilder clear() {
        sb.setLength(0);
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    // --- 私有方法 ---

    /**
     * 转义 XML 特殊字符。
     */
    private void escapeXml(String content) {
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            switch (c) {
                case '&' -> sb.append(AMP);
                case '<' -> sb.append(LT);
                case '>' -> sb.append(GT);
                case '"' -> sb.append(QUOT);
                case '\'' -> sb.append(APOS);
                default -> sb.append(c);
            }
        }
    }
}
