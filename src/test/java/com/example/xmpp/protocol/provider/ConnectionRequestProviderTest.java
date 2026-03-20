package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.extension.ConnectionRequest;
import com.example.xmpp.util.XmlParserUtils;
import com.example.xmpp.util.XmlStringBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConnectionRequestProvider 行为测试。
 */
class ConnectionRequestProviderTest {

    private final ConnectionRequestProvider provider = new ConnectionRequestProvider();

    @Test
    @DisplayName("parse 应解析 username 和 password")
    void testParse() throws Exception {
        XMLEventReader reader = createReader("""
                <connectionRequest xmlns="urn:broadband-forum-org:cwmp:xmppConnReq-1-0">
                    <username>acs-user</username>
                    <password>acs-pass</password>
                </connectionRequest>
                """);

        ConnectionRequest request = provider.parse(reader);

        assertEquals("acs-user", request.getUsername());
        assertEquals("acs-pass", request.getPassword());
    }

    @Test
    @DisplayName("parse 应忽略未知子元素")
    void testParseIgnoresUnknownElements() throws Exception {
        XMLEventReader reader = createReader("""
                <connectionRequest xmlns="urn:broadband-forum-org:cwmp:xmppConnReq-1-0">
                    <ignored>value</ignored>
                    <username>acs-user</username>
                    <password>acs-pass</password>
                </connectionRequest>
                """);

        ConnectionRequest request = provider.parse(reader);

        assertEquals("acs-user", request.getUsername());
        assertEquals("acs-pass", request.getPassword());
    }

    @Test
    @DisplayName("parse 在存在 XML 声明和空白时仍应正确解析字段")
    void testParseWithXmlDeclarationAndWhitespace() throws Exception {
        XMLEventReader reader = createReader("""
                <?xml version="1.0"?>
                <connectionRequest xmlns="urn:broadband-forum-org:cwmp:xmppConnReq-1-0">
                    
                    <username>acs-user</username>
                    <password>acs-pass</password>
                </connectionRequest>
                """);

        ConnectionRequest request = provider.parse(reader);

        assertEquals("acs-user", request.getUsername());
        assertEquals("acs-pass", request.getPassword());
    }

    @Test
    @DisplayName("parse 在缺少必填字段时应抛出异常")
    void testParseFailsWhenPasswordMissing() throws Exception {
        XMLEventReader reader = createReader("""
                <connectionRequest xmlns="urn:broadband-forum-org:cwmp:xmppConnReq-1-0">
                    <username>acs-user</username>
                </connectionRequest>
                """);

        XmppParseException exception = assertThrows(XmppParseException.class, () -> provider.parse(reader));

        assertTrue(exception.getMessage().contains("connectionRequest"));
    }

    @Test
    @DisplayName("parse 在缺少 username 时应抛出异常")
    void testParseFailsWhenUsernameMissing() throws Exception {
        XMLEventReader reader = createReader("""
                <connectionRequest xmlns="urn:broadband-forum-org:cwmp:xmppConnReq-1-0">
                    <password>acs-pass</password>
                </connectionRequest>
                """);

        XmppParseException exception = assertThrows(XmppParseException.class, () -> provider.parse(reader));

        assertTrue(exception.getMessage().contains("connectionRequest"));
    }

    @Test
    @DisplayName("serialize 应输出 ConnectionRequest XML")
    void testSerialize() {
        XmlStringBuilder xml = new XmlStringBuilder();

        provider.serialize(ConnectionRequest.builder()
                .username("acs-user")
                .password("acs-pass")
                .build(), xml);

        assertEquals(
                "<connectionRequest xmlns=\"urn:broadband-forum-org:cwmp:xmppConnReq-1-0\"><username>acs-user</username><password>acs-pass</password></connectionRequest>",
                xml.toString());
    }

    private static XMLEventReader createReader(String xml) throws XMLStreamException {
        XMLEventReader reader = XmlParserUtils.createInputFactory().createXMLEventReader(new StringReader(xml));
        while (reader.hasNext() && !reader.peek().isStartElement()) {
            reader.nextEvent();
        }
        return reader;
    }
}
