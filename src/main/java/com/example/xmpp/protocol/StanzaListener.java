package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.XmppStanza;

/**
 * XMPP 节监听器接口。
 *
 * <p>用于监听和处理接收到的 XMPP 节。
 * 参考 Smack 的 StanzaListener 设计。
 * 监听器通常与 StanzaFilter 配合使用，仅处理匹配过滤条件的节。</p>
 *
 * @see StanzaFilter
 * @since 2026-02-09
 */
public interface StanzaListener {

    /**
     * 处理接收到的 XMPP 节。
     *
     * <p>此方法在 Netty EventLoop 线程中调用，应避免耗时操作以防止阻塞网络 I/O。</p>
     *
     * @param stanza 接收到的 XMPP 节
     */
    void processStanza(XmppStanza stanza);
}
