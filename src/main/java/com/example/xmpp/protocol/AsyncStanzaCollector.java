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
        // compareAndSet 同时完成：检查是否已收集 + 原子标记
        if (!collected.compareAndSet(false, true)) {
            return false;
        }

        // 只有获取了"收集权"的线程才执行 filter
        if (filter.accept(stanza)) {
            future.complete(stanza);
            return true;
        }

        // filter 不匹配，重置为 false，让后续 stanza 有机会被收集
        collected.set(false);
        return false;
    }
}
