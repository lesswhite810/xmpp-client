package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.util.XmlParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XMPP 服务器集成测试
 *
 * 测试与主流 XMPP 服务器的兼容性，包括连接、认证、IQ、Message、Presence 等功能。
 */
public class XmppServerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(XmppServerIntegrationTest.class);

    private static final String XMPP_DOMAIN = "example.com";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "testpass";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;

    /**
     * 测试基本连接和认证功能
     */
    @Disabled("需要实际的 XMPP 服务器")
    @Test
    public void testConnectionAndAuthentication() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .username(USERNAME)
                .password(PASSWORD.toCharArray())
                .host(HOST)
                .port(PORT)
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .sendPresence(true)
                .build();

        XmppTcpConnection connection = new XmppTcpConnection(config);

        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch closeLatch = new CountDownLatch(1);

        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.ConnectedEvent e ->
                    log.info("=== CONNECTED ===");
                case ConnectionEvent.AuthenticatedEvent e -> {
                    log.info("=== AUTHENTICATED === resumed={}", e.resumed());
                    authLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedEvent e -> {
                    log.info("=== CONNECTION CLOSED ===");
                    closeLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedOnErrorEvent e -> {
                    log.error("=== CONNECTION ERROR ===", e.error());
                    closeLatch.countDown();
                }
            }
        });

        try {
            connection.connect();
            boolean authenticated = authLatch.await(10, TimeUnit.SECONDS);
            assertTrue(authenticated, "Authentication should complete within 10 seconds");
            assertTrue(connection.isConnected(), "Connection should be connected");
            assertTrue(connection.isAuthenticated(), "Connection should be authenticated");
        } finally {
            connection.disconnect();
            closeLatch.await(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 测试 Ping 请求和响应（XEP-0199）
     */
    @Disabled("需要实际的 XMPP 服务器")
    @Test
    public void testPingRequestResponse() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .username(USERNAME)
                .password(PASSWORD.toCharArray())
                .host(HOST)
                .port(PORT)
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .sendPresence(true)
                .build();

        XmppTcpConnection connection = new XmppTcpConnection(config);
        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch pingLatch = new CountDownLatch(1);
        CountDownLatch closeLatch = new CountDownLatch(1);

        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.ConnectedEvent e ->
                    log.info("=== CONNECTED ===");
                case ConnectionEvent.AuthenticatedEvent e -> {
                    log.info("=== AUTHENTICATED ===");
                    authLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedEvent e -> {
                    log.info("=== CONNECTION CLOSED ===");
                    closeLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedOnErrorEvent e -> {
                    log.error("=== CONNECTION ERROR ===", e.error());
                    closeLatch.countDown();
                }
            }
        });

        try {
            connection.connect();
            authLatch.await(10, TimeUnit.SECONDS);
            assertTrue(connection.isAuthenticated(), "Connection should be authenticated");

            String pingId = "ping-" + System.currentTimeMillis();
            Iq pingRequest = PingIq.createPingRequest(pingId, XMPP_DOMAIN);

            log.info("Sending Ping request: {}", toXml(pingRequest));

            connection.sendIqPacketAsync(pingRequest)
                    .thenAccept(response -> {
                        log.info("Received Ping response: ID={}, Type={}", response.getId(),
                                response.getClass().getSimpleName());
                        assertTrue(response instanceof Iq, "Response should be an IQ");
                        Iq pingResponse = (Iq) response;
                        assertEquals(Iq.Type.RESULT, pingResponse.getType(), "Response type should be 'result'");
                        assertEquals(pingId, pingResponse.getId(), "Response ID should match request ID");
                        pingLatch.countDown();
                    })
                    .exceptionally(ex -> {
                        log.error("Error sending Ping request: {}", ex.getMessage());
                        pingLatch.countDown();
                        return null;
                    });

            boolean pingReceived = pingLatch.await(5, TimeUnit.SECONDS);
            assertTrue(pingReceived, "Ping response should be received within 5 seconds");

            Presence presence = new Presence.Builder()
                    .type(Presence.Type.AVAILABLE)
                    .build();
            connection.sendStanza(presence);
            log.info("Sent presence stanza: {}", toXml(presence));

        } finally {
            connection.disconnect();
            closeLatch.await(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 测试 IQ 节的解析和序列化
     */
    @Test
    public void testIqParsingAndSerialization() {
        String iqXml = "<iq type=\"result\" id=\"bind-234\" to=\"user@example.com/resource\">" +
                "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">" +
                "<jid>user@example.com/resource</jid>" +
                "</bind>" +
                "</iq>";

        Iq iq = XmlParser.parseIq(iqXml);
        assertNotNull(iq, "IQ should be parsed successfully");
        assertEquals(Iq.Type.RESULT, iq.getType(), "IQ type should be 'result'");
        assertEquals("bind-234", iq.getId(), "IQ ID should match");
        assertEquals("user@example.com/resource", iq.getTo(), "IQ to should match");
        assertNotNull(iq.getChildElement(), "IQ should have a child element");
        assertTrue(iq.getChildElement() instanceof Bind, "Child element should be Bind");

        Iq originalIq = new Iq.Builder(Iq.Type.GET)
                .id("test-123")
                .from("user@example.com")
                .to("server.example.com")
                .childElement(new com.example.xmpp.protocol.model.extension.Ping())
                .build();

        String serializedXml = toXml(originalIq);
        assertNotNull(serializedXml, "IQ should be serialized successfully");
        assertTrue(serializedXml.contains("<iq"), "Serialized XML should contain iq element");
        assertTrue(serializedXml.contains("type=\"get\""), "Serialized XML should have correct type");
        assertTrue(serializedXml.contains("id=\"test-123\""), "Serialized XML should have correct ID");
        assertTrue(serializedXml.contains("<ping"), "Serialized XML should contain ping element");
    }

    /**
     * 测试 Message 节的解析和序列化
     */
    @Test
    public void testMessageParsingAndSerialization() {
        String messageXml = "<message type=\"chat\" id=\"msg-567\" from=\"alice@example.com/desktop\" to=\"bob@example.com/mobile\">"
                +
                "<subject>Test Subject</subject>" +
                "<body>Hello Bob! This is a test message.</body>" +
                "</message>";

        Message message = XmlParser.parseMessage(messageXml);
        assertNotNull(message, "Message should be parsed successfully");
        assertEquals(Message.Type.CHAT, message.getType(), "Message type should be 'chat'");
        assertEquals("msg-567", message.getId(), "Message ID should match");
        assertEquals("alice@example.com/desktop", message.getFrom(), "Message from should match");
        assertEquals("bob@example.com/mobile", message.getTo(), "Message to should match");
        assertEquals("Test Subject", message.getSubject(), "Message subject should match");
        assertEquals("Hello Bob! This is a test message.", message.getBody(), "Message body should match");

        Message originalMessage = new Message.Builder()
                .type(Message.Type.GROUPCHAT)
                .id("msg-789")
                .from("room@conference.example.com/alice")
                .to("room@conference.example.com")
                .subject("Meeting Agenda")
                .body("Let's discuss the project timeline.")
                .build();

        String serializedXml = toXml(originalMessage);
        assertNotNull(serializedXml, "Message should be serialized successfully");
        assertTrue(serializedXml.contains("<message"), "Serialized XML should contain message element");
        assertTrue(serializedXml.contains("type=\"groupchat\""), "Serialized XML should have correct type");
        assertTrue(serializedXml.contains("id=\"msg-789\""), "Serialized XML should have correct ID");
        assertTrue(serializedXml.contains("<subject>Meeting Agenda</subject>"),
                "Serialized XML should have correct subject");
        assertTrue(serializedXml.contains("<body>Let&apos;s discuss the project timeline.</body>"),
                "Serialized XML should have correct body (with escaped apostrophe)");
    }

    /**
     * 测试 Presence 节的解析和序列化
     */
    @Test
    public void testPresenceParsingAndSerialization() {
        String presenceXml = "<presence type=\"available\" id=\"pres-123\" from=\"user@example.com/laptop\" to=\"friend@example.com\">"
                +
                "<show>chat</show>" +
                "<status>Online and ready to chat</status>" +
                "<priority>5</priority>" +
                "</presence>";

        Presence presence = XmlParser.parsePresence(presenceXml);
        assertNotNull(presence, "Presence should be parsed successfully");
        assertEquals(Presence.Type.AVAILABLE, presence.getType(), "Presence type should be 'available'");
        assertEquals("pres-123", presence.getId(), "Presence ID should match");
        assertEquals("user@example.com/laptop", presence.getFrom(), "Presence from should match");
        assertEquals("friend@example.com", presence.getTo(), "Presence to should match");
        assertEquals("chat", presence.getShow(), "Presence show should match");
        assertEquals("Online and ready to chat", presence.getStatus(), "Presence status should match");
        assertEquals(Integer.valueOf(5), presence.getPriority(), "Presence priority should match");

        Presence originalPresence = new Presence.Builder()
                .type(Presence.Type.UNAVAILABLE)
                .id("pres-456")
                .from("user@example.com/desktop")
                .to("contact@example.com")
                .status("Going offline")
                .build();

        String serializedXml = toXml(originalPresence);
        assertNotNull(serializedXml, "Presence should be serialized successfully");
        assertTrue(serializedXml.contains("<presence"), "Serialized XML should contain presence element");
        assertTrue(serializedXml.contains("type=\"unavailable\""), "Serialized XML should have correct type");
        assertTrue(serializedXml.contains("id=\"pres-456\""), "Serialized XML should have correct ID");
        assertTrue(serializedXml.contains("<status>Going offline</status>"),
                "Serialized XML should have correct status");
    }

    /**
     * 测试通用 Stanza 解析
     */
    @Test
    public void testGenericStanzaParsing() {
        String iqXml = "<iq type=\"get\" id=\"test-123\">" +
                "<ping xmlns=\"urn:xmpp:ping\"/>" +
                "</iq>";

        Optional<Object> stanza = XmlParser.parseStanza(iqXml);
        assertTrue(stanza.isPresent(), "Stanza should be parsed successfully");
        assertTrue(stanza.get() instanceof Iq, "Parsed stanza should be an IQ");

        String messageXml = "<message type=\"chat\" id=\"msg-456\">" +
                "<body>Test message</body>" +
                "</message>";

        stanza = XmlParser.parseStanza(messageXml);
        assertTrue(stanza.isPresent(), "Stanza should be parsed successfully");
        assertTrue(stanza.get() instanceof Message, "Parsed stanza should be a Message");

        String presenceXml = "<presence type=\"available\"/>";

        stanza = XmlParser.parseStanza(presenceXml);
        assertTrue(stanza.isPresent(), "Stanza should be parsed successfully");
        assertTrue(stanza.get() instanceof Presence, "Parsed stanza should be a Presence");
    }

    private String toXml(com.example.xmpp.protocol.model.Stanza stanza) {
        return stanza.toXml();
    }
}
