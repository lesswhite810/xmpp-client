package com.example.xmpp.exception;

/**
 * XMPP 协议级异常。
 *
 * <p>用于表示非 IQ 请求级别的协议错误，例如 SASL 失败和 stream error。</p>
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
