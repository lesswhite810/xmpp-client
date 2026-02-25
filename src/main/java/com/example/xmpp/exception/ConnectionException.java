package com.example.xmpp.exception;

import lombok.experimental.StandardException;

import java.io.Serial;

/**
 * 连接异常。
 *
 * <p>用于表示连接建立和维护过程中的异常，是 {@link XmppNetworkException} 的子类。</p>
 *
 * @see XmppNetworkException
 * @since 2026-02-24
 */
@StandardException
public class ConnectionException extends XmppNetworkException {

    @Serial
    private static final long serialVersionUID = 1L;
}
