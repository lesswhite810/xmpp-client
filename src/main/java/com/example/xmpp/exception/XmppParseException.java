package com.example.xmpp.exception;

import lombok.experimental.StandardException;

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
 * <h3>构造器说明</h3>
 * <p>本类使用 Lombok {@code @StandardException} 注解自动生成构造器，
 * 继承自 {@link XmppException} 的所有构造器形式。</p>
 *
 * @see XmppException
 * @since 2026-02-13
 */
@StandardException
public class XmppParseException extends XmppException {
}
