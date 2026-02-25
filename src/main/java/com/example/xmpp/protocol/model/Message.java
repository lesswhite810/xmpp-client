package com.example.xmpp.protocol.model;

import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Message 节。
 *
 * Message 节用于在 XMPP 实体之间交换消息，是 XMPP 中三种基本节类型之一。
 * 支持的类型：chat（单对单聊天）、groupchat（多用户聊天室）、headline（头条/通知）、normal（普通消息）、error（错误消息）。
 *
 * @since 2026-02-09
 */
@Getter
public final class Message extends Stanza {

    /** Message 类型 */
    private final Type type;
    /** 消息主体 */
    private final String body;
    /** 消息主题 */
    private final String subject;
    /** 会话线程标识 */
    private final String thread;

    private Message(Builder builder) {
        super(builder.getId(), builder.getFrom(), builder.getTo(), builder.getExtensions());
        this.type = builder.type != null ? builder.type : Type.normal;
        this.body = builder.body;
        this.subject = builder.subject;
        this.thread = builder.thread;
    }

    /**
     * 完整构造器。
     *
     * @param type Message 类型
     * @param id 唯一标识符
     * @param from 发送方 JID
     * @param to 接收方 JID
     * @param body 消息主体
     * @param subject 消息主题
     * @param thread 会话线程标识
     * @param extensions 扩展元素
     */
    public Message(Type type, String id, String from, String to, String body, String subject, String thread,
            List<ExtensionElement> extensions) {
        super(id, from, to, extensions);
        this.type = type != null ? type : Type.normal;
        this.body = body;
        this.subject = subject;
        this.thread = thread;
    }

    /**
     * 空构造器，用于解析时创建实例。
     */
    public Message() {
        this(Type.normal, null, null, null, null, null, null, null);
    }

    /**
     * 判断是否为聊天类型。
     *
     * @return 是聊天类型返回 true
     */
    public boolean isChat() {
        return type == Type.chat;
    }

    /**
     * 判断是否为群聊类型。
     *
     * @return 是群聊类型返回 true
     */
    public boolean isGroupchat() {
        return type == Type.groupchat;
    }

    /**
     * 判断是否为头条类型。
     *
     * @return 是头条类型返回 true
     */
    public boolean isHeadline() {
        return type == Type.headline;
    }

    /**
     * 判断是否为普通类型。
     *
     * @return 是普通类型返回 true
     */
    public boolean isNormal() {
        return type == Type.normal;
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
     * 获取元素名称。
     *
     * @return "message"
     */
    @Override
    public String getElementName() {
        return "message";
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
        xml.optTextElement("subject", subject)
           .optTextElement("body", body)
           .optTextElement("thread", thread);
        super.appendExtensions(xml);
    }

    /**
     * Message Builder。
     *
     * @since 2026-02-09
     */
    public static class Builder extends Stanza.Builder<Builder, Message> {
        /** Message 类型 */
        private Type type;
        /** 消息主体 */
        private String body;
        /** 消息主题 */
        private String subject;
        /** 会话线程标识 */
        private String thread;

        /**
         * 构造器，默认类型为 normal。
         */
        public Builder() {
            this.type = Type.normal;
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
            this.type = Type.fromString(type).orElse(Type.normal);
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
            this.type = Type.fromString(type).orElse(Type.normal);
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
     *
     * @since 2026-02-09
     */
    public enum Type {
        /** 单对单聊天 */
        chat,
        /** 多用户聊天室 */
        groupchat,
        /** 头条/通知 */
        headline,
        /** 普通消息 */
        normal,
        /** 错误消息 */
        error;

        /**
         * 从字符串解析 Message 类型。
         *
         * @param type 类型字符串
         * @return 对应的 Type 枚举值的 Optional，无效则返回 Optional.empty()
         */
        public static Optional<Type> fromString(String type) {
            if (type == null || type.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(type.toLowerCase()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }
}
