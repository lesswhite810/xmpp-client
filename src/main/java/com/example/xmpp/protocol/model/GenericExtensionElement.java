package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通用扩展元素，用于保存未知 XML 元素的数据。
 *
 * <p>当没有注册对应 Provider 时，使用此类保存元素数据，避免数据丢失。</p>
 *
 * @since 2026-02-27
 */
public class GenericExtensionElement implements ExtensionElement {

    private final String elementName;
    private final String namespace;
    private final Map<String, String> attributes;
    private final List<GenericExtensionElement> children;
    private final String text;

    private GenericExtensionElement(Builder builder) {
        this.elementName = Objects.requireNonNull(builder.elementName, "elementName must not be null");
        this.namespace = builder.namespace != null ? builder.namespace : "";
        this.attributes = builder.attributes != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes))
                : Collections.emptyMap();
        this.children = builder.children != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.children))
                : Collections.emptyList();
        this.text = builder.text;
    }

    @Override
    public String getElementName() {
        return elementName;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    /**
     * 获取属性值。
     *
     * @param name 属性名
     * @return 属性值，不存在返回 null
     */
    public String getAttributeValue(String name) {
        return attributes.get(name);
    }

    /**
     * 获取所有属性。
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * 获取子元素列表。
     */
    public List<GenericExtensionElement> getChildren() {
        return children;
    }

    /**
     * 获取指定名称的第一个子元素。
     *
     * @param elementName 子元素名称
     * @return 子元素，不存在返回 null
     */
    public GenericExtensionElement getFirstChild(String elementName) {
        for (GenericExtensionElement child : children) {
            if (child.getElementName().equals(elementName)) {
                return child;
            }
        }
        return null;
    }

    /**
     * 获取指定名称和命名空间的第一个子元素。
     */
    public GenericExtensionElement getFirstChild(String elementName, String namespace) {
        for (GenericExtensionElement child : children) {
            if (child.getElementName().equals(elementName)
                    && Objects.equals(child.getNamespace(), namespace)) {
                return child;
            }
        }
        return null;
    }

    /**
     * 获取文本内容。
     */
    public String getText() {
        return text;
    }

    /**
     * 获取 QName。
     */
    public QName getQName() {
        return new QName(namespace, elementName);
    }

    @Override
    public String toXml() {
        XmlStringBuilder xml = new XmlStringBuilder();

        // 如果没有子元素和文本，使用自闭合标签
        if (children.isEmpty() && (text == null || text.isEmpty())) {
            return buildEmptyElementXml();
        }

        // 有内容时使用完整标签
        xml.element(elementName);

        // 添加命名空间
        if (!namespace.isEmpty()) {
            xml.attribute("xmlns", namespace);
        }

        // 添加属性
        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            xml.attribute(attr.getKey(), attr.getValue());
        }

        xml.rightAngleBracket();

        // 添加文本内容
        if (text != null && !text.isEmpty()) {
            xml.escapedContent(text);
        }

        // 添加子元素
        for (GenericExtensionElement child : children) {
            xml.append(child.toXml());
        }

        xml.closeElement(elementName);
        return xml.toString();
    }

    /**
     * 构建空元素的 XML（自闭合标签）。
     */
    private String buildEmptyElementXml() {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(elementName);

        // 添加命名空间
        if (!namespace.isEmpty()) {
            sb.append(" xmlns=\"").append(escapeXml(namespace)).append('"');
        }

        // 添加属性
        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            sb.append(' ').append(attr.getKey()).append("=\"")
              .append(escapeXml(attr.getValue())).append('"');
        }

        sb.append("/>");
        return sb.toString();
    }

    /**
     * XML 转义。
     */
    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * 创建 Builder。
     *
     * @param elementName 元素名称
     * @param namespace    命名空间
     * @return Builder 实例
     */
    public static Builder builder(String elementName, String namespace) {
        return new Builder(elementName, namespace);
    }

    @Override
    public String toString() {
        return "GenericExtensionElement{" +
                "elementName='" + elementName + '\'' +
                ", namespace='" + namespace + '\'' +
                ", attributes=" + attributes +
                ", children=" + children.size() +
                ", text='" + text + '\'' +
                '}';
    }

    /**
     * Builder 类。
     */
    public static class Builder {
        private final String elementName;
        private final String namespace;
        private Map<String, String> attributes;
        private List<GenericExtensionElement> children;
        private String text;

        public Builder(String elementName, String namespace) {
            this.elementName = elementName;
            this.namespace = namespace;
        }

        /**
         * 添加属性。
         */
        public Builder addAttribute(String name, String value) {
            if (attributes == null) {
                attributes = new LinkedHashMap<>();
            }
            attributes.put(name, value);
            return this;
        }

        /**
         * 添加所有属性。
         */
        public Builder addAttributes(Map<String, String> attrs) {
            if (attrs == null || attrs.isEmpty()) {
                return this;
            }
            if (attributes == null) {
                attributes = new LinkedHashMap<>();
            }
            attributes.putAll(attrs);
            return this;
        }

        /**
         * 添加子元素。
         */
        public Builder addChild(GenericExtensionElement child) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(child);
            return this;
        }

        /**
         * 设置文本内容。
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * 构建 GenericExtensionElement。
         */
        public GenericExtensionElement build() {
            return new GenericExtensionElement(this);
        }
    }
}
