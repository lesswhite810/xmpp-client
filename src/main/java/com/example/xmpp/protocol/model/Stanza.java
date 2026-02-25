package com.example.xmpp.protocol.model;

import com.example.xmpp.XmppConstants;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * XMPP 节（Stanza）抽象基类。
 *
 * @since 2026-02-09
 */
@Getter
public abstract sealed class Stanza implements XmppStanza, XmlSerializable permits Iq, Message, Presence {

    /** 唯一标识符 */
    private final String id;
    /** 发送方 JID */
    private final String from;
    /** 接收方 JID */
    private final String to;
    /** 扩展元素列表 */
    private final List<ExtensionElement> extensions;

    /**
     * 构造器。
     *
     * @param id         唯一标识符
     * @param from       发送方 JID
     * @param to         接收方 JID
     * @param extensions 扩展元素列表
     */
    protected Stanza(String id, String from, String to, List<ExtensionElement> extensions) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.extensions = extensions != null ? List.copyOf(extensions) : List.of();
    }

    /**
     * 获取指定类型的扩展元素。
     *
     * @param <E> 扩展元素类型
     * @param extensionClass 扩展元素类
     * @return 扩展元素的 Optional，不存在则返回 Optional.empty()
     */
    public <E extends ExtensionElement> Optional<E> getExtension(Class<E> extensionClass) {
        for (ExtensionElement extension : extensions) {
            if (extensionClass.isInstance(extension)) {
                return Optional.of(extensionClass.cast(extension));
            }
        }
        return Optional.empty();
    }

    /**
     * 获取指定命名空间的扩展元素。
     *
     * @param namespace 扩展元素命名空间
     * @return 扩展元素的 Optional，不存在则返回 Optional.empty()
     * @throws NullPointerException if namespace is null
     */
    public Optional<ExtensionElement> getExtension(String namespace) {
        Objects.requireNonNull(namespace, "namespace must not be null");
        for (ExtensionElement extension : extensions) {
            if (namespace.equals(extension.getNamespace())) {
                return Optional.of(extension);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取指定名称和命名空间的扩展元素。
     *
     * @param elementName 扩展元素名称
     * @param namespace 扩展元素命名空间
     * @return 扩展元素的 Optional，不存在则返回 Optional.empty()
     * @throws NullPointerException if elementName or namespace is null
     */
    public Optional<ExtensionElement> getExtension(String elementName, String namespace) {
        Objects.requireNonNull(elementName, "elementName must not be null");
        Objects.requireNonNull(namespace, "namespace must not be null");
        for (ExtensionElement extension : extensions) {
            if (elementName.equals(extension.getElementName()) && namespace.equals(extension.getNamespace())) {
                return Optional.of(extension);
            }
        }
        return Optional.empty();
    }

    /**
     * 检查是否包含指定类型的扩展元素。
     *
     * @param extensionClass 扩展元素类
     * @return 包含则返回 true
     */
    public boolean hasExtension(Class<? extends ExtensionElement> extensionClass) {
        return getExtension(extensionClass).isPresent();
    }

    /**
     * 检查是否包含指定命名空间的扩展元素。
     *
     * @param namespace 扩展元素命名空间
     * @return 包含则返回 true
     */
    public boolean hasExtension(String namespace) {
        return getExtension(namespace).isPresent();
    }

    /**
     * 检查是否包含指定名称和命名空间的扩展元素。
     *
     * @param elementName 扩展元素名称
     * @param namespace 扩展元素命名空间
     * @return 包含则返回 true
     */
    public boolean hasExtension(String elementName, String namespace) {
        return getExtension(elementName, namespace).isPresent();
    }

    /**
     * 获取节名称（iq、message、presence）。
     *
     * @return 节元素名称
     */
    public abstract String getElementName();

    /**
     * 序列化为 XML 字符串。
     *
     * @return XML 字符串
     */
    @Override
    public String toXml() {
        XmlStringBuilder xml = new XmlStringBuilder(XmppConstants.DEFAULT_XML_BUILDER_CAPACITY);
        xml.element(getElementName());
        appendAttributes(xml);
        xml.rightAngleBracket();
        appendExtensions(xml);
        xml.closeElement(getElementName());
        return xml.toString();
    }

    /**
     * 追加属性。
     *
     * @param xml XML 构建器
     */
    protected void appendAttributes(XmlStringBuilder xml) {
        xml.attribute("id", id)
           .attribute("from", from)
           .attribute("to", to);
    }

    /**
     * 追加扩展元素。
     *
     * @param xml XML 构建器
     */
    protected void appendExtensions(XmlStringBuilder xml) {
        for (ExtensionElement extension : extensions) {
            if (extension != null) {
                xml.append(extension.toXml());
            }
        }
    }

    /**
     * 基础 Builder 类。
     *
     * @param <B> Builder 类型
     * @param <S> Stanza 类型
     */
    protected abstract static class Builder<B extends Builder<B, S>, S extends Stanza> {
        /** 唯一标识符 */
        private String id;
        /** 发送方 JID */
        private String from;
        /** 接收方 JID */
        private String to;
        /** 扩展元素列表 */
        private List<ExtensionElement> extensions;

        protected Builder() {
        }

        protected abstract B self();

        public B id(String id) {
            this.id = id;
            return self();
        }

        public B from(String from) {
            this.from = from;
            return self();
        }

        public B to(String to) {
            this.to = to;
            return self();
        }

        public B addExtension(ExtensionElement extension) {
            if (extension != null) {
                if (extensions == null) {
                    extensions = new ArrayList<>();
                }
                extensions.add(extension);
            }
            return self();
        }

        public B addExtensions(List<ExtensionElement> extensions) {
            if (extensions != null) {
                if (this.extensions == null) {
                    this.extensions = new ArrayList<>();
                }
                this.extensions.addAll(extensions);
            }
            return self();
        }

        protected String getId() {
            return id;
        }

        protected String getFrom() {
            return from;
        }

        protected String getTo() {
            return to;
        }

        protected List<ExtensionElement> getExtensions() {
            return extensions;
        }

        public abstract S build();
    }
}
