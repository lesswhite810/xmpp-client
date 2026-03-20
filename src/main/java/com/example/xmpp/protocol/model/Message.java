package com.example.xmpp.protocol.model;

import com.example.xmpp.util.EnumUtils;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Message 节。
 *
 * @since 2026-02-09
 */
@Getter
public final class Message extends Stanza {
    public static final String ELEMENT = "message";

    /**
     * Message 类型。
     */
    private final Type type;
    /**
     * 消息主体。
     */
    private final String body;
    /**
     * 消息主题。
     */
    private final String subject;
    /**
     * 会话线程标识。
     */
    private final String thread;

    private Message(Builder builder) {
        super(builder.getId(), builder.getFrom(), builder.getTo(), builder.getExtensions());
        this.type = builder.type != null ? builder.type : Type.NORMAL;
        this.body = builder.body;
        this.subject = builder.subject;
        this.thread = builder.thread;
    }
    
    /**
     * 判断是否为聊天类型。
     *
     * @return 是否为聊天类型
     */
    public boolean isChat() {
        return type == Type.CHAT;
    }

    /**
     * 判断是否为群聊类型。
     *
     * @return 是否为群聊类型
     */
    public boolean isGroupchat() {
        return type == Type.GROUPCHAT;
    }

    /**
     * 判断是否为头条类型。
     *
     * @return 是否为头条类型
     */
    public boolean isHeadline() {
        return type == Type.HEADLINE;
    }

    /**
     * 判断是否为普通类型。
     *
     * @return 是否为普通类型
     */
    public boolean isNormal() {
        return type == Type.NORMAL;
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
        if (subject != null) {
            xml.wrapElement("subject", subject);
        }
        if (body != null) {
            xml.wrapElement("body", body);
        }
        if (thread != null) {
            xml.wrapElement("thread", thread);
        }
        super.appendExtensions(xml);
    }

    /**
     * Message Builder。
     */
    public static class Builder extends Stanza.Builder<Builder, Message> {
        /**
         * Message 类型。
         */
        private Type type;
        /**
         * 消息主体。
         */
        private String body;
        /**
         * 消息主题。
         */
        private String subject;
        /**
         * 会话线程标识。
         */
        private String thread;

        /**
         * 构造器，默认类型为 normal。
         */
        public Builder() {
            this.type = Type.NORMAL;
        }

        /**
         * 构造器。
         *
         * @param type Message 类型
         */
        public Builder(Type type) {
            this.type = type;
        }

        /**
         * 构造器。
         *
         * @param type Message 类型字符串
         */
        public Builder(String type) {
            this.type = Type.fromString(type).orElse(Type.NORMAL);
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * 设置 Message 类型。
         *
         * @param type Message 类型
         * @return this
         */
        public Builder type(Type type) {
            this.type = type;
            return self();
        }

        /**
         * 设置 Message 类型。
         *
         * @param type Message 类型字符串
         * @return this
         */
        public Builder type(String type) {
            this.type = Type.fromString(type).orElse(Type.NORMAL);
            return self();
        }

        /**
         * 设置消息主体。
         *
         * @param body 消息主体
         * @return this
         */
        public Builder body(String body) {
            this.body = body;
            return self();
        }

        /**
         * 设置消息主题。
         *
         * @param subject 消息主题
         * @return this
         */
        public Builder subject(String subject) {
            this.subject = subject;
            return self();
        }

        /**
         * 设置会话线程标识。
         *
         * @param thread 会话线程标识
         * @return this
         */
        public Builder thread(String thread) {
            this.thread = thread;
            return self();
        }

        /**
         * 构建 Message 实例。
         *
         * @return Message 实例
         */
        @Override
        public Message build() {
            return new Message(this);
        }
    }

    /**
     * Message 类型枚举。
     */
    public enum Type {
        /**
         * 单对单聊天
         */
        CHAT,
        /**
         * 多用户聊天室
         */
        GROUPCHAT,
        /**
         * 头条/通知
         */
        HEADLINE,
        /**
         * 普通消息
         */
        NORMAL,
        /**
         * 错误消息
         */
        ERROR;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        /**
         * 从字符串解析 Message 类型。
         *
         * @param type 类型字符串
         * @return 对应的 Type 枚举值的 Optional，无效则返回 Optional.empty()
         */
        public static Optional<Type> fromString(String type) {
            return EnumUtils.fromString(Type.class, type);
        }
    }
}
