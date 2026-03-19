package com.example.xmpp.protocol.model;

/**
 * XMPP 节（Stanza）基接口。
 *
 * XMPP 节是 XMPP 通信的基本单元，包含三种核心节类型：
 * <ul>
 *   <li><iq> - 信息/查询，用于请求和响应</li>
 *   <li><message> - 消息，用于推送消息</li>
 *   <li><presence> - 在线状态，用于Presence通知</li>
 * </ul>
 *
 * 此接口定义了所有 XMPP 节共有的属性，包括唯一标识符（id）、
 * 发送方（from）和接收方（to）的 Jabber ID。
 *
 * @since 2026-02-09
 */
public interface XmppStanza {

    /**
     * 获取节的唯一标识符。
     *
     * @return 节 ID，可能为 null
     */
    String getId();

    /**
     * 获取节的发送方 JID。
     *
     * @return 发送方的 Jabber ID，可能为 null
     */
    String getFrom();

    /**
     * 获取节的接收方 JID。
     *
     * @return 接收方的 Jabber ID，可能为 null
     */
    String getTo();
}
