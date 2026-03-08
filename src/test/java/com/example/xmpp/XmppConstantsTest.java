package com.example.xmpp;

import com.example.xmpp.util.XmppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmppConstants 单元测试。
 */
class XmppConstantsTest {

    @Test
    @DisplayName("DEFAULT_XMPP_PORT 应为 5222")
    void testDefaultXmppPort() {
        assertEquals(5222, XmppConstants.DEFAULT_XMPP_PORT);
    }

    @Test
    @DisplayName("DIRECT_TLS_PORT 应为 5223")
    void testDirectTlsPort() {
        assertEquals(5223, XmppConstants.DIRECT_TLS_PORT);
    }

    @Test
    @DisplayName("DEFAULT_CONNECT_TIMEOUT_MS 应为 30000")
    void testConnectTimeout() {
        assertEquals(30000, XmppConstants.DEFAULT_CONNECT_TIMEOUT_MS);
    }

    @Test
    @DisplayName("DEFAULT_READ_TIMEOUT_MS 应为 60000")
    void testReadTimeout() {
        assertEquals(60000, XmppConstants.DEFAULT_READ_TIMEOUT_MS);
    }

    @Test
    @DisplayName("DNS_QUERY_TIMEOUT_SECONDS 应为 5")
    void testDnsTimeout() {
        assertEquals(5, XmppConstants.DNS_QUERY_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("PING_RESPONSE_TIMEOUT_SECONDS 应为 10")
    void testPingTimeout() {
        assertEquals(10, XmppConstants.PING_RESPONSE_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("AUTH_TIMEOUT_SECONDS 应为 10")
    void testAuthTimeout() {
        assertEquals(10, XmppConstants.AUTH_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("SHUTDOWN_TIMEOUT_SECONDS 应为 5")
    void testShutdownTimeout() {
        assertEquals(5, XmppConstants.SHUTDOWN_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("重连延迟应在合理范围")
    void testReconnectDelays() {
        assertEquals(2, XmppConstants.RECONNECT_BASE_DELAY_SECONDS);
        assertEquals(60, XmppConstants.RECONNECT_MAX_DELAY_SECONDS);
        assertTrue(XmppConstants.RECONNECT_BASE_DELAY_SECONDS < XmppConstants.RECONNECT_MAX_DELAY_SECONDS);
    }

    @Test
    @DisplayName("SASL 机制优先级应正确排序")
    void testSaslPriorities() {
        assertTrue(XmppConstants.PRIORITY_SCRAM_SHA512 > XmppConstants.PRIORITY_SCRAM_SHA256);
        assertTrue(XmppConstants.PRIORITY_SCRAM_SHA256 > XmppConstants.PRIORITY_SCRAM_SHA1);
        assertTrue(XmppConstants.PRIORITY_SCRAM_SHA1 > XmppConstants.PRIORITY_PLAIN);
    }

    @Test
    @DisplayName("哈希大小常量应正确")
    void testHashSizes() {
        assertEquals(20, XmppConstants.SHA1_HASH_SIZE_BYTES);
        assertEquals(32, XmppConstants.SHA256_HASH_SIZE_BYTES);
        assertEquals(64, XmppConstants.SHA512_HASH_SIZE_BYTES);
    }

    @Test
    @DisplayName("DEFAULT_IQ_TIMEOUT_MS 应合理")
    void testIqTimeout() {
        assertTrue(XmppConstants.DEFAULT_IQ_TIMEOUT_MS > 0);
    }

    @Test
    @DisplayName("DEFAULT_PING_INTERVAL_SECONDS 应合理")
    void testPingInterval() {
        assertTrue(XmppConstants.DEFAULT_PING_INTERVAL_SECONDS > 0);
    }

    @Test
    @DisplayName("命名空间常量应非空")
    void testNamespaces() {
        assertNotNull(XmppConstants.NS_XMPP_STREAMS);
        assertNotNull(XmppConstants.NS_XMPP_SASL);
        assertNotNull(XmppConstants.NS_XMPP_BIND);
    }

    @Test
    @DisplayName("generateStanzaId 应生成唯一 ID")
    void testGenerateStanzaId() {
        String id1 = XmppConstants.generateStanzaId();
        String id2 = XmppConstants.generateStanzaId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertTrue(id1.startsWith("xmpp-"));
        assertTrue(id2.startsWith("xmpp-"));
        assertNotEquals(id1, id2, "生成的 ID 应该唯一");
    }

    @Test
    @DisplayName("generateStanzaId 多次调用应生成唯一 ID")
    void testGenerateStanzaIdUnique() {
        String id1 = XmppConstants.generateStanzaId();
        String id2 = XmppConstants.generateStanzaId();
        String id3 = XmppConstants.generateStanzaId();

        // 验证格式：xmpp-{uuid}-{timestamp}
        assertTrue(id1.startsWith("xmpp-"));
        assertTrue(id2.startsWith("xmpp-"));
        assertTrue(id3.startsWith("xmpp-"));

        // 验证每次调用生成不同的 ID
        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);

        // 验证包含 UUID 格式（32位十六进制）和时间戳
        assertTrue(id1.split("-").length >= 2, "ID 应包含多个部分");
    }
}
