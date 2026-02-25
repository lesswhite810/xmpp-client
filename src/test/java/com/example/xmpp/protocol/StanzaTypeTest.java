package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StanzaType 单元测试。
 */
class StanzaTypeTest {

    @Test
    @DisplayName("Iq.Type 应包含所有类型")
    void testIqTypes() {
        assertEquals(4, Iq.Type.values().length);
        assertNotNull(Iq.Type.get);
        assertNotNull(Iq.Type.set);
        assertNotNull(Iq.Type.result);
        assertNotNull(Iq.Type.error);
    }

    @Test
    @DisplayName("Message.Type 应包含所有类型")
    void testMessageTypes() {
        assertTrue(Message.Type.values().length >= 4);
        assertNotNull(Message.Type.chat);
        assertNotNull(Message.Type.groupchat);
        assertNotNull(Message.Type.normal);
        assertNotNull(Message.Type.error);
    }

    @Test
    @DisplayName("Presence.Type 应包含所有类型")
    void testPresenceTypes() {
        assertTrue(Presence.Type.values().length >= 4);
        assertNotNull(Presence.Type.available);
        assertNotNull(Presence.Type.unavailable);
        assertNotNull(Presence.Type.subscribe);
        assertNotNull(Presence.Type.error);
    }

    @Test
    @DisplayName("Iq.Type.valueOf 应正确解析")
    void testIqTypeValueOf() {
        assertEquals(Iq.Type.get, Iq.Type.valueOf("get"));
        assertEquals(Iq.Type.set, Iq.Type.valueOf("set"));
    }
}
