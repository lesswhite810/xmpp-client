package com.example.xmpp.exception;

/**
 * XMPP 协议级异常。
 *
 * @since 2026-03-11
 */
public class XmppProtocolException extends XmppException {

    /**
     * 创建协议级异常。
     *
     * @param message 异常消息
     */
    public XmppProtocolException(String message) {
        super(message);
    }

    /**
     * 创建协议级异常。
     *
     * @param message 异常消息
     * @param cause   原始异常
     */
    public XmppProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
