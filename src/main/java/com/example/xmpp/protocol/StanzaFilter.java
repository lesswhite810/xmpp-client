package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.XmppStanza;

/**
 * XMPP 节过滤器接口。
 *
 * <p>用于确定特定的 XMPP 节是否应该被监听器处理。
 * 参考 Smack 的 StanzaFilter 设计。</p>
 *
 * @see StanzaListener
 * @see AsyncStanzaCollector
 * @since 2026-02-09
 */
public interface StanzaFilter {

    /**
     * 判断监听器是否应该接受并处理此节。
     *
     * @param stanza 要检查的 XMPP 节
     *
     * @return 如果节应该被处理则返回 true，否则返回 false
     */
    boolean accept(XmppStanza stanza);
}
