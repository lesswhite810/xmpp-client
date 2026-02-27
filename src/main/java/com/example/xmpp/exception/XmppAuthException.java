package com.example.xmpp.exception;

import lombok.experimental.StandardException;

import java.io.Serial;

/**
 * XMPP 认证异常。
 *
 * <p>用于表示认证相关的异常，包括但不限于：</p>
 * <ul>
 *   <li>SASL 认证失败</li>
 *   <li>凭据无效或过期</li>
 *   <li>认证机制不支持</li>
 *   <li>服务器拒绝认证</li>
 * </ul>
 *
 * @see XmppException
 * @since 2026-02-13
 */
@StandardException
public class XmppAuthException extends XmppException {

    @Serial
    private static final long serialVersionUID = 1L;
}
