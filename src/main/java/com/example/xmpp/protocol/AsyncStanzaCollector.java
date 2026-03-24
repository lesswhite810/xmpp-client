package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.XmppStanza;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步节收集器。
 *
 * @see StanzaFilter
 * @since 2026-02-09
 */
public class AsyncStanzaCollector {

    /**
     * 匹配目标响应的过滤器。
     */
    private final StanzaFilter filter;

    /**
     * 收集结果对应的异步结果。
     */
    @Getter
    private final CompletableFuture<XmppStanza> future;

    /**
     * 确保只收集一次。
     */
    private final AtomicBoolean collected = new AtomicBoolean(false);

    /**
     * 创建异步节收集器。
     *
     * @param filter 过滤器
     */
    public AsyncStanzaCollector(StanzaFilter filter) {
        this.filter = filter;
        this.future = new CompletableFuture<>();
    }

    /**
     * 处理传入的节。
     *
     * @param stanza XMPP 节
     * @return 是否收集成功
     */
    public boolean processStanza(XmppStanza stanza) {
        if (!filter.accept(stanza)) {
            return false;
        }

        // filter 已匹配后再抢占收集权，避免非匹配节阻塞后续匹配节。
        if (!collected.compareAndSet(false, true)) {
            return false;
        }

        future.complete(stanza);
        return true;
    }
}
