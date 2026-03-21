package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 通用扩展元素。
 *
 * @since 2026-02-27
 */
@Getter
public class GenericExtensionElement implements ExtensionElement {

    private final String elementName;
    private final String namespace;
    private final Map<QName, String> attributes;
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
     * 获取属性值。
     *
     * @param name 属性名
     * @return 属性值
     */
    public String getAttributeValue(String name) {
        return getAttributeValue(XMLConstants.NULL_NS_URI, name);
    }

    /**
     * 获取指定命名空间和属性名的属性值。
     *
     * @param namespace 命名空间
     * @param name 属性名
     * @return 属性值
     */
    public String getAttributeValue(String namespace, String name) {
        return getAttributeValue(new QName(namespace != null ? namespace : XMLConstants.NULL_NS_URI, name));
    }

    /**
     * 获取指定 QName 的属性值。
     *
     * @param attributeName 属性 QName
     * @return 属性值
     */
    public String getAttributeValue(QName attributeName) {
        return attributes.get(attributeName);
    }

    /**
     * 获取指定名称的第一个子元素。
     *
     * @param elementName 子元素名称
     * @return 匹配的子元素
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
     * @return 匹配的子元素
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
     * 获取当前元素的 QName。
     *
     * @return QName
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
        appendStartElement(xml);
        if (contentNodes.isEmpty()) {
            xml.append("/>");
            return xml.toString();
        }

        xml.append(">");
        for (ContentNode contentNode : contentNodes) {
            if (contentNode instanceof TextContent textContent) {
                xml.escapeXml(textContent.text());
            } else if (contentNode instanceof ElementContent elementContent) {
                xml.append(elementContent.element().toXml());
            }
        }
        xml.append("</").append(elementName).append(">");
        return xml.toString();
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

    private void appendStartElement(XmlStringBuilder xml) {
        xml.append("<").append(elementName);
        if (!namespace.isEmpty()) {
            xml.append(" xmlns=\"");
            xml.escapeXml(namespace);
            xml.append("\"");
        }

        ResolvedAttributes resolvedAttributes = resolveAttributes();
        for (Map.Entry<String, String> declaration : resolvedAttributes.namespaceDeclarations().entrySet()) {
            xml.append(" xmlns:").append(declaration.getKey()).append("=\"");
            xml.escapeXml(declaration.getValue());
            xml.append("\"");
        }
        for (ResolvedAttribute attribute : resolvedAttributes.attributes()) {
            xml.append(" ").append(attribute.qualifiedName()).append("=\"");
            xml.escapeXml(attribute.value());
            xml.append("\"");
        }
    }

    private ResolvedAttributes resolveAttributes() {
        List<ResolvedAttribute> resolved = new ArrayList<>();
        Map<String, String> namespaceDeclarations = new LinkedHashMap<>();
        Map<String, String> namespaceToPrefix = new LinkedHashMap<>();
        int generatedPrefixIndex = 1;

        for (Map.Entry<QName, String> entry : attributes.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            QName attributeName = entry.getKey();
            String attributeNamespace = attributeName.getNamespaceURI();
            if (attributeNamespace == null || attributeNamespace.isEmpty()) {
                resolved.add(new ResolvedAttribute(attributeName.getLocalPart(), entry.getValue()));
                continue;
            }

            String prefix;
            if (XMLConstants.XML_NS_URI.equals(attributeNamespace)) {
                prefix = XMLConstants.XML_NS_PREFIX;
            } else {
                prefix = namespaceToPrefix.get(attributeNamespace);
                if (prefix == null) {
                    prefix = normalizeAttributePrefix(attributeName.getPrefix(), namespaceDeclarations, generatedPrefixIndex);
                    while (namespaceDeclarations.containsKey(prefix)
                            && !attributeNamespace.equals(namespaceDeclarations.get(prefix))) {
                        generatedPrefixIndex++;
                        prefix = generatedPrefix(generatedPrefixIndex);
                    }
                    namespaceToPrefix.put(attributeNamespace, prefix);
                    namespaceDeclarations.put(prefix, attributeNamespace);
                    generatedPrefixIndex++;
                }
            }

            resolved.add(new ResolvedAttribute(prefix + ":" + attributeName.getLocalPart(), entry.getValue()));
        }

        return new ResolvedAttributes(namespaceDeclarations, resolved);
    }

    private String normalizeAttributePrefix(String prefix,
                                           Map<String, String> namespaceDeclarations,
                                           int generatedPrefixIndex) {
        if (prefix == null || prefix.isEmpty()
                || XMLConstants.XML_NS_PREFIX.equals(prefix)
                || XMLConstants.XMLNS_ATTRIBUTE.equals(prefix)
                || namespaceDeclarations.containsKey(prefix)) {
            return generatedPrefix(generatedPrefixIndex);
        }
        return prefix;
    }

    private String generatedPrefix(int index) {
        return "ns" + index;
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
     * Builder 类。
     */
    public static class Builder {
        private final String elementName;
        private final String namespace;
        private Map<QName, String> attributes;
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
            return addAttribute(new QName(XMLConstants.NULL_NS_URI, name), value);
        }

        /**
         * 添加属性。
         *
         * @param name 属性 QName
         * @param value 属性值
         * @return Builder 实例
         */
        public Builder addAttribute(QName name, String value) {
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
        public Builder addAttributes(Map<QName, String> attrs) {
            if (MapUtils.isEmpty(attrs)) {
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

    private record ResolvedAttributes(Map<String, String> namespaceDeclarations,
                                      List<ResolvedAttribute> attributes) {
    }

    private record ResolvedAttribute(String qualifiedName, String value) {
    }
}
