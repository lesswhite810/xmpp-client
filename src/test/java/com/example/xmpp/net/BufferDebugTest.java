package com.example.xmpp.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug test for buffer management in XmppStreamDecoder.
 */
class BufferDebugTest {

    private static final Logger log = LoggerFactory.getLogger(BufferDebugTest.class);

    @Test
    void debugCharacterOffset() throws Exception {
        // Test character offsets in XML parsing
        String xml = "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>";

        log.debug("XML: {}", xml);
        log.debug("Length: {}", xml.length());

        XMLInputFactory factory = XMLInputFactory.newInstance();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        XMLEventReader reader = factory.createXMLEventReader(inputStream);

        while (reader.hasNext()) {
            try {
                XMLEvent event = reader.nextEvent();
                int offset = (int) event.getLocation().getCharacterOffset();
                log.debug("Event type: {}, charOffset: {}, remaining: {}",
                        event.getEventType(), offset, xml.length() - offset);
            } catch (Exception e) {
                log.debug("Exception: {}", e.getMessage());
                break;
            }
        }
    }

    @Test
    void debugBufferState() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // Step 1: Send stream header
        String streamHeader = "<stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams'>";
        log.debug("\n=== Step 1: Sending stream header ===");
        sendXml(channel, streamHeader);

        // Read all messages
        List<Object> messages1 = readAllMessages(channel);
        log.debug("Messages after step 1: {}", messages1.size());
        for (Object msg : messages1) {
            log.debug("  - {}", msg.getClass().getSimpleName());
        }

        // Step 2: Send features
        String features = "<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>";
        log.debug("\n=== Step 2: Sending features ===");
        sendXml(channel, features);

        // Only read NEW messages (not accumulated ones)
        List<Object> messages2 = new ArrayList<>();
        Object msg2;
        while ((msg2 = channel.readInbound()) != null) {
            // Skip messages that were already read in step 1
            if (messages1.contains(msg2)) {
                log.debug("  Skipping duplicate: {}", msg2.getClass().getSimpleName());
            } else {
                messages2.add(msg2);
            }
        }
        log.debug("NEW messages after step 2: {}", messages2.size());
        for (Object msg : messages2) {
            log.debug("  - {}", msg.getClass().getSimpleName());
        }

        // Step 3: Send IQ
        String iq = "<iq type='get' id='test1'><ping xmlns='urn:xmpp:ping'/></iq>";
        log.debug("\n=== Step 3: Sending IQ ===");
        sendXml(channel, iq);

        List<Object> messages3 = new ArrayList<>();
        Object msg3;
        while ((msg3 = channel.readInbound()) != null) {
            // Skip messages that were already read
            if (messages1.contains(msg3) || messages2.contains(msg3)) {
                log.debug("  Skipping duplicate: {}", msg3.getClass().getSimpleName());
            } else {
                messages3.add(msg3);
            }
        }
        log.debug("NEW messages after step 3: {}", messages3.size());
        for (Object msg : messages3) {
            log.debug("  - {}", msg.getClass().getSimpleName());
        }
    }

    private void sendXml(EmbeddedChannel channel, String xml) {
        log.debug("Sending: {}", xml);
        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        channel.writeInbound(buf);
    }

    private List<Object> readAllMessages(EmbeddedChannel channel) {
        List<Object> messages = new ArrayList<>();
        Object msg;
        while ((msg = channel.readInbound()) != null) {
            messages.add(msg);
        }
        return messages;
    }
}
