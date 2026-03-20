package com.example.xmpp.protocol.model;

import com.example.xmpp.util.EnumUtils;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Presence 节。
 *
 * @since 2026-02-09
 */
@Getter
public final class Presence extends Stanza {
    public static final String ELEMENT = "presence";

    /**
     * Presence 类型。
     */
    private final Type type;
    /**
     * 状态显示。
     */
    private final String show;
    /**
     * 状态描述。
     */
    private final String status;
    /**
     * 优先级。
     */
    private final Integer priority;

    private Presence(Builder builder) {
        super(builder.getId(), builder.getFrom(), builder.getTo(), builder.getExtensions());
        this.type = builder.type != null ? builder.type : Type.AVAILABLE;
        this.show = builder.show;
        this.status = builder.status;
        this.priority = builder.priority;
    }

    /**
     * 获取状态显示枚举值。
     *
     * @return 状态显示
     * @throws NullPointerException 如果 show 为 null
     */
    public Optional<Show> getPresenceShow() {
        return Show.fromString(show);
    }

    /**
     * 判断是否为可用状态。
     *
     * @return 是否为可用状态
     */
    public boolean isAvailable() {
        return type == Type.AVAILABLE;
    }

    /**
     * 判断是否为不可用状态。
     *
     * @return 是否为不可用状态
     */
    public boolean isUnavailable() {
        return type == Type.UNAVAILABLE;
    }

    /**
     * 判断是否为错误状态。
     *
     * @return 是否为错误状态
     */
    public boolean isError() {
        return type == Type.ERROR;
    }

    /**
     * 判断是否为订阅请求。
     *
     * @return 是否为订阅请求
     */
    public boolean isSubscribe() {
        return type == Type.SUBSCRIBE;
    }

    /**
     * 判断是否为订阅确认。
     *
     * @return 是否为订阅确认
     */
    public boolean isSubscribed() {
        return type == Type.SUBSCRIBED;
    }

    /**
     * 判断是否为取消订阅请求。
     *
     * @return 是否为取消订阅请求
     */
    public boolean isUnsubscribe() {
        return type == Type.UNSUBSCRIBE;
    }

    /**
     * 判断是否为取消订阅确认。
     *
     * @return 是否为取消订阅确认
     */
    public boolean isUnsubscribed() {
        return type == Type.UNSUBSCRIBED;
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
        if (type != null && type != Type.AVAILABLE) {
            attributes.put("type", type);
        }
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
        if (show != null) {
            xml.wrapElement("show", show);
        }
        if (status != null) {
            xml.wrapElement("status", status);
        }
        if (priority != null) {
            xml.wrapElement("priority", String.valueOf(priority));
        }
        super.appendExtensions(xml);
    }

    /**
     * Presence Builder。
     */
    public static class Builder extends Stanza.Builder<Builder, Presence> {
        /**
         * Presence 类型。
         */
        private Type type;
        /**
         * 状态显示。
         */
        private String show;
        /**
         * 状态描述。
         */
        private String status;
        /**
         * 优先级。
         */
        private Integer priority;

        /**
         * 构造器，默认类型为 available。
         */
        public Builder() {
            this.type = Type.AVAILABLE;
        }

        /**
         * 构造器。
         *
         * @param type Presence 类型
         */
        public Builder(Type type) {
            this.type = Objects.requireNonNull(type, "Presence type cannot be null");
        }

        /**
         * 构造器。
         *
         * @param type Presence 类型字符串
         */
        public Builder(String type) {
            this.type = parseType(type);
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * 设置 Presence 类型。
         *
         * @param type Presence 类型
         * @return this
         */
        public Builder type(Type type) {
            this.type = Objects.requireNonNull(type, "Presence type cannot be null");
            return self();
        }

        /**
         * 设置 Presence 类型。
         *
         * @param type Presence 类型字符串
         * @return this
         */
        public Builder type(String type) {
            this.type = parseType(type);
            return self();
        }

        private Type parseType(String type) {
            Objects.requireNonNull(type, "Presence type cannot be null");
            return Type.fromString(type)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid presence type: " + type));
        }

        /**
         * 设置状态显示。
         *
         * @param show 状态显示
         * @return this
         */
        public Builder show(String show) {
            this.show = show;
            return self();
        }

        /**
         * 设置状态描述。
         *
         * @param status 状态描述
         * @return this
         */
        public Builder status(String status) {
            this.status = status;
            return self();
        }

        /**
         * 设置优先级。
         *
         * @param priority 优先级
         * @return this
         */
        public Builder priority(Integer priority) {
            this.priority = priority;
            return self();
        }

        /**
         * 构建 Presence 实例。
         *
         * @return Presence 实例
         */
        @Override
        public Presence build() {
            return new Presence(this);
        }
    }

    /**
     * Presence 类型枚举。
     */
    public enum Type {
        /**
         * 可用状态
         */
        AVAILABLE,
        /**
         * 不可用状态
         */
        UNAVAILABLE,
        /**
         * 错误状态
         */
        ERROR,
        /**
         * 订阅请求
         */
        SUBSCRIBE,
        /**
         * 订阅确认
         */
        SUBSCRIBED,
        /**
         * 取消订阅请求
         */
        UNSUBSCRIBE,
        /**
         * 取消订阅确认
         */
        UNSUBSCRIBED;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        /**
         * 从字符串解析 Presence 类型。
         *
         * @param type 类型字符串
         * @return 对应的 Type 枚举值的 Optional，无效则返回 Optional.empty()
         */
        public static Optional<Type> fromString(String type) {
            return EnumUtils.fromString(Type.class, type);
        }
    }

    /**
     * Presence 显示状态枚举。
     */
    public enum Show {
        /**
         * 离开
         */
        AWAY,
        /**
         * 聊天
         */
        CHAT,
        /**
         * 请勿打扰
         */
        DND,
        /**
         * 长时间离开
         */
        XA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        /**
         * 从字符串解析 Show 值。
         *
         * @param show Show 字符串
         * @return 对应的 Show 枚举值的 Optional，无效则返回 Optional.empty()
         */
        public static Optional<Show> fromString(String show) {
            return EnumUtils.fromString(Show.class, show);
        }
    }
}
