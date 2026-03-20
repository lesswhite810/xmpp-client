package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.ExtensionElement;

/**
 * 扩展元素 Provider 接口。
 *
 * @param <T> 扩展元素类型
 * @since 2026-02-26
 * @see ExtensionElement
 */
public interface ExtensionElementProvider<T extends ExtensionElement> extends Provider<T> {
}
