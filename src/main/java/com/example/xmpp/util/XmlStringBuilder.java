package com.example.xmpp.util;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.function.Consumer;

/**
 * XML 字符串构建器。
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
     * @param capacity 初始容量
     */
    public XmlStringBuilder(int capacity) {
        this.sb = new StringBuilder(capacity);
    }

    /**
     * 添加字符串。
     *
     * @param str 字符串
     * @return 当前实例
     */
    public XmlStringBuilder append(String str) {
        if (str != null) {
            sb.append(str);
        }
        return this;
    }

    /**
     * 包装元素内容。
     *
     * @param name 元素名称
     * @param content 元素内容
     * @return 当前实例
     */
    public XmlStringBuilder wrapElement(String name, String content) {
        return wrapElement(name, null, null, content);
    }

    /**
     * 包装带命名空间的元素内容。
     *
     * @param name 元素名称
     * @param namespace 命名空间
     * @param content 元素内容
     * @return 当前实例
     */
    public XmlStringBuilder wrapElement(String name, String namespace, String content) {
        return wrapElement(name, namespace, null, content);
    }

    /**
     * 包装元素内容。
     *
     * @param name 元素名称
     * @param content 内容构建器
     * @return 当前实例
     */
    public XmlStringBuilder wrapElement(String name, Consumer<XmlStringBuilder> content) {
        return wrapElement(name, null, null, content);
    }

    /**
     * 包装带命名空间的元素内容。
     *
     * @param name 元素名称
     * @param namespace 命名空间
     * @param content 内容构建器
     * @return 当前实例
     */
    public XmlStringBuilder wrapElement(String name, String namespace, Consumer<XmlStringBuilder> content) {
        return wrapElement(name, namespace, null, content);
    }

    /**
     * 包装带属性的元素内容。
     *
     * @param name 元素名称
     * @param attributes 元素属性
     * @param content 内容构建器
     * @return 当前实例
     */
    public XmlStringBuilder wrapElement(String name, Map<String, ?> attributes, Consumer<XmlStringBuilder> content) {
        return wrapElement(name, null, attributes, content);
    }

    /**
     * 包装带属性的元素内容。
     *
     * @param name 元素名称
     * @param namespace 命名空间
     * @param attributes 元素属性
     * @param content 元素内容
     * @return 当前实例
     */
    public XmlStringBuilder wrapElement(String name, String namespace, Map<String, ?> attributes, String content) {
        return wrapElement(name, namespace, attributes, content, true);
    }

    private XmlStringBuilder wrapElement(String name,
                                         String namespace,
                                         Map<String, ?> attributes,
                                         String content,
                                         boolean escapeContent) {
        sb.append('<').append(name);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        appendAttributes(attributes);
        if (StringUtils.isEmpty(content)) {
            sb.append("/>");
            return this;
        }
        sb.append('>');
        if (escapeContent) {
            sb.append(SecurityUtils.escapeXmlAttribute(content));
        } else {
            sb.append(content);
        }
        sb.append("</").append(name).append('>');
        return this;
    }

    /**
     * 包装带属性的元素内容。
     *
     * @param name 元素名称
     * @param namespace 命名空间
     * @param attributes 元素属性
     * @param content 内容构建器
     * @return 当前实例
     */
    public XmlStringBuilder wrapElement(String name,
                                        String namespace,
                                        Map<String, ?> attributes,
                                        Consumer<XmlStringBuilder> content) {
        XmlStringBuilder contentBuilder = new XmlStringBuilder();
        if (content != null) {
            content.accept(contentBuilder);
        }
        return wrapElement(name, namespace, attributes, contentBuilder.toString(), false);
    }

    /**
     * 添加开标签。
     *
     * @param name 元素名称
     * @param namespace 命名空间
     * @param attributes 属性映射
     * @return 当前实例
     */
    public XmlStringBuilder openElement(String name, String namespace, Map<String, ?> attributes) {
        sb.append('<').append(name);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        appendAttributes(attributes);
        sb.append('>');
        return this;
    }

    /**
     * 添加带前缀的开标签。
     *
     * @param prefix 命名空间前缀
     * @param name 元素名称
     * @param namespace 命名空间
     * @param attributes 属性映射
     * @return 当前实例
     */
    public XmlStringBuilder openElement(String prefix,
                                        String name,
                                        String namespace,
                                        Map<String, ?> attributes) {
        sb.append('<').append(prefix).append(':').append(name);
        if (namespace != null) {
            sb.append(" xmlns=\"").append(namespace).append('"');
        }
        appendAttributes(attributes);
        sb.append('>');
        return this;
    }

    /**
     * 转为字符串。
     *
     * @return XML 字符串
     */
    @Override
    public String toString() {
        return sb.toString();
    }

    /**
     * 转义 XML 内容并追加。
     *
     * @param content 内容
     * @return 当前实例
     */
    public XmlStringBuilder escapeXml(String content) {
        if (content != null) {
            sb.append(SecurityUtils.escapeXmlAttribute(content));
        }
        return this;
    }

    private void appendAttributes(Map<String, ?> attributes) {
        if (MapUtils.isEmpty(attributes)) {
            return;
        }
        for (Map.Entry<String, ?> entry : attributes.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Enum<?> enumValue) {
                appendAttribute(entry.getKey(), enumValue.toString());
            } else if (value != null) {
                appendAttribute(entry.getKey(), String.valueOf(value));
            }
        }
    }

    private void appendAttribute(String name, String value) {
        sb.append(' ').append(name).append("=\"");
        escapeXml(value);
        sb.append('"');
    }

}
