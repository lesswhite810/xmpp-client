package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * 获取元素名称。
     *
     * @return 元素名称
     */
    @Override
    public String getElementName() {
        return elementName;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间
     */
    @Override
    public String getNamespace() {
        return namespace;
    }

    /**
     * 获取属性值。
     *
     * @param name 属性名
     * @return 属性值；如果属性不存在则返回 {@code null}
     */
    public String getAttributeValue(String name) {
        return attributes.get(name);
    }

    /**
     * 获取所有属性。
     *
     * @return 属性映射
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * 获取子元素列表。
     *
     * @return 子元素列表
     */
    public List<GenericExtensionElement> getChildren() {
        return children;
    }

    /**
     * 获取指定名称的第一个子元素。
     *
     * @param elementName 子元素名称
     * @return 匹配的子元素；如果不存在则返回 {@link Optional#empty()}
     */
    public Optional<GenericExtensionElement> getFirstChild(String elementName) {
        for (GenericExtensionElement child : children) {
            if (child.getElementName().equals(elementName)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取指定名称和命名空间的第一个子元素。
     *
     * @param elementName 元素名称
     * @param namespace 命名空间
     * @return 匹配的子元素；如果不存在则返回 {@link Optional#empty()}
     */
    public Optional<GenericExtensionElement> getFirstChild(String elementName, String namespace) {
        for (GenericExtensionElement child : children) {
            if (child.getElementName().equals(elementName)
                    && Objects.equals(child.getNamespace(), namespace)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取文本内容。
     *
     * @return 文本内容
     */
    public String getText() {
        return text;
    }

    /**
     * 获取当前元素的 QName。
     *
     * @return QName 对象
     */
    public QName getQName() {
        return new QName(namespace, elementName);
    }

    /**
     * 将当前扩展元素序列化为 XML 字符串。
     *
     * @return XML 字符串
     */
    @Override
    public String toXml() {
        XmlStringBuilder xml = new XmlStringBuilder();

        if (children.isEmpty() && (text == null || text.isEmpty())) {
            return buildEmptyElementXml();
        }

        xml.element(elementName);

        if (!namespace.isEmpty()) {
            xml.attribute("xmlns", namespace);
        }

        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            xml.attribute(attr.getKey(), attr.getValue());
        }

        xml.rightAngleBracket();

        if (text != null && !text.isEmpty()) {
            xml.escapedContent(text);
        }

        for (GenericExtensionElement child : children) {
            xml.append(child.toXml());
        }

        xml.closeElement(elementName);
        return xml.toString();
    }

    /**
     * 构建空元素的 XML（自闭合标签）。
     *
     * @return XML 字符串
     */
    private String buildEmptyElementXml() {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(elementName);

        if (!namespace.isEmpty()) {
            sb.append(" xmlns=\"").append(escapeXml(namespace)).append('"');
        }

        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            sb.append(' ').append(attr.getKey()).append("=\"")
              .append(escapeXml(attr.getValue())).append('"');
        }

        sb.append("/>");
        return sb.toString();
    }

    /**
     * XML 转义。
     *
     * @param value 需要转义的值
     * @return 转义后的字符串
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
     * @param namespace 命名空间
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
     * Builder 类，用于构造 GenericExtensionElement 实例。
     *
     * <p>支持逐步添加属性、子元素和文本内容，最终构建不可变扩展元素对象。</p>
     */
    public static class Builder {
        private final String elementName;
        private final String namespace;
        private Map<String, String> attributes;
        private List<GenericExtensionElement> children;
        private String text;

        /**
         * 构造 Builder 实例。
         *
         * @param elementName 元素名称
         * @param namespace 命名空间
         */
        public Builder(String elementName, String namespace) {
            this.elementName = elementName;
            this.namespace = namespace;
        }

        /**
         * 添加属性。
         *
         * @param name 属性名
         * @param value 属性值
         * @return Builder 实例
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
         *
         * @param attrs 属性映射
         * @return Builder 实例
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
         *
         * @param child 子元素
         * @return Builder 实例
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
         *
         * @param text 文本内容
         * @return Builder 实例
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * 构建 GenericExtensionElement。
         *
         * @return GenericExtensionElement 实例
         */
        public GenericExtensionElement build() {
            return new GenericExtensionElement(this);
        }
    }
}
