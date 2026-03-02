package com.example.xmpp.decorator;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志包装器。
 * 
 * <p>包装 XmppConnection，添加日志功能。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * XmppConnection connection = new XmppTcpConnection(config);
 * XmppConnection logged = new LoggingWrapper(connection);
 * }</pre>
 * 
 * @since 2026-03-02
 */
@Slf4j
public class LoggingWrapper {

    private final XmppConnection delegate;

    public LoggingWrapper(XmppConnection delegate) {
        this.delegate = delegate;
    }

    /**
     * 获取被包装的连接。
     */
    public XmppConnection getDelegate() {
        return delegate;
    }

    /**
     * 发送 Stanza 并记录日志。
     */
    public void sendStanza(XmppStanza stanza) {
        log.debug("[SEND] >>> {}", stanza);
        long startTime = System.currentTimeMillis();
        try {
            delegate.sendStanza(stanza);
            log.debug("[SEND] <<< {} ({}ms)", stanza, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[SEND] ERROR {}: {}", stanza, e.getMessage());
            throw e;
        }
    }
}
