package com.example.xmpp.protocol.provider;

import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.util.XmlParserUtils;
import com.example.xmpp.util.XmlStringBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * PingProvider 测试。
 *
 * @since 2026-03-20
 */
class PingProviderTest {

    @Test
    @DisplayName("解析 ping 元素应返回单例")
    void testParseReturnsSingleton() throws Exception {
        PingProvider provider = new PingProvider();
        XMLEventReader reader = XmlParserUtils.createReader("<ping xmlns='urn:xmpp:ping'/>".getBytes());
        reader.nextEvent();
        reader.nextEvent();

        Ping ping = provider.parse(reader);

        assertSame(Ping.INSTANCE, ping);
    }

    @Test
    @DisplayName("解析带空白内容的 ping 元素应跳过非结束事件并返回单例")
    void testParseReturnsSingletonForExpandedElement() throws Exception {
        PingProvider provider = new PingProvider();
        XMLEventReader reader = XmlParserUtils.createReader("<ping xmlns='urn:xmpp:ping'> \n</ping>".getBytes());
        reader.nextEvent();
        reader.nextEvent();

        Ping ping = provider.parse(reader);

        assertSame(Ping.INSTANCE, ping);
    }

    @Test
    @DisplayName("序列化 ping 应输出自闭合元素")
    void testSerializePing() {
        PingProvider provider = new PingProvider();
        XmlStringBuilder xml = new XmlStringBuilder();

        provider.serialize(Ping.INSTANCE, xml);

        assertEquals("<ping xmlns=\"urn:xmpp:ping\"/>", xml.toString());
    }
}
