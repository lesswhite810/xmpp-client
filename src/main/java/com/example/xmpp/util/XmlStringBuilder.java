package com.example.xmpp.util;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.function.Consumer;

/**
 * XML 字符串构建器。
 *
 * <p>提供流式 API 构建 XML 文档，自动处理 XML 特殊字符转义。支持链式调用。</p>
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
     * 包装元素内容。
     *
     * <p>生成形如 <name>content</name> 的完整元素。字符串内容会自动转义。</p>
     *
     * @param name    元素名称
     * @param content 元素内容，null 将按空内容处理
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder wrapElement(String name, String content) {
        return wrapElement(name, null, null, content);
    }

    /**
     * 包装带命名空间的元素内容。
     *
     * <p>生成形如 <name xmlns="namespace">content</name> 的完整元素。字符串内容会自动转义。</p>
     *
     * @param name      元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @param content   元素内容，null 将按空内容处理
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder wrapElement(String name, String namespace, String content) {
        return wrapElement(name, namespace, null, content);
    }

    /**
     * 包装元素内容（使用 Consumer 构建子元素）。
     *
     * <p>生成形如 <name>content</name> 的完整元素。使用 Consumer 可以链式添加多个子元素。</p>
     *
     * @param name    元素名称
     * @param content Consumer 用于构建元素内容
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder wrapElement(String name, Consumer<XmlStringBuilder> content) {
        return wrapElement(name, null, null, content);
    }

    /**
     * 包装带命名空间的元素内容（使用 Consumer 构建子元素）。
     *
     * <p>生成形如 <name xmlns="namespace">content</name> 的完整元素。使用 Consumer 可以链式添加多个子元素。</p>
     *
     * @param name      元素名称
     * @param namespace 命名空间 URI，null 值将不添加 xmlns 属性
     * @param content   Consumer 用于构建元素内容
     * @return 当前 XmlStringBuilder 实例，用于链式调用
     */
    public XmlStringBuilder wrapElement(String name, String namespace, Consumer<XmlStringBuilder> content) {
        return wrapElement(name, namespace, null, content);
    }

    /**
     * 包装带属性的元素内容。
     *
     * @param name       元素名称
     * @param attributes 元素属性
     * @param content    子元素构建器
     * @return 当前 XmlStringBuilder 实例
     */
    public XmlStringBuilder wrapElement(String name, Map<String, ?> attributes, Consumer<XmlStringBuilder> content) {
        return wrapElement(name, null, attributes, content);
    }

    /**
     * 包装带属性的元素内容。
     *
     * @param name       元素名称
     * @param namespace  命名空间 URI
     * @param attributes 元素属性
     * @param content    原始内容
     * @return 当前 XmlStringBuilder 实例
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
     * @param name       元素名称
     * @param namespace  命名空间 URI
     * @param attributes 元素属性
     * @param content    子元素构建器
     * @return 当前 XmlStringBuilder 实例
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
     * 添加完整的开标签（带属性）。
     *
     * @param name       元素名称
     * @param namespace  命名空间 URI
     * @param attributes 属性映射
     * @return 当前 XmlStringBuilder 实例
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
     * 添加带前缀的完整开标签。
     *
     * @param prefix     命名空间前缀
     * @param name       元素名称
     * @param namespace  命名空间 URI
     * @param attributes 属性映射
     * @return 当前 XmlStringBuilder 实例
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
     * @return 当前 XmlStringBuilder 实例
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
