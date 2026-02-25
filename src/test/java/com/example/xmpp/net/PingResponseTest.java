package com.example.xmpp.net;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.util.XmlStringBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 PING 请求的自动响应。
 */
class PingResponseTest {

    private static final Logger log = LoggerFactory.getLogger(PingResponseTest.class);

    /**
     * 测试 PING 响应的构建逻辑。
     */
    @Test
    void testPingResponseBuild() {
        // 模拟服务器发来的 PING 请求
        Iq pingRequest = new Iq.Builder(Iq.Type.get)
                .id("server-ping-123")
                .from("example.com")
                .to("user@example.com/resource")
                .childElement(Ping.INSTANCE)
                .build();

        // 构建响应（模拟 handlePingRequest 的逻辑）
        Iq pongResponse = new Iq.Builder(Iq.Type.result)
                .id(pingRequest.getId())
                .to(pingRequest.getFrom())
                .build();

        // 序列化
        String result = pongResponse.toXml();

        log.debug("PING request:\n{}", toXml(pingRequest));
        log.debug("PONG response:\n{}", result);

        // 验证
        assertTrue(result.contains("type=\"result\""), "响应类型应该是 result");
        assertTrue(result.contains("id=\"server-ping-123\""), "响应 ID 应该匹配");
        assertTrue(result.contains("to=\"example.com\""), "响应 to 应该是请求的 from");
        assertFalse(result.contains("<ping"), "result IQ 不应该有子元素");
    }

    /**
     * 测试 PING IQ 的识别。
     */
    @Test
    void testPingIqRecognition() {
        // 创建 PING 请求
        Iq pingRequest = new Iq.Builder(Iq.Type.get)
                .id("ping-1")
                .from("server.com")
                .childElement(Ping.INSTANCE)
                .build();

        // 验证识别逻辑（这是 XmppNettyHandler 中使用的判断条件）
        assertTrue(pingRequest.isGet(), "PING 应该是 get 类型");
        assertTrue(pingRequest.getChildElement() instanceof Ping,
                "子元素应该是 Ping");

        // 非 PING 请求
        Iq normalIq = new Iq.Builder(Iq.Type.get)
                .id("iq-1")
                .childElement(com.example.xmpp.protocol.model.extension.Bind.builder().resource("test").build())
                .build();

        assertFalse(normalIq.getChildElement() instanceof Ping,
                "Bind 不是 Ping");
    }

    /**
     * 测试解码器能正确解析 PING IQ（需要流上下文）。
     */
    @Test
    void testPingIqDecoding() {
        XmppStreamDecoder decoder = new XmppStreamDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // 先发送流头建立上下文
        String streamHeader = "<stream:stream xmlns='jabber:client' " +
                "xmlns:stream='http://etherx.jabber.org/streams'>";
        ByteBuf headerBuf = Unpooled.copiedBuffer(streamHeader, StandardCharsets.UTF_8);
        channel.writeInbound(headerBuf);

        // 读取流头（可以忽略）
        channel.readInbound();

        // 发送 PING IQ
        String pingXml = "<iq type='get' id='ping-123' from='server.com'>" +
                "<ping xmlns='urn:xmpp:ping'/></iq>";

        ByteBuf buf = Unpooled.copiedBuffer(pingXml, StandardCharsets.UTF_8);
        channel.writeInbound(buf);

        // 读取解码后的对象
        Object msg = channel.readInbound();

        assertNotNull(msg, "应该解码出消息");
        assertTrue(msg instanceof Iq, "应该是 Iq 类型");

        Iq iq = (Iq) msg;
        assertEquals(Iq.Type.get, iq.getType());
        assertEquals("ping-123", iq.getId());
        assertEquals("server.com", iq.getFrom());
        assertTrue(iq.getChildElement() instanceof Ping, "子元素应该是 Ping");

        log.debug("Decoding successful: {}", toXml(iq));

        channel.finish();
    }

    private String toXml(Iq iq) {
        return iq.toXml();
    }
}
