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
    private final List<ContentNode> contentNodes;
    private final List<GenericExtensionElement> children;
    private final String text;

    private GenericExtensionElement(Builder builder) {
        this.elementName = Objects.requireNonNull(builder.elementName, "elementName must not be null");
        this.namespace = builder.namespace != null ? builder.namespace : "";
        this.attributes = builder.attributes != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes))
                : Collections.emptyMap();
        List<ContentNode> nodes = builder.contentNodes != null
                ? List.copyOf(builder.contentNodes)
                : List.of();
        this.contentNodes = nodes;
        this.children = Collections.unmodifiableList(extractChildren(nodes));
        this.text = mergeText(nodes);
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
     * @return 属性值；如果属性不存在则返回 null
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
     * 获取保序内容节点。
     *
     * @return 内容节点列表
     */
    public List<ContentNode> getContentNodes() {
        return contentNodes;
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
        if (contentNodes.isEmpty()) {
            return buildEmptyElementXml();
        }

        return new XmlStringBuilder()
                .wrapElement(elementName, namespace.isEmpty() ? null : namespace, attributes, xml -> {
                    for (ContentNode contentNode : contentNodes) {
                        if (contentNode instanceof TextContent textContent) {
                            xml.escapeXml(textContent.text());
                        } else if (contentNode instanceof ElementContent elementContent) {
                            xml.append(elementContent.element().toXml());
                        }
                    }
                })
                .toString();
    }

    /**
     * 构建空元素的 XML（自闭合标签）。
     *
     * @return XML 字符串
     */
    private String buildEmptyElementXml() {
        return new XmlStringBuilder()
                .wrapElement(elementName, namespace.isEmpty() ? null : namespace, attributes, "")
                .toString();
    }

    private static List<GenericExtensionElement> extractChildren(List<ContentNode> nodes) {
        List<GenericExtensionElement> result = new ArrayList<>();
        for (ContentNode node : nodes) {
            if (node instanceof ElementContent elementContent) {
                result.add(elementContent.element());
            }
        }
        return result;
    }

    private static String mergeText(List<ContentNode> nodes) {
        StringBuilder builder = new StringBuilder();
        for (ContentNode node : nodes) {
            if (node instanceof TextContent textContent) {
                builder.append(textContent.text());
            }
        }
        return builder.isEmpty() ? null : builder.toString();
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
                ", contentNodes=" + contentNodes.size() +
                ", text='" + text + '\'' +
                '}';
    }

    /**
     * 通用 XML 内容节点。
     */
    public sealed interface ContentNode permits TextContent, ElementContent {
    }

    /**
     * 文本内容节点。
     *
     * @param text 文本内容
     */
    public record TextContent(String text) implements ContentNode {
        public TextContent {
            Objects.requireNonNull(text, "text must not be null");
        }
    }

    /**
     * 子元素内容节点。
     *
     * @param element 子元素
     */
    public record ElementContent(GenericExtensionElement element) implements ContentNode {
        public ElementContent {
            Objects.requireNonNull(element, "element must not be null");
        }
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
        private List<ContentNode> contentNodes;

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
            if (contentNodes == null) {
                contentNodes = new ArrayList<>();
            }
            contentNodes.add(new ElementContent(child));
            return this;
        }

        /**
         * 设置文本内容。
         *
         * @param text 文本内容
         * @return Builder 实例
         */
        public Builder text(String text) {
            if (text == null) {
                return this;
            }
            if (contentNodes == null) {
                contentNodes = new ArrayList<>();
            }
            contentNodes.add(new TextContent(text));
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
