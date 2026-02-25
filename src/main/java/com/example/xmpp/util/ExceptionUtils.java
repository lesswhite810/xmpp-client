package com.example.xmpp.util;

import com.example.xmpp.exception.XmppException;

import lombok.experimental.UtilityClass;

/**
 * 统一异常处理工具类。
 *
 * <p>提供标准化的异常类型转换方法。</p>
 *
 * @since 2026-02-09
 */
@UtilityClass
public class ExceptionUtils {

    /**
     * 将 Throwable 转换为 Exception。
     *
     * @param cause 错误原因
     * @return Exception 对象
     */
    public static Exception toException(Throwable cause) {
        return (cause instanceof Exception) ? (Exception) cause : new Exception(cause);
    }

    /**
     * 将任意对象转换为 XmppException。
     *
     * @param cause 错误原因
     * @return XmppException 对象
     */
    public static XmppException toException(Object cause) {
        if (cause instanceof Throwable t) {
            return (t instanceof XmppException e) ? e : new XmppException(t);
        } else if (cause instanceof String s) {
            return new XmppException(s);
        } else {
            return new XmppException(String.valueOf(cause));
        }
    }
}
