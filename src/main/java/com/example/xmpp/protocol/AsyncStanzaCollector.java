package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.XmppStanza;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

/**
 * 异步节收集器，用于发送请求并等待响应。
 *
 * <p>典型用例包括发送 IQ 请求并等待响应、实现请求-响应关联、支持同步和异步等待。
 * 参考 Smack 的 StanzaCollector / IQReplyFilter 设计。</p>
 *
 * @see StanzaFilter
 * @since 2026-02-09
 */
public class AsyncStanzaCollector {

    /** 节过滤器 */
    private final StanzaFilter filter;

    /** 异步结果 Future */
    @Getter
    private final CompletableFuture<XmppStanza> future;

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
     * @param stanza 传入的 XMPP 节
     *
     * @return 如果节被收集（匹配过滤器）则返回 true
     */
    public boolean processStanza(XmppStanza stanza) {
        if (filter.accept(stanza)) {
            future.complete(stanza);
            return true;
        }
        return false;
    }
}
