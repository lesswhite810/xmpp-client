package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * IQ (Info/Query) 节。
 *
 * @since 2026-02-09
 */
@Getter
public final class Iq extends Stanza {

    /** IQ 类型 */
    private final Type type;
    /** IQ 子元素 */
    private final ExtensionElement childElement;
    /** 错误元素 */
    private final XmppError error;

    private Iq(Builder builder) {
        super(builder.getId(), builder.getFrom(), builder.getTo(),
                builder.childElement != null || builder.error != null || builder.getExtensions() != null
                        ? consolidateExtensions(builder.childElement, builder.error, builder.getExtensions())
                        : null);
        this.type = builder.type != null ? builder.type : Type.get;
        this.childElement = builder.childElement;
        this.error = builder.error;
    }

    /**
     * 合并子元素、错误和扩展为单一列表。
     */
    private static List<ExtensionElement> consolidateExtensions(
            ExtensionElement childElement, XmppError error, List<ExtensionElement> extensions) {
        List<ExtensionElement> result = new ArrayList<>();
        if (childElement != null) {
            result.add(childElement);
        }
        if (error != null) {
            result.add(error);
        }
        if (extensions != null) {
            result.addAll(extensions);
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    /**
     * 空构造器，用于解析时创建实例。
     */
    public Iq() {
        this(Type.get, null, null, null, null, null);
    }

    /**
     * 完整构造器。
     *
     * @param type IQ 类型
     * @param id 唯一标识符
     * @param from 发送方 JID
     * @param to 接收方 JID
     * @param childElement IQ 子元素
     * @param error 错误元素
     */
    public Iq(Type type, String id, String from, String to, ExtensionElement childElement, XmppError error) {
        super(id, from, to, childElement != null || error != null
                ? consolidateExtensions(childElement, error, null)
                : null);
        this.type = type != null ? type : Type.get;
        this.childElement = childElement;
        this.error = error;
    }

    /**
     * 创建错误响应。
     *
     * @param request 请求 IQ
     * @param error 错误信息
     * @return 错误响应 IQ
     */
    public static Iq createErrorResponse(Iq request, XmppError error) {
        return new Builder(Type.error)
                .id(request.getId())
                .to(request.getFrom())
                .from(request.getTo())
                .error(error)
                .build();
    }

    /**
     * 创建结果响应。
     *
     * @param request 请求 IQ
     * @param childElement 子元素
     * @return 结果响应 IQ
     */
    public static Iq createResultResponse(Iq request, ExtensionElement childElement) {
        return new Builder(Type.result)
                .id(request.getId())
                .to(request.getFrom())
                .from(request.getTo())
                .childElement(childElement)
                .build();
    }

    /**
     * 获取子元素名称。
     *
     * @return 子元素名称，不存在则返回 null
     */
    public String getChildElementName() {
        return childElement != null ? childElement.getElementName() : null;
    }

    /**
     * 获取子元素命名空间。
     *
     * @return 子元素命名空间，不存在则返回 null
     */
    public String getChildElementNamespace() {
        return childElement != null ? childElement.getNamespace() : null;
    }

    /**
     * 判断是否为错误类型。
     *
     * @return 是错误类型返回 true
     */
    public boolean isError() {
        return type == Type.error;
    }

    /**
     * 判断是否为结果类型。
     *
     * @return 是结果类型返回 true
     */
    public boolean isResult() {
        return type == Type.result;
    }

    /**
     * 判断是否为获取类型。
     *
     * @return 是获取类型返回 true
     */
    public boolean isGet() {
        return type == Type.get;
    }

    /**
     * 判断是否为设置类型。
     *
     * @return 是设置类型返回 true
     */
    public boolean isSet() {
        return type == Type.set;
    }

    /**
     * 获取元素名称。
     *
     * @return "iq"
     */
    @Override
    public String getElementName() {
        return "iq";
    }

    /**
     * 追加属性到 XML 构建器。
     *
     * @param xml XML 构建器
     */
    @Override
    protected void appendAttributes(XmlStringBuilder xml) {
        xml.attribute("type", type);
        super.appendAttributes(xml);
    }

    /**
     * 追加扩展元素到 XML 构建器。
     *
     * @param xml XML 构建器
     */
    @Override
    protected void appendExtensions(XmlStringBuilder xml) {
        if (childElement != null) {
            xml.append(childElement.toXml());
        }
        if (error != null) {
            xml.append(error.toXml());
        }
    }

    /**
     * IQ Builder。
     *
     * @since 2026-02-09
     */
    public static class Builder extends Stanza.Builder<Builder, Iq> {
        /** IQ 类型 */
        private Type type;
        /** IQ 子元素 */
        private ExtensionElement childElement;
        /** 错误元素 */
        private XmppError error;

        /**
         * 构造器。
         *
         * @param type IQ 类型
         */
        public Builder(Type type) {
            this.type = type;
        }

        /**
         * 构造器。
         *
         * @param type IQ 类型字符串
         */
        public Builder(String type) {
            this.type = Type.fromStringOrDefault(type, Type.get);
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * 设置 IQ 类型。
         *
         * @param type IQ 类型
         * @return this
         */
        public Builder type(Type type) {
            this.type = type;
            return self();
        }

        /**
         * 设置 IQ 类型。
         *
         * @param type IQ 类型字符串
         * @return this
         */
        public Builder type(String type) {
            this.type = Type.fromStringOrDefault(type, Type.get);
            return self();
        }

        /**
         * 设置子元素。
         *
         * @param childElement 子元素
         * @return this
         */
        public Builder childElement(ExtensionElement childElement) {
            this.childElement = childElement;
            return self();
        }

        /**
         * 设置错误元素。
         *
         * @param error 错误元素
         * @return this
         */
        public Builder error(XmppError error) {
            this.error = error;
            return self();
        }

        /**
         * 构建 Iq 实例。
         *
         * @return Iq 实例
         */
        @Override
        public Iq build() {
            if (type == null) {
                type = Type.get;
            }
            return new Iq(this);
        }
    }

    /**
     * IQ 类型枚举。
     *
     * @since 2026-02-09
     */
    public enum Type {
        /** 获取类型 */
        get,
        /** 设置类型 */
        set,
        /** 结果类型 */
        result,
        /** 错误类型 */
        error;

        /**
         * 从字符串解析 IQ 类型。
         *
         * @param type 类型字符串（不区分大小写）
         * @return 对应的 Type 枚举值
         * @throws IllegalArgumentException 如果类型字符串为 null 或无效
         */
        public static Type fromString(String type) {
            if (type == null) {
                throw new IllegalArgumentException("IQ type cannot be null");
            }
            try {
                return valueOf(type.toLowerCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid IQ type: '" + type
                        + "'. Valid types are: get, set, result, error");
            }
        }

        /**
         * 从字符串解析 IQ 类型，无效时返回默认值。
         *
         * @param type 类型字符串（不区分大小写）
         * @param defaultValue 默认值
         * @return 对应的 Type 枚举值，无效则返回默认值
         */
        public static Type fromStringOrDefault(String type, Type defaultValue) {
            if (type == null || type.isEmpty()) {
                return defaultValue;
            }
            try {
                return valueOf(type.toLowerCase());
            } catch (IllegalArgumentException e) {
                return defaultValue;
            }
        }
    }
}
