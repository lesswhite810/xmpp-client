package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.logic.PingIqRequestHandler;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IqRequestHandler 单元测试。
 *
 * @since 2026-02-26
 */
class IqRequestHandlerTest {

    private TestXmppConnection connection;

    @BeforeEach
    void setUp() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .build();
        connection = new TestXmppConnection(config);
    }

    @Test
    @DisplayName("注册和注销 IQ 请求处理器")
    void testRegisterAndUnregisterHandler() {
        PingIqRequestHandler handler = new PingIqRequestHandler();

        // 注册处理器
        assertDoesNotThrow(() -> connection.registerIqRequestHandler(handler));

        // 注销处理器
        assertTrue(connection.unregisterIqRequestHandler(handler));

        // 重复注销返回 false
        assertFalse(connection.unregisterIqRequestHandler(handler));
    }

    @Test
    @DisplayName("重复注册相同键的处理器应抛出异常")
    void testDuplicateRegistrationThrowsException() {
        PingIqRequestHandler handler1 = new PingIqRequestHandler();
        PingIqRequestHandler handler2 = new PingIqRequestHandler();

        connection.registerIqRequestHandler(handler1);

        // 相同键的处理器不能重复注册
        assertThrows(IllegalArgumentException.class, () -> connection.registerIqRequestHandler(handler2));
    }

    @Test
    @DisplayName("PingIqRequestHandler 处理器属性正确")
    void testPingHandlerProperties() {
        PingIqRequestHandler handler = new PingIqRequestHandler();

        assertEquals(Ping.ELEMENT, handler.getElement());
        assertEquals(Ping.NAMESPACE, handler.getNamespace());
        assertEquals(Iq.Type.GET, handler.getIqType());
        assertEquals(IqRequestHandler.Mode.SYNC, handler.getMode());
    }

    @Test
    @DisplayName("PingIqRequestHandler 返回正确的响应")
    void testPingHandlerResponse() {
        PingIqRequestHandler handler = new PingIqRequestHandler();

        Iq pingRequest = new Iq.Builder(Iq.Type.GET)
                .id("ping-123")
                .from("server@example.com")
                .childElement(Ping.INSTANCE)
                .build();

        Iq response = handler.handleIqRequest(pingRequest);

        assertNotNull(response);
        assertEquals(Iq.Type.RESULT, response.getType());
        assertEquals("ping-123", response.getId());
    }

    @Test
    @DisplayName("处理 null 处理器应抛出异常")
    void testNullHandlerThrowsException() {
        assertThrows(NullPointerException.class, () -> connection.registerIqRequestHandler(null));
        assertThrows(NullPointerException.class, () -> connection.unregisterIqRequestHandler(null));
    }

    /**
     * 测试用 XMPP 连接。
     */
    private static class TestXmppConnection extends AbstractXmppConnection {

        private final XmppClientConfig config;

        TestXmppConnection(XmppClientConfig config) {
            this.config = config;
        }

        @Override
        public void connect() {
            // 测试用空实现
        }

        @Override
        public void disconnect() {
            // 测试用空实现
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public boolean isAuthenticated() {
            return false;
        }

        @Override
        public void sendStanza(com.example.xmpp.protocol.model.XmppStanza stanza) {
            // 测试用空实现
        }

        @Override
        public XmppClientConfig getConfig() {
            return config;
        }

        @Override
        public void resetHandlerState() {
            // 测试用空实现
        }
    }
}
