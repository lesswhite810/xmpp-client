package com.example.xmpp.exception;

import lombok.experimental.StandardException;

import java.io.Serial;

/**
 * XMPP 解析异常。
 *
 * <p>用于表示 XML 解析相关的异常，包括但不限于：</p>
 * <ul>
 *   <li>XML 格式错误</li>
 *   <li>无效的 XMPP 节格式</li>
 *   <li>未知的元素或属性</li>
 *   <li>字符编码问题</li>
 * </ul>
 *
 * @see XmppException
 * @since 2026-02-13
 */
@StandardException
public class XmppParseException extends XmppException {

    @Serial
    private static final long serialVersionUID = 1L;
}
