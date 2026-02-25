package com.example.xmpp;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ping 报文格式测试（XEP-0199）
 */
class PingFormatTest {

    private static final Logger log = LoggerFactory.getLogger(PingFormatTest.class);

    @Test
    void testPingIqSerialization() {
        // 使用新的 PingIq 类（参考 Smack 设计）
        String id = "ping_123456";
        String to = "example.com";
        Iq pingIq = PingIq.createPingRequest(id, to);

        // 使用 toXml 方法序列化
        String xml = pingIq.toXml();

        log.debug("Generated Ping IQ XML:\n{}", xml);

        // 验证基本结构
        assertTrue(xml.contains("<iq"), "Should contain iq element");
        assertTrue(xml.contains("type=\"get\""), "Should have type='get'");
        assertTrue(xml.contains("id=\"" + id + "\""), "Should have id attribute");
        assertTrue(xml.contains("to=\"" + to + "\""), "Should have to attribute");

        // 验证 Ping 扩展（XEP-0199 要求）
        // 正确的格式: <iq type='get' ...><ping xmlns='urn:xmpp:ping'/></iq>
        assertTrue(xml.contains("<ping"), "Should contain ping element");
        assertTrue(xml.contains("xmlns=\"urn:xmpp:ping\""), "Should have urn:xmpp:ping namespace");

        // 验证没有错误的包装元素
        assertFalse(xml.contains("<XmppPackets.Ping"), "Should not have Java class name as element");
        assertFalse(xml.contains("<extensions"), "Should not have extensions wrapper");
    }

    @Test
    void testPingResponseFormat() {
        // 创建 Ping 请求然后生成响应
        String id = "ping_123";
        String server = "server@example.com";
        String client = "client@example.com";
        Iq pingRequest = PingIq.createPingRequest(id, server, client);
        Iq pongIq = PingIq.createPingResponse(pingRequest);

        String xml = pongIq.toXml();

        log.debug("Generated Pong IQ XML:\n{}", xml);

        // 验证响应格式（空的 result IQ）
        assertTrue(xml.contains("type=\"result\""), "Should have type='result'");
        assertTrue(xml.contains("id=\"ping_123\""), "Should have same id as request");
        assertTrue(xml.contains("to=\"client@example.com\""), "Should have to attribute set to client");
        assertTrue(xml.contains("from=\"server@example.com\""), "Should have from attribute set to server");
        assertFalse(xml.contains("<ping"), "Pong should not contain ping element");
    }

    @Test
    void testGenericIqToXml() {
        // 测试普通 IQ 的 toXml
        Iq iq = new Iq.Builder("set")
                .id("test-001")
                .from("client@example.com")
                .to("server@example.com")
                .build();

        String xml = iq.toXml();
        log.debug("Generic IQ XML:\n{}", xml);

        assertTrue(xml.contains("type=\"set\""));
        assertTrue(xml.contains("id=\"test-001\""));
        assertTrue(xml.contains("to=\"server@example.com\""));
        assertTrue(xml.contains("from=\"client@example.com\""));
    }
}
