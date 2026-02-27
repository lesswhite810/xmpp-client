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
        assertNotNull(Iq.Type.GET);
        assertNotNull(Iq.Type.SET);
        assertNotNull(Iq.Type.RESULT);
        assertNotNull(Iq.Type.ERROR);
    }

    @Test
    @DisplayName("Message.Type 应包含所有类型")
    void testMessageTypes() {
        assertTrue(Message.Type.values().length >= 4);
        assertNotNull(Message.Type.CHAT);
        assertNotNull(Message.Type.GROUPCHAT);
        assertNotNull(Message.Type.NORMAL);
        assertNotNull(Message.Type.ERROR);
    }

    @Test
    @DisplayName("Presence.Type 应包含所有类型")
    void testPresenceTypes() {
        assertTrue(Presence.Type.values().length >= 4);
        assertNotNull(Presence.Type.AVAILABLE);
        assertNotNull(Presence.Type.UNAVAILABLE);
        assertNotNull(Presence.Type.SUBSCRIBE);
        assertNotNull(Presence.Type.ERROR);
    }

    @Test
    @DisplayName("Iq.Type.valueOf 应正确解析")
    void testIqTypeValueOf() {
        assertEquals(Iq.Type.GET, Iq.Type.valueOf("GET"));
        assertEquals(Iq.Type.SET, Iq.Type.valueOf("SET"));
    }
}
