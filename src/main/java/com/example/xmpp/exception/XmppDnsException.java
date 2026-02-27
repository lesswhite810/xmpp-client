package com.example.xmpp.exception;

import lombok.experimental.StandardException;

import java.io.Serial;

/**
 * XMPP DNS 解析异常。
 *
 * <p>用于表示 DNS 解析相关的异常，包括但不限于：</p>
 * <ul>
 *   <li>SRV 记录查询失败</li>
 *   <li>DNS 服务器无响应</li>
 *   <li>DNS 解析超时</li>
 *   <li>未找到 XMPP 服务记录</li>
 * </ul>
 *
 * @see XmppException
 * @since 2026-02-13
 */
@StandardException
public class XmppDnsException extends XmppException {

    @Serial
    private static final long serialVersionUID = 1L;
}
