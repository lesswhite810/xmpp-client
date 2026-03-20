package com.example.xmpp.protocol.model;

import com.example.xmpp.util.EnumUtils;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IQ 节。
 *
 * @since 2026-02-09
 */
@Getter
public final class Iq extends Stanza {
    public static final String ELEMENT = "iq";

    /**
     * IQ 类型。
     */
    private final Type type;
    /**
     * IQ 子元素。
     */
    private final ExtensionElement childElement;
    /**
     * 错误元素。
     */
    private final XmppError error;

    private Iq(Builder builder) {
        super(builder.getId(), builder.getFrom(), builder.getTo(),
                builder.childElement != null || builder.error != null || builder.getExtensions() != null
                        ? consolidateExtensions(builder.childElement, builder.error, builder.getExtensions())
                        : null);
        this.type = builder.type != null ? builder.type : Type.GET;
        this.childElement = builder.childElement;
        this.error = builder.error;
    }

    /**
     * 合并子元素、错误和扩展。
     *
     * @param childElement 子元素
     * @param error 错误元素
     * @param extensions 扩展列表
     * @return 合并后的扩展元素列表
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
     * 创建错误响应。
     *
     * @param request 请求 IQ
     * @param error 错误信息
     * @return 错误响应 IQ
     * @throws NullPointerException 如果 request 或 error 为 null
     */
    public static Iq createErrorResponse(Iq request, XmppError error) {
        return new Builder(Type.ERROR)
                .id(request.getId())
                .to(request.getFrom())
                .from(request.getTo())
                .childElement(request.getChildElement())
                .error(error)
                .build();
    }

    /**
     * 创建结果响应。
     *
     * @param request 请求 IQ
     * @param childElement 子元素
     * @return 结果响应 IQ
     * @throws NullPointerException 如果 request 为 null
     */
    public static Iq createResultResponse(Iq request, ExtensionElement childElement) {
        return new Builder(Type.RESULT)
                .id(request.getId())
                .to(request.getFrom())
                .from(request.getTo())
                .childElement(childElement)
                .build();
    }

    /**
     * 判断是否为错误类型。
     *
     * @return 是否为错误类型
     */
    public boolean isError() {
        return type == Type.ERROR;
    }

    /**
     * 判断是否为结果类型。
     *
     * @return 是否为结果类型
     */
    public boolean isResult() {
        return type == Type.RESULT;
    }

    /**
     * 判断是否为获取类型。
     *
     * @return 是否为获取类型
     */
    public boolean isGet() {
        return type == Type.GET;
    }

    /**
     * 判断是否为设置类型。
     *
     * @return 是否为设置类型
     */
    public boolean isSet() {
        return type == Type.SET;
    }

    /**
     * 获取元素名称。
     *
     * @return 元素名称
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    @Override
    protected Map<String, Object> buildAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("type", type);
        attributes.putAll(super.buildAttributes());
        return attributes;
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
     */
    public static class Builder extends Stanza.Builder<Builder, Iq> {
        /**
         * IQ 类型。
         */
        private Type type;
        /**
         * IQ 子元素。
         */
        private ExtensionElement childElement;
        /**
         * 错误元素。
         */
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
            this.type = Type.fromStringOrDefault(type, Type.GET);
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
            this.type = Type.fromStringOrDefault(type, Type.GET);
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
                type = Type.GET;
            }
            return new Iq(this);
        }
    }

    /**
     * IQ 类型枚举。
     */
    public enum Type {
        /**
         * 获取类型
         */
        GET,
        /**
         * 设置类型
         */
        SET,
        /**
         * 结果类型
         */
        RESULT,
        /**
         * 错误类型
         */
        ERROR;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

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
            return EnumUtils.fromString(Type.class, type)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Invalid IQ type: '" + type + "'. Valid types are: get, set, result, error"));
        }

        /**
         * 从字符串解析 IQ 类型，无效时返回默认值。
         *
         * @param type 类型字符串（不区分大小写）
         * @param defaultValue 默认值
         * @return 对应的 Type 枚举值，无效则返回默认值
         */
        public static Type fromStringOrDefault(String type, Type defaultValue) {
            return EnumUtils.fromStringOrDefault(Type.class, type, defaultValue);
        }
    }
}
