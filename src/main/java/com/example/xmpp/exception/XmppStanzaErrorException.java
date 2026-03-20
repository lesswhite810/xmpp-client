package com.example.xmpp.exception;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import lombok.Getter;

/**
 * XMPP 节错误异常。
 *
 * @since 2026-03-11
 */
public class XmppStanzaErrorException extends XmppException {

    /**
     * 服务端返回的错误 IQ，可能为 null。
     */
    @Getter
    private final Iq errorIq;

    @Getter
    private final XmppError xmppError;

    /**
     * 创建节错误异常。
     *
     * @param message 错误消息
     * @param errorIq 服务端返回的错误 IQ
     */
    public XmppStanzaErrorException(String message, Iq errorIq) {
        super(message);
        this.errorIq = errorIq;
        this.xmppError = errorIq != null ? errorIq.getError() : null;
    }
}
