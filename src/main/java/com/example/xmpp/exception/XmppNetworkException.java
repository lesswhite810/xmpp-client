package com.example.xmpp.exception;

import lombok.experimental.StandardException;

import java.io.Serial;

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
 * <h3>构造器说明</h3>
 * <p>本类使用 Lombok {@code @StandardException} 注解自动生成构造器，
 * 继承自 {@link XmppException} 的所有构造器形式。</p>
 *
 * @see XmppException
 * @since 2026-02-13
 */
@StandardException
public class XmppNetworkException extends XmppException {

    @Serial
    private static final long serialVersionUID = 1L;
}
