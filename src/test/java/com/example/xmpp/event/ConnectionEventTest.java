package com.example.xmpp.event;

import com.example.xmpp.XmppConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * ConnectionEvent 测试。
 *
 * @since 2026-03-20
 */
class ConnectionEventTest {

    @Test
    @DisplayName("无错误构造器应默认 error 为 null")
    void testConstructorWithoutError() {
        XmppConnection connection = mock(XmppConnection.class);

        ConnectionEvent event = new ConnectionEvent(connection, ConnectionEventType.CONNECTED);

        assertEquals(connection, event.connection());
        assertEquals(ConnectionEventType.CONNECTED, event.eventType());
        assertNull(event.error());
        assertTrue(event.toString().contains("type=CONNECTED"));
    }

    @Test
    @DisplayName("toString 应仅输出错误类型")
    void testToStringWithError() {
        XmppConnection connection = mock(XmppConnection.class);
        IllegalStateException error = new IllegalStateException("secret");

        ConnectionEvent event = new ConnectionEvent(connection, ConnectionEventType.ERROR, error);

        assertEquals(error, event.error());
        assertTrue(event.toString().contains("errorType=IllegalStateException"));
        assertTrue(!event.toString().contains("secret"));
    }
}
