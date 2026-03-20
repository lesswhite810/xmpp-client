package com.example.xmpp.protocol.model;

/**
 * XMPP 节基接口。
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
