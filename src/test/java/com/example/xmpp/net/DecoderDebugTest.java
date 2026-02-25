package com.example.xmpp.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test for XmppStreamDecoder.
 */
class DecoderDebugTest {

    private static final Logger log = LoggerFactory.getLogger(DecoderDebugTest.class);

    @Test
    void debugStreamHeaderParsing() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // Simple stream header - stream element skipped
        String xml = "<?xml version='1.0'?>" +
                "<stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams' " +
                "from='example.com' id='test123'>";

        log.debug("Sending XML: {}", xml);

        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        boolean writeResult = channel.writeInbound(buf);
        log.debug("writeInbound result: {}", writeResult);

        // Check if there are any inbound messages
        Object msg = channel.readInbound();
        log.debug("First readInbound: {}", msg);

        // stream element skipped, returns null
        assertNull(msg, "stream element should be skipped");
    }

    @Test
    void debugStreamHeaderWithoutXmlDecl() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // Stream header without XML declaration - stream element skipped
        String xml = "<stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams' " +
                "from='example.com' id='test123'>";

        log.debug("Sending XML: {}", xml);

        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        channel.writeInbound(buf);

        Object msg = channel.readInbound();
        log.debug("readInbound: {}", msg);

        // stream element skipped, returns null
        assertNull(msg, "stream element should be skipped");
    }

    @Test
    void debugXmlParsingDirectly() throws Exception {
        // Test the XML parsing logic directly without Netty
        String xml = "<stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams' " +
                "from='example.com' id='test123'>";

        log.debug("Testing XML parsing directly: {}", xml);

        XMLInputFactory factory = XMLInputFactory.newInstance();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        XMLEventReader reader = factory.createXMLEventReader(inputStream);

        while (reader.hasNext()) {
            try {
                XMLEvent event = reader.nextEvent();
                log.debug("Event: {} - {}", event.getEventType(), event);

                if (event.isStartElement()) {
                    StartElement start = event.asStartElement();
                    String localName = start.getName().getLocalPart();
                    String namespace = start.getName().getNamespaceURI();
                    log.debug("  LocalName: {}", localName);
                    log.debug("  Namespace: {}", namespace);
                    log.debug("  Prefix: {}", start.getName().getPrefix());
                }
            } catch (Exception e) {
                log.debug("Exception during parsing: {}", e.getMessage());
                break;
            }
        }
    }
}
