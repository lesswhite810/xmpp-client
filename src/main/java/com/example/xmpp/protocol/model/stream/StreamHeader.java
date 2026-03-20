package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XMPP 流头元素。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class StreamHeader implements ExtensionElement {
    public static final String ELEMENT = "stream";

    /**
     * 发送方域名。
     */
    private final String from;

    /**
     * 接收方域名。
     */
    private final String to;

    /**
     * 流标识。
     */
    private final String id;

    /**
     * XMPP 版本。
     */
    private final String version;

    /**
     * 首选语言。
     */
    private final String lang;

    /**
     * 默认 XML 命名空间。
     */
    private final String namespace;

    /**
     * 获取元素名称。
     *
     * @return 元素名称
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间
     */
    @Override
    public String getNamespace() {
        return XmppConstants.NS_XMPP_STREAMS;
    }

    /**
     * 序列化为 XML 字符串。
     *
     * @return 流头元素 XML 字符串
     */
    @Override
    public String toXml() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("from", from);
        attributes.put("to", to);
        attributes.put("id", id);
        attributes.put("version", version);
        attributes.put("xml:lang", lang);
        attributes.put("xmlns", namespace);
        attributes.put("xmlns:stream", XmppConstants.NS_XMPP_STREAMS);
        return new XmlStringBuilder()
                .openElement(ELEMENT, ELEMENT, null, attributes)
                .toString();
    }
}
