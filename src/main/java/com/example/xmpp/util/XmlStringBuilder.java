package com.example.xmpp.util;

/**
 * XML 字符串构建器。
 *
 * <p>提供流式 API 构建 XML 文档，自动处理 XML 特殊字符转义。
 * 支持链式调用，示例：</p>
 * <pre>{@code
 * new XmlStringBuilder()
 *     .element("iq")
 *     .attribute("type", "set")
 *     .attribute("id", "123")
 *     .rightAngleBracket()
 *     .openElement("bind")
 *     .attribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind")
 *     .closeElement("bind")
 * }</pre>
 *
 * @since 2026-02-09
 */
public class XmlStringBuilder {

    private final StringBuilder sb;

    /**
     * 构造 XmlStringBuilder。
     */
    public XmlStringBuilder() {
        this(XmppConstants.DEFAULT_XML_BUILDER_CAPACITY);
    }

    /**
     * 构造带初始容量的 XmlStringBuilder。
     *
     * @param capacity 初始容量，用于优化大型 XML 文档的内存分配
     */
    public XmlStringBuilder(int capacity) {
        this.sb = new StringBuilder(capacity);
    }

    /**
     * 添加字符串。
     *
     * @param str 要添加的字符串，null 值将被忽略
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder append(String str) {
        if (str != null) {
            sb.append(str);
        }
        return this;
    }

    /**
     * 添加字符。
     *
     * @param c 要添加的字符
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder append(char c) {
        sb.append(c);
        return this;
    }

    /**
     * 添加元素开标签前缀（不带右尖括号）。
     *
     * <p>用于后续添加属性：{@code xml.element("iq").attribute("id", "123").rightAngleBracket()}</p>
     *
     * @param name 元素名称
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder element(String name) {
        sb.append('<').append(name);
        return this;
    }

    /**
     * 添加带命名空间的元素开标签前缀。
     *
     * @param name      元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @return 当前 XmlStringBuilder 实例，用于链式调用
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
     * @param prefix    命名空间前缀
     * @param name      元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @return 当前 XmlStringBuilder 实例，用于链式调用
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
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder rightAngleBracket() {
        sb.append('>');
        return this;
    }

    /**
     * 添加元素闭标签。
     *
     * @param name 元素名称，应与对应的开标签名称匹配
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder closeElement(String name) {
        sb.append("</").append(name).append('>');
        return this;
    }

    /**
     * 添加完整的开标签（带右尖括号）。
     *
     * @param name 元素名称
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder openElement(String name) {
        sb.append('<').append(name).append('>');
        return this;
    }

    /**
     * 添加带命名空间的完整开标签。
     *
     * @param name      元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder openElement(String name, String namespace) {
        sb.append('<').append(name);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        sb.append('>');
        return this;
    }

    /**
     * 添加属性。
     *
     * <p>属性值将自动进行 XML 转义处理。</p>
     *
     * @param name  属性名称
     * @param value 属性值，null 值将被忽略（不添加属性）
     * @return 当前 XmlStringBuilder 实例，用于链式调用
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
     *
     * <p>枚举的 toString() 值将作为属性值。</p>
     *
     * @param name  属性名称
     * @param value 枚举值，null 值将被忽略（不添加属性）
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder attribute(String name, Enum<?> value) {
        if (value != null) {
            attribute(name, value.toString());
        }
        return this;
    }

    /**
     * 添加转义的文本内容。
     *
     * <p>该方法仅转义内容，不添加标签。内容中的 XML 特殊字符将被转义。</p>
     *
     * @param content 文本内容，null 值将被忽略
     * @return 当前 XmlStringBuilder 实例，用于链式调用
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
     * <p>生成形如 {@code <name>content</name>} 的完整元素。</p>
     *
     * @param name    元素名称
     * @param content 文本内容，null 值将不生成任何内容
     * @return 当前 XmlStringBuilder 实例，用于链式调用
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
     * <p>如果 content 为 null，则不生成任何内容。</p>
     *
     * @param name    元素名称
     * @param content 文本内容，null 值将不生成任何内容
     * @return 当前 XmlStringBuilder 实例，用于链式调用
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
     * <p>生成形如 {@code <name xmlns="namespace">content</name>} 的完整元素。</p>
     *
     * @param name      元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @param content   文本内容，null 值将不生成任何内容
     * @return 当前 XmlStringBuilder 实例，用于链式调用
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
     * <p>如果 content 为 null，则不生成任何内容。</p>
     *
     * @param name      元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @param content   文本内容，null 值将不生成任何内容
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder optTextElement(String name, String namespace, String content) {
        if (content != null) {
            textElement(name, namespace, content);
        }
        return this;
    }

    /**
     * 包装元素内容。
     *
     * <p>生成形如 {@code <name>content</name>} 的完整元素。内容将按原样追加，不做转义。</p>
     *
     * @param name 元素名称
     * @param content 元素内容，null 将按空内容处理
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder wrapElement(String name, String content) {
        return openElement(name)
                .append(content)
                .closeElement(name);
    }

    /**
     * 包装带命名空间的元素内容。
     *
     * <p>生成形如 {@code <name xmlns="namespace">content</name>} 的完整元素。内容将按原样追加，不做转义。</p>
     *
     * @param name 元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @param content 元素内容，null 将按空内容处理
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder wrapElement(String name, String namespace, String content) {
        return openElement(name, namespace)
                .append(content)
                .closeElement(name);
    }

    /**
     * 添加空元素（自闭合标签）。
     *
     * <p>生成形如 {@code <element/>} 的自闭合标签。</p>
     *
     * @param element 元素名称
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder emptyElement(String element) {
        sb.append('<').append(element).append("/>");
        return this;
    }

    /**
     * 添加空元素（带命名空间）。
     *
     * <p>生成形如 {@code <element xmlns="namespace"/>} 的自闭合标签。</p>
     *
     * @param element   元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder emptyElement(String element, String namespace) {
        sb.append('<').append(element);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        sb.append("/>");
        return this;
    }

    /**
     * 获取字符串长度。
     *
     * @return 当前构建的 XML 字符串长度
     */
    public int length() {
        return sb.length();
    }

    /**
     * 检查是否为空。
     *
     * @return 如果当前未构建任何内容返回 true，否则返回 false
     */
    public boolean isEmpty() {
        return sb.length() == 0;
    }

    /**
     * 将构建的 XML 内容转换为字符串。
     *
     * @return 生成的 XML 字符串
     */
    @Override
    public String toString() {
        return sb.toString();
    }

    /**
     * 转义 XML 特殊字符并追加到构建器。
     *
     * <p>委托给 {@link SecurityUtils#escapeXmlAttribute(String)} 进行转义处理。</p>
     *
     * @param content 要转义并追加的内容
     */
    private void escapeXml(String content) {
        sb.append(SecurityUtils.escapeXmlAttribute(content));
    }
}
