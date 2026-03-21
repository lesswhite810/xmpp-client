package com.example.xmpp.protocol.provider;

import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.util.XmlParserUtils;
import com.example.xmpp.util.XmlStringBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BindProvider 测试。
 *
 * @since 2026-03-20
 */
class BindProviderTest {

    @Test
    @DisplayName("应解析 jid 与 resource 子元素")
    void testParseBind() throws Exception {
        String xml = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>"
                + "<jid>alice@example.com/device</jid>"
                + "<resource>device</resource>"
                + "</bind>";
        BindProvider provider = new BindProvider();
        XMLEventReader reader = XmlParserUtils.createReader(xml.getBytes());
        reader.nextEvent();
        reader.nextEvent();

        Bind bind = provider.parse(reader);

        assertEquals("alice@example.com/device", bind.getJid());
        assertEquals("device", bind.getResource());
    }

    @Test
    @DisplayName("未知子元素应被忽略")
    void testParseIgnoresUnknownChild() throws Exception {
        String xml = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><unknown>value</unknown></bind>";
        BindProvider provider = new BindProvider();
        XMLEventReader reader = XmlParserUtils.createReader(xml.getBytes());
        reader.nextEvent();
        reader.nextEvent();

        Bind bind = provider.parse(reader);

        assertNull(bind.getJid());
        assertNull(bind.getResource());
    }

    @Test
    @DisplayName("同名但不同命名空间的子元素应被忽略")
    void testParseIgnoresKnownChildWithDifferentNamespace() throws Exception {
        String xml = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>"
                + "<jid xmlns='urn:test:other'>alice@example.com/wrong</jid>"
                + "<resource>device</resource>"
                + "</bind>";
        BindProvider provider = new BindProvider();
        XMLEventReader reader = XmlParserUtils.createReader(xml.getBytes());
        reader.nextEvent();
        reader.nextEvent();

        Bind bind = provider.parse(reader);

        assertNull(bind.getJid());
        assertEquals("device", bind.getResource());
    }

    @Test
    @DisplayName("序列化 bind 应输出模型 XML")
    void testSerializeBind() {
        BindProvider provider = new BindProvider();
        XmlStringBuilder xml = new XmlStringBuilder();

        provider.serialize(Bind.builder().resource("desktop").jid("alice@example.com/desktop").build(), xml);

        assertEquals(
                "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"><resource>desktop</resource>"
                        + "<jid>alice@example.com/desktop</jid></bind>",
                xml.toString());
    }
}
