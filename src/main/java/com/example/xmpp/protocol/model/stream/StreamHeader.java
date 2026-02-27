package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * XMPP 流头。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class StreamHeader implements ExtensionElement {

    private static final String NAMESPACE_STREAM = XmppConstants.NS_XMPP_STREAMS;

    private final String from;
    private final String to;
    private final String id;
    private final String version;
    private final String lang;
    private final String namespace;

    /**
     * 获取元素名称。
     *
     * @return 元素名称 "stream"
     */
    @Override
    public String getElementName() {
        return "stream";
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE_STREAM;
    }

    /**
     * 序列化为 XML 字符串。
     *
     * @return XML 字符串表示
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .element("stream", "stream", null)
                .attribute("from", from)
                .attribute("to", to)
                .attribute("id", id)
                .attribute("version", version)
                .attribute("xml:lang", lang)
                .attribute("xmlns", namespace)
                .attribute("xmlns:stream", NAMESPACE_STREAM)
                .rightAngleBracket()
                .toString();
    }
}
