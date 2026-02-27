package com.example.xmpp;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.util.XmlParser;
import com.example.xmpp.util.XmlStringBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XML 节处理性能测试
 * 
 * 测试 XML 节的创建、解析、序列化和反序列化的性能，以及内存使用效率。
 */
public class XmlStanzaPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(XmlStanzaPerformanceTest.class);

    private static final int ITERATIONS = 10000;

    /**
     * 测试 IQ 节的创建性能
     */
    @Test
    public void testIqCreationPerformance() {
        log.info("Testing IQ creation performance with {} iterations...", ITERATIONS);

        long startTime = System.nanoTime();

        List<Iq> iqs = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            Iq iq = new Iq.Builder("get")
                    .id("test-" + i)
                    .from("user@example.com/resource")
                    .to("server.example.com")
                    .childElement(new Ping())
                    .build();
            iqs.add(iq);
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        log.info("IQ creation performance: {} iterations in {} ms ({} ms per iteration)",
                ITERATIONS, durationMs, (double) durationMs / ITERATIONS);
        log.info("Memory used by IQ objects: ~{} bytes (estimated)", iqs.size() * 128);

        iqs.clear();
    }

    /**
     * 测试 Message 节的创建性能
     */
    @Test
    public void testMessageCreationPerformance() {
        log.info("Testing Message creation performance with {} iterations...", ITERATIONS);

        long startTime = System.nanoTime();

        List<Message> messages = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            Message message = new Message.Builder()
                    .type("chat")
                    .id("msg-" + i)
                    .from("alice@example.com/desktop")
                    .to("bob@example.com/mobile")
                    .subject("Test Subject " + i)
                    .body("Hello Bob! This is test message " + i)
                    .build();
            messages.add(message);
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        log.info("Message creation performance: {} iterations in {} ms ({} ms per iteration)",
                ITERATIONS, durationMs, (double) durationMs / ITERATIONS);

        messages.clear();
    }

    /**
     * 测试 Presence 节的创建性能
     */
    @Test
    public void testPresenceCreationPerformance() {
        log.info("Testing Presence creation performance with {} iterations...", ITERATIONS);

        long startTime = System.nanoTime();

        List<Presence> presences = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            Presence presence = new Presence.Builder()
                    .type("available")
                    .id("pres-" + i)
                    .from("user@example.com/laptop")
                    .to("friend@example.com")
                    .show("chat")
                    .status("Online and ready to chat " + i)
                    .priority(i % 10)
                    .build();
            presences.add(presence);
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        log.info("Presence creation performance: {} iterations in {} ms ({} ms per iteration)",
                ITERATIONS, durationMs, (double) durationMs / ITERATIONS);

        presences.clear();
    }

    /**
     * 测试 XML 序列化性能
     */
    @Test
    public void testXmlSerializationPerformance() {
        log.info("Testing XML serialization performance with {} iterations...", ITERATIONS);

        List<Iq> iqs = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            Iq iq = new Iq.Builder("get")
                    .id("test-" + i)
                    .from("user@example.com/resource")
                    .to("server.example.com")
                    .childElement(new Ping())
                    .build();
            iqs.add(iq);
        }

        long startTime = System.nanoTime();

        for (Iq iq : iqs) {
            String xmlString = iq.toXml();
            assertFalse(xmlString.isEmpty());
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        log.info("XML serialization performance: {} iterations in {} ms ({} ms per iteration)",
                ITERATIONS, durationMs, (double) durationMs / ITERATIONS);

        iqs.clear();
    }

    /**
     * 测试 XML 解析性能
     */
    @Test
    public void testXmlParsingPerformance() {
        log.info("Testing XML parsing performance with {} iterations...", ITERATIONS);

        String testXml = "<iq type=\"result\" id=\"bind-234\" to=\"user@example.com/resource\">" +
                       "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">" +
                       "<jid>user@example.com/resource</jid>" +
                       "</bind>" +
                       "</iq>";

        long startTime = System.nanoTime();

        List<Iq> parsedIqs = new ArrayList<>(ITERATIONS);
        for (int i = 0; i < ITERATIONS; i++) {
            Iq iq = XmlParser.parseIq(testXml);
            if (iq != null) {
                parsedIqs.add(iq);
            }
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        log.info("XML parsing performance: {} iterations in {} ms ({} ms per iteration)",
                ITERATIONS, durationMs, (double) durationMs / ITERATIONS);

        parsedIqs.clear();
    }

    /**
     * 测试 Provider 机制性能
     */
    @Test
    public void testProviderMechanismPerformance() {
        log.info("Testing Provider mechanism performance with {} iterations...", ITERATIONS);

        String testXml = "<iq type=\"result\" id=\"bind-234\" to=\"user@example.com/resource\">" +
                       "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">" +
                       "<jid>user@example.com/resource</jid>" +
                       "</bind>" +
                       "</iq>";

        long startTime = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            Iq iq = XmlParser.parseIq(testXml);
            assertNotNull(iq);
            assertNotNull(iq.getChildElement());
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        log.info("Provider mechanism performance: {} iterations in {} ms ({} ms per iteration)",
                ITERATIONS, durationMs, (double) durationMs / ITERATIONS);
    }

    /**
     * 测试内存使用效率 - 批量创建和解析
     */
    @Test
    public void testMemoryUsageEfficiency() {
        log.info("Testing memory usage efficiency...");

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

        int batchSize = 5000;
        List<Iq> iqs = new ArrayList<>(batchSize);

        for (int i = 0; i < batchSize; i++) {
            Iq iq = new Iq.Builder("get")
                    .id("test-" + i)
                    .from("user@example.com/resource")
                    .to("server.example.com")
                    .childElement(new Ping())
                    .build();
            iqs.add(iq);

            String xmlString = iq.toXml();

            Iq parsedIq = XmlParser.parseIq(xmlString);
            assertNotNull(parsedIq);
        }

        runtime.gc();
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = afterMemory - beforeMemory;

        log.info("Memory usage efficiency: Batch size = {}", batchSize);
        log.info("Memory used: {} bytes (~{} bytes per operation)",
                memoryUsed, memoryUsed / (batchSize * 2));

        iqs.clear();
    }

    /**
     * 测试 XmlStringBuilder 的性能
     */
    @Test
    public void testXmlStringBuilderPerformance() {
        log.info("Testing XmlStringBuilder performance with {} iterations...", ITERATIONS);

        long startTime = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            XmlStringBuilder xml = new XmlStringBuilder();
            xml.append("<iq type=\"get\" id=\"test-").append(i).append("\" ")
               .append("from=\"user@example.com/resource\" ")
               .append("to=\"server.example.com\">")
               .append("<ping xmlns=\"urn:xmpp:ping\"/>")
               .append("</iq>");
            String result = xml.toString();
            assertFalse(result.isEmpty());
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        log.info("XmlStringBuilder performance: {} iterations in {} ms ({} ms per iteration)",
                ITERATIONS, durationMs, (double) durationMs / ITERATIONS);
    }
}
