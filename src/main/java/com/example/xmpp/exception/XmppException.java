package com.example.xmpp.exception;

import lombok.experimental.StandardException;

/**
 * XMPP 基础异常类。
 *
 * <p>所有 XMPP 相关的异常都应继承此类。此类作为 XMPP 客户端异常层次结构的根，
 * 提供统一的异常处理机制。</p>
 *
 * <h3>异常层次结构</h3>
 * <ul>
 *   <li>{@link XmppException} - 基础异常</li>
 *   <li>{@link XmppAuthException} - 认证相关异常</li>
 *   <li>{@link XmppNetworkException} - 网络连接相关异常</li>
 *   <li>{@link XmppParseException} - XML 解析相关异常</li>
 *   <li>{@link XmppDnsException} - DNS 解析相关异常</li>
 * </ul>
 *
 * @since 2026-02-09
 */
@StandardException
public class XmppException extends Exception {

    @java.io.Serial
    private static final long serialVersionUID = 1L;
}
