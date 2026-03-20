package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.XmppStanza;

/**
 * XMPP 节过滤器接口。
 *
 * @see AsyncStanzaCollector
 * @since 2026-02-09
 */
@FunctionalInterface
public interface StanzaFilter {

    /**
     * 判断是否接受该节。
     *
     * @param stanza XMPP 节
     * @return 是否接受
     */
    boolean accept(XmppStanza stanza);
}
