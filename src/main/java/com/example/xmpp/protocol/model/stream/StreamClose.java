package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmppConstants;

/**
 * XMPP 流关闭标记。
 *
 * @since 2026-03-24
 */
public enum StreamClose implements ExtensionElement {

    INSTANCE;

    /**
     * 元素名称。
     */
    public static final String ELEMENT = "stream";

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    @Override
    public String getNamespace() {
        return XmppConstants.NS_XMPP_STREAMS;
    }

    @Override
    public String toXml() {
        return "</stream:stream>";
    }
}
