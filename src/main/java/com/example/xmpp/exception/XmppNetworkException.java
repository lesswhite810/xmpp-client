package com.example.xmpp.exception;

import lombok.experimental.StandardException;

/**
 * XMPP 网络异常。
 *
 * <p>用于表示网络连接相关的异常，包括但不限于：</p>
 * <ul>
 *   <li>连接超时</li>
 *   <li>连接断开</li>
 *   <li>网络 I/O 错误</li>
 *   <li>连接被拒绝</li>
 * </ul>
 *
 * @see XmppException
 * @since 2026-02-13
 */
@StandardException
public class XmppNetworkException extends XmppException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;
}
