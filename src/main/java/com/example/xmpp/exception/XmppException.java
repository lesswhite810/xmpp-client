package com.example.xmpp.exception;

import lombok.experimental.StandardException;

import java.io.Serial;

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
 *   <li>{@link AdminCommandException} - 管理命令执行异常</li>
 * </ul>
 *
 * <h3>构造器说明</h3>
 * <p>本类使用 Lombok {@code @StandardException} 注解自动生成构造器，
 * 支持以下构造器形式：</p>
 * <ul>
 *   <li>{@code XmppException()} - 无参构造器</li>
 *   <li>{@code XmppException(String message)} - 带消息构造器</li>
 *   <li>{@code XmppException(String message, Throwable cause)} - 带消息和原因构造器</li>
 *   <li>{@code XmppException(Throwable cause)} - 带原因构造器</li>
 * </ul>
 *
 * @since 2026-02-09
 */
@StandardException
public class XmppException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;
}
