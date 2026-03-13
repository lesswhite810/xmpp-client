package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.XmppStanza;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步节收集器，用于发送请求并等待响应。
 *
 * <p>典型用例包括发送 IQ 请求并等待响应、实现请求-响应关联、支持同步和异步等待。</p>
 *
 * <p><strong>线程安全性：</strong>此类是线程安全的。
 * 使用 {@link AtomicBoolean} 确保在多线程环境下只有一个节被成功收集。</p>
 *
 * @see StanzaFilter
 * @since 2026-02-09
 */
public class AsyncStanzaCollector {

    /**
     * 用于匹配目标响应的过滤器。
     */
    private final StanzaFilter filter;

    /**
     * 收集结果对应的异步结果。
     */
    @Getter
    private final CompletableFuture<XmppStanza> future;

    /**
     * 用于确保只有一个线程成功完成收集操作。
     * CAS 操作保证原子性，避免 check-then-act 竞态条件。
     */
    private final AtomicBoolean collected = new AtomicBoolean(false);

    /**
     * 创建异步节收集器。
     *
     * @param filter 用于匹配响应的过滤器
     */
    public AsyncStanzaCollector(StanzaFilter filter) {
        this.filter = filter;
        this.future = new CompletableFuture<>();
    }

    /**
     * 处理传入的节，检查是否匹配预期的响应。
     *
     * <p>此方法是线程安全的。使用 CAS 操作确保在并发场景下，
     * 即使多个线程同时处理匹配的节，也只有一个能成功完成收集。</p>
     *
     * @param stanza 传入的 XMPP 节
     *
     * @return 如果节被收集（匹配过滤器且 Future 未完成）则返回 true
     */
    public boolean processStanza(XmppStanza stanza) {
        if (collected.get()) {
            return false;
        }

        if (filter.accept(stanza)) {
            if (collected.compareAndSet(false, true)) {
                future.complete(stanza);
                return true;
            }
        }
        return false;
    }
}
