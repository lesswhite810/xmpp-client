package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Presence 节。
 *
 * Presence 节用于在 XMPP 实体之间交换状态信息，是 XMPP 中三种基本节类型之一。
 * 支持的类型：available（可用状态）、unavailable（不可用状态）、error（错误状态）、
 * subscribe（订阅请求）、subscribed（订阅确认）、unsubscribe（取消订阅请求）、unsubscribed（取消订阅确认）。
 *
 * @since 2026-02-09
 */
@Getter
public final class Presence extends Stanza {

    /** Presence 类型 */
    private final Type type;
    /** 状态显示 */
    private final String show;
    /** 状态描述 */
    private final String status;
    /** 优先级 */
    private final Integer priority;

    private Presence(Builder builder) {
        super(builder.getId(), builder.getFrom(), builder.getTo(), builder.getExtensions());
        this.type = builder.type != null ? builder.type : Type.AVAILABLE;
        this.show = builder.show;
        this.status = builder.status;
        this.priority = builder.priority;
    }

    /**
     * 完整构造器。
     *
     * @param type Presence 类型
     * @param id 唯一标识符
     * @param from 发送方 JID
     * @param to 接收方 JID
     * @param show 状态显示
     * @param status 状态描述
     * @param priority 优先级
     * @param extensions 扩展元素
     */
    public Presence(Type type, String id, String from, String to, String show, String status, Integer priority,
            List<ExtensionElement> extensions) {
        super(id, from, to, extensions);
        this.type = type != null ? type : Type.AVAILABLE;
        this.show = show;
        this.status = status;
        this.priority = priority;
    }

    /**
     * 空构造器，用于解析时创建实例。
     */
    public Presence() {
        this(Type.AVAILABLE, null, null, null, null, null, null, null);
    }

    /**
     * 获取状态显示枚举值。
     *
     * @return Show 枚举值的 Optional
     */
    public Optional<Show> getPresenceShow() {
        return Show.fromString(show);
    }

    /**
     * 判断是否为可用状态。
     *
     * @return 是可用状态返回 true
     */
    public boolean isAvailable() {
        return type == Type.AVAILABLE;
    }

    /**
     * 判断是否为不可用状态。
     *
     * @return 是不可用状态返回 true
     */
    public boolean isUnavailable() {
        return type == Type.UNAVAILABLE;
    }

    /**
     * 判断是否为错误状态。
     *
     * @return 是错误状态返回 true
     */
    public boolean isError() {
        return type == Type.ERROR;
    }

    /**
     * 判断是否为订阅请求。
     *
     * @return 是订阅请求返回 true
     */
    public boolean isSubscribe() {
        return type == Type.SUBSCRIBE;
    }

    /**
     * 判断是否为订阅确认。
     *
     * @return 是订阅确认返回 true
     */
    public boolean isSubscribed() {
        return type == Type.SUBSCRIBED;
    }

    /**
     * 判断是否为取消订阅请求。
     *
     * @return 是取消订阅请求返回 true
     */
    public boolean isUnsubscribe() {
        return type == Type.UNSUBSCRIBE;
    }

    /**
     * 判断是否为取消订阅确认。
     *
     * @return 是取消订阅确认返回 true
     */
    public boolean isUnsubscribed() {
        return type == Type.UNSUBSCRIBED;
    }

    /**
     * 获取元素名称。
     *
     * @return "presence"
     */
    @Override
    public String getElementName() {
        return "presence";
    }

    /**
     * 追加属性到 XML 构建器。
     *
     * @param xml XML 构建器
     */
    @Override
    protected void appendAttributes(XmlStringBuilder xml) {
        if (type != null && type != Type.AVAILABLE) {
            xml.attribute("type", type);
        }
        super.appendAttributes(xml);
    }

    /**
     * 追加扩展元素到 XML 构建器。
     *
     * @param xml XML 构建器
     */
    @Override
    protected void appendExtensions(XmlStringBuilder xml) {
        xml.optTextElement("show", show)
           .optTextElement("status", status);
        if (priority != null) {
            xml.textElement("priority", String.valueOf(priority));
        }
        super.appendExtensions(xml);
    }

    /**
     * Presence Builder。
     *
     * @since 2026-02-09
     */
    public static class Builder extends Stanza.Builder<Builder, Presence> {
        /** Presence 类型 */
        private Type type;
        /** 状态显示 */
        private String show;
        /** 状态描述 */
        private String status;
        /** 优先级 */
        /** 优先级 */
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
            this.type = type;
        }

        /**
         * 构造器。
         *
         * @param type Presence 类型字符串
         */
        public Builder(String type) {
            this.type = Type.fromString(type).orElse(null);
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
            this.type = type;
            return self();
        }

        /**
         * 设置 Presence 类型。
         *
         * @param type Presence 类型字符串
         * @return this
         */
        public Builder type(String type) {
            this.type = Type.fromString(type).orElse(null);
            return self();
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
     *
     * @since 2026-02-09
     */
    public enum Type {
        /** 可用状态 */
        AVAILABLE,
        /** 不可用状态 */
        UNAVAILABLE,
        /** 错误状态 */
        ERROR,
        /** 订阅请求 */
        SUBSCRIBE,
        /** 订阅确认 */
        SUBSCRIBED,
        /** 取消订阅请求 */
        UNSUBSCRIBE,
        /** 取消订阅确认 */
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
            if (type == null || type.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(type.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /**
     * Presence 显示状态枚举。
     *
     * @since 2026-02-09
     */
    public enum Show {
        /** 离开 */
        AWAY,
        /** 聊天 */
        CHAT,
        /** 请勿打扰 */
        DND,
        /** 长时间离开 */
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
            if (show == null || show.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(show.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }
}
