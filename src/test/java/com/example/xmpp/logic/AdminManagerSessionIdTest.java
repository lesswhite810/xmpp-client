package com.example.xmpp.logic;

import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 extractSessionId 方法是否能正确从 GenericExtensionElement 中提取 sessionid。
 */
@Slf4j
class AdminManagerSessionIdTest {

    @Test
    void testExtractSessionIdFromGenericExtensionElement() {
        // 模拟服务器返回的 command 元素
        GenericExtensionElement commandElement = GenericExtensionElement.builder("command", "http://jabber.org/protocol/commands")
                .addAttribute("sessionid", "XZdTcQXoGcRG5GI")
                .addAttribute("node", "http://jabber.org/protocol/admin#add-user")
                .addAttribute("status", "executing")
                .build();

        // 创建 IQ 娡拟响应
        Iq responseIq = new Iq.Builder(Iq.Type.RESULT)
                .id("add-execute-1772898465140")
                .from("lesswhite")
                .to("admin@lesswhite/xmpp")
                .childElement(commandElement)
                .build();

        // 验证 childElement 是 GenericExtensionElement
        assertTrue(responseIq.getChildElement() instanceof GenericExtensionElement);

        // 验证可以获取 sessionid
        GenericExtensionElement child = (GenericExtensionElement) responseIq.getChildElement();
        String sessionId = child.getAttributeValue("sessionid");

        assertEquals("XZdTcQXoGcRG5GI", sessionId);
        log.info("Successfully extracted sessionId: {}", sessionId);
    }

    @Test
    void testExtractSessionIdFromXml() {
        // 模拟一个没有正确设置 childElement 的 IQ（使用 toXml 回退）
        GenericExtensionElement commandElement = GenericExtensionElement.builder("command", "http://jabber.org/protocol/commands")
                .addAttribute("sessionid", "test-session-123")
                .build();

        Iq responseIq = new Iq.Builder(Iq.Type.RESULT)
                .id("test-1")
                .childElement(commandElement)
                .build();

        // 验证 toXml 包含 sessionid
        String xml = responseIq.toXml();
        log.info("Generated XML: {}", xml);
        assertTrue(xml.contains("sessionid=\"test-session-123\""));
    }
}
