package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.net.SrvRecord;
import com.example.xmpp.protocol.*;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.sasl.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Core functionality unit tests.
 */
class CoreFunctionalityTest {

    @Test
    void testXmppClientConfigBuilder() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("testuser")
                .password("testpass".toCharArray())
                .host("xmpp.example.com")
                .port(5222)
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .sendPresence(true)
                .build();

        assertEquals("example.com", config.getXmppServiceDomain());
        assertEquals("testuser", config.getUsername());
        assertArrayEquals("testpass".toCharArray(), config.getPassword());
        assertEquals("xmpp.example.com", config.getHost());
        assertEquals(5222, config.getPort());
        assertEquals(XmppClientConfig.SecurityMode.IF_POSSIBLE, config.getSecurityMode());
        assertTrue(config.isSendPresence());
    }

    @Test
    void testSrvRecordComparable() {
        SrvRecord high = new SrvRecord("high.example.com", 5222, 10, 100);
        SrvRecord low = new SrvRecord("low.example.com", 5222, 20, 50);
        SrvRecord sameWeight = new SrvRecord("same.example.com", 5222, 10, 50);

        assertTrue(high.compareTo(low) < 0);
        assertTrue(high.compareTo(sameWeight) < 0);
    }

    @Test
    void testSaslMechanismFactoryBestMechanism() {
        SaslMechanismFactory factory = SaslMechanismFactory.getInstance();

        Optional<SaslMechanism> plain = factory.createBestMechanism(
                List.of("PLAIN"), "user", "pass".toCharArray());
        assertTrue(plain.isPresent());
        assertEquals("PLAIN", plain.get().getMechanismName());

        Optional<SaslMechanism> scram256 = factory.createBestMechanism(
                List.of("PLAIN", "SCRAM-SHA-256"), "user", "pass".toCharArray());
        assertTrue(scram256.isPresent());
        assertEquals("SCRAM-SHA-256", scram256.get().getMechanismName());

        Optional<SaslMechanism> scram512 = factory.createBestMechanism(
                List.of("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512"), "user", "pass".toCharArray());
        assertTrue(scram512.isPresent());
        assertEquals("SCRAM-SHA-512", scram512.get().getMechanismName());
    }

    @Test
    void testPlainSaslMechanism() throws Exception {
        PlainSaslMechanism plain = new PlainSaslMechanism("testuser", "testpass".toCharArray());
        assertEquals("PLAIN", plain.getMechanismName());
        assertTrue(plain.hasInitialResponse());

        byte[] response = plain.processChallenge(null);
        assertNotNull(response);
        assertTrue(response.length > 0);
    }

    @Test
    void testStanzaFilter() {
        StanzaFilter iqFilter = stanza -> stanza instanceof Iq;

        Iq iq = new Iq.Builder("get")
                .id("test-id")
                .build();

        assertTrue(iqFilter.accept(iq));
    }

    @Test
    void testIqStanza() {
        Iq iq = new Iq.Builder("get")
                .id("test-123")
                .from("client@example.com")
                .to("server@example.com")
                .build();

        assertEquals("test-123", iq.getId());
        assertEquals(Iq.Type.GET, iq.getType());
        assertEquals("server@example.com", iq.getTo());
        assertEquals("client@example.com", iq.getFrom());

        assertEquals("test-123", iq.getId());
        assertEquals("server@example.com", iq.getTo());
        assertEquals("client@example.com", iq.getFrom());
    }

    @Test
    void testAsyncStanzaCollector() {
        StanzaFilter filter = stanza -> "match-id".equals(stanza.getId());
        AsyncStanzaCollector collector = new AsyncStanzaCollector(filter);

        Iq noMatch = new Iq.Builder("get")
                .id("other-id")
                .build();
        assertFalse(collector.processStanza(noMatch));

        Iq match = new Iq.Builder("get")
                .id("match-id")
                .build();
        assertTrue(collector.processStanza(match));

        assertTrue(collector.getFuture().isDone());
    }

    @Test
    void testSecurityModeEnum() {
        assertEquals(3, XmppClientConfig.SecurityMode.values().length);
        assertNotNull(XmppClientConfig.SecurityMode.REQUIRED);
        assertNotNull(XmppClientConfig.SecurityMode.IF_POSSIBLE);
        assertNotNull(XmppClientConfig.SecurityMode.DISABLED);
    }

    @Test
    void testIqTypeEnum() {
        assertEquals(4, Iq.Type.values().length);
        assertNotNull(Iq.Type.GET);
        assertNotNull(Iq.Type.SET);
        assertNotNull(Iq.Type.RESULT);
        assertNotNull(Iq.Type.ERROR);

        assertEquals(Iq.Type.GET, Iq.Type.fromString("get"));
        assertEquals(Iq.Type.SET, Iq.Type.fromString("SET"));

        // fromString 对无效输入抛出异常
        assertThrows(IllegalArgumentException.class, () -> Iq.Type.fromString("invalid"));
        assertThrows(IllegalArgumentException.class, () -> Iq.Type.fromString(null));

        // fromStringOrDefault 对无效输入返回默认值
        assertEquals(Iq.Type.GET, Iq.Type.fromStringOrDefault("get", Iq.Type.ERROR));
        assertEquals(Iq.Type.ERROR, Iq.Type.fromStringOrDefault(null, Iq.Type.ERROR));
        assertEquals(Iq.Type.ERROR, Iq.Type.fromStringOrDefault("invalid", Iq.Type.ERROR));
    }

    @Test
    void testXmppExceptionCreation() {
        XmppException e1 = new XmppException("Test message");
        assertEquals("Test message", e1.getMessage());

        RuntimeException cause = new RuntimeException("Root cause");
        XmppException e2 = new XmppException("Wrapper", cause);
        assertEquals("Wrapper", e2.getMessage());
        assertEquals(cause, e2.getCause());
    }

    @Test
    void testIqChildElement() {
        Iq iq = new Iq.Builder("get")
                .id("ping-1")
                .from("client@example.com")
                .to("server@example.com")
                .childElement(new Ping())
                .build();

        assertNotNull(iq.getChildElement());
        assertTrue(iq.getChildElement() instanceof Ping);
    }

    @Test
    void testPingExtension() {
        Ping ping = new Ping();
        assertEquals("ping", Ping.ELEMENT);
        assertEquals("urn:xmpp:ping", Ping.NAMESPACE);
    }

    @Test
    void testSrvRecordFields() {
        SrvRecord record = new SrvRecord("xmpp.example.com", 5222, 10, 20);
        assertEquals("xmpp.example.com", record.target());
        assertEquals(5222, record.port());
        assertEquals(10, record.priority());
        assertEquals(20, record.weight());
    }
}
