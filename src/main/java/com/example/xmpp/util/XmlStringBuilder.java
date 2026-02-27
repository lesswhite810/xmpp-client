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

    /**
     * 构造 XmlStringBuilder。
     */
    public XmlStringBuilder() {
        this(512);
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
     */
    public XmlStringBuilder append(String str) {
        if (str != null) {
            sb.append(str);
        }
        return this;
    }

    /**
     * 添加字符。
     */
    public XmlStringBuilder append(char c) {
        sb.append(c);
        return this;
    }

    // --- 元素构建方法 ---

    /**
     * 添加元素开标签前缀（不带右尖括号）。
     *
     * <p>用于后续添加属性：{@code xml.element("iq").attribute("id", "123").rightAngleBracket()}</p>
     */
    public XmlStringBuilder element(String name) {
        sb.append('<').append(name);
        return this;
    }

    /**
     * 添加带命名空间的元素开标签前缀。
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
     */
    public XmlStringBuilder rightAngleBracket() {
        sb.append('>');
        return this;
    }

    /**
     * 添加元素闭标签。
     */
    public XmlStringBuilder closeElement(String name) {
        sb.append("</").append(name).append('>');
        return this;
    }

    /**
     * 添加完整的开标签（带右尖括号）。
     */
    public XmlStringBuilder openElement(String name) {
        sb.append('<').append(name).append('>');
        return this;
    }

    /**
     * 添加带命名空间的完整开标签。
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
     * 添加枚举属性。
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
     */
    public XmlStringBuilder escapedContent(String content) {
        if (content != null) {
            escapeXml(content);
        }
        return this;
    }

    /**
     * 添加完整的文本元素（自动转义内容）。
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
     */
    public XmlStringBuilder optTextElement(String name, String content) {
        if (content != null) {
            textElement(name, content);
        }
        return this;
    }

    /**
     * 添加带命名空间的文本元素（自动转义内容）。
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
     */
    public XmlStringBuilder emptyElement(String element) {
        sb.append('<').append(element).append("/>");
        return this;
    }

    /**
     * 添加空元素（带命名空间）。
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
     */
    public int length() {
        return sb.length();
    }

    /**
     * 检查是否为空。
     */
    public boolean isEmpty() {
        return sb.length() == 0;
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
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
    }
}
