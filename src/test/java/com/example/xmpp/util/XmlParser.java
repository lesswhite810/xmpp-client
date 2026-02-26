package com.example.xmpp.util;

import com.example.xmpp.net.XmppStreamDecoder;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.stream.StreamHeader;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * XMPP 解析测试工具类。
 *
 * <p>提供从 XML 字符串解析 Stanza 的便捷方法，仅供测试使用。</p>
 *
 * @since 2026-02-27
 */
public final class XmlParser {

    private XmlParser() {
        // 工具类，禁止实例化
    }

    /**
     * 解析 XML 字符串为协议对象。
     *
     * @param xml XML 字符串
     * @return 解析后的对象，如果解析失败返回 Optional.empty()
     */
    public static Optional<Object> parse(String xml) {
        if (xml == null || xml.isEmpty()) {
            return Optional.empty();
        }
        if (xml.startsWith("<?xml") || xml.contains("stream:stream")) {
            return Optional.of(parseStreamHeader(xml));
        }
        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        return new TestDecoder().parseFromByteBuf(buf);
    }

    /**
     * 解析 XML 字符串为 IQ 节。
     *
     * @param xml XML 字符串
     * @return 解析后的 Iq 对象
     * @throws IllegalArgumentException 如果 XML 不是有效的 IQ 节
     */
    public static Iq parseIq(String xml) {
        return parse(xml)
                .filter(Iq.class::isInstance)
                .map(Iq.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Not an IQ stanza"));
    }

    /**
     * 解析 XML 字符串为 Message 节。
     *
     * @param xml XML 字符串
     * @return 解析后的 Message 对象
     * @throws IllegalArgumentException 如果 XML 不是有效的 Message 节
     */
    public static Message parseMessage(String xml) {
        return parse(xml)
                .filter(Message.class::isInstance)
                .map(Message.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Not a Message stanza"));
    }

    /**
     * 解析 XML 字符串为 Presence 节。
     *
     * @param xml XML 字符串
     * @return 解析后的 Presence 对象
     * @throws IllegalArgumentException 如果 XML 不是有效的 Presence 节
     */
    public static Presence parsePresence(String xml) {
        return parse(xml)
                .filter(Presence.class::isInstance)
                .map(Presence.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Not a Presence stanza"));
    }

    /**
     * 解析 XML 字符串为 Stanza 对象。
     *
     * @param xml XML 字符串
     * @return 解析后的对象，如果解析失败返回 Optional.empty()
     */
    public static Optional<Object> parseStanza(String xml) {
        return parse(xml);
    }

    private static StreamHeader parseStreamHeader(String xml) {
        return StreamHeader.builder()
                .from(extractAttribute(xml, "from"))
                .to(extractAttribute(xml, "to"))
                .id(extractAttribute(xml, "id"))
                .version(extractAttribute(xml, "version"))
                .lang(extractAttribute(xml, "lang"))
                .namespace("jabber:client")
                .build();
    }

    private static String extractAttribute(String xml, String name) {
        int idx = xml.indexOf(name + "='");
        if (idx == -1) {
            idx = xml.indexOf(name + "=\"");
        }
        if (idx == -1) {
            return null;
        }
        char quote = xml.charAt(idx + name.length() + 1);
        int start = idx + name.length() + 2;
        int end = xml.indexOf(quote, start);
        return end == -1 ? null : xml.substring(start, end);
    }

    /**
     * 测试用解码器，暴露 parseFromByteBuf 方法。
     */
    private static class TestDecoder extends XmppStreamDecoder {
        @Override
        protected Optional<Object> parseFromByteBuf(ByteBuf buf) {
            return super.parseFromByteBuf(buf);
        }
    }
}
