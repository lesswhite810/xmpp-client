package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XMPP 流头元素，实现 RFC 6120 §4.7 Stream Start。
 * <p>
 * 表示 XMPP 流的起始元素，包含连接的初始化参数：
 * <ul>
 *     <li>from - 发送方域名</li>
 *     <li>to - 接收方域名</li>
 *     <li>id - 服务端分配的唯一流标识</li>
 *     <li>version - XMPP 版本号</li>
 *     <li>xml:lang - 首选语言</li>
 *     <li>xmlns - 默认命名空间</li>
 * </ul>
 * 该元素在建立 XMPP 连接时由客户端首次发送，服务端响应包含自身的 stream 元素。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class StreamHeader implements ExtensionElement {

    /**
     * 流命名空间。
     */
    private static final String NAMESPACE_STREAM = XmppConstants.NS_XMPP_STREAMS;

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
     * @return 固定返回 {@code stream}
     */
    @Override
    public String getElementName() {
        return "stream";
    }

    /**
     * 获取命名空间。
     *
     * @return 流命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE_STREAM;
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
        attributes.put("xmlns:stream", NAMESPACE_STREAM);
        return new XmlStringBuilder()
                .openElement("stream", "stream", null, attributes)
                .toString();
    }
}
