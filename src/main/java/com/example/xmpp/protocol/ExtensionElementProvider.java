package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.ExtensionElement;

/**
 * 扩展元素 Provider 接口。
 *
 * <p>用于解析 IQ、Message、Presence 节的扩展子元素。
 * 扩展元素是附加到节上的额外数据，如 Ping、Bind、Delay 等。</p>
 *
 * <p>示例：</p>
 * <ul>
 *   <li>Ping 扩展: {@code <ping xmlns="urn:xmpp:ping"/>}</li>
 *   <li>Bind 扩展: {@code <bind xmlns="urn:ietf:params:xml:ns:xmpp-bind">...</bind>}</li>
 *   <li>延迟信息: {@code <x xmlns="jabber:x:delay">...</x>}</li>
 * </ul>
 *
 * @param <T> 扩展元素类型
 * @since 2026-02-26
 * @see ExtensionElement
 */
public interface ExtensionElementProvider<T extends ExtensionElement> extends Provider<T> {
}
