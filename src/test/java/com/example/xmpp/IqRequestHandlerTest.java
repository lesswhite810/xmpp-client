package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.handler.IqRequestHandler;
import com.example.xmpp.handler.AbstractIqRequestHandler;
import com.example.xmpp.handler.PingIqRequestHandler;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.extension.Ping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;

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
    @DisplayName("未知命名空间的 IQ 请求应返回 service-unavailable 错误")
    void testUnsupportedIqRequestReturnsServiceUnavailableForUnknownNamespace() {
        Iq unsupportedRequest = new Iq.Builder(Iq.Type.GET)
                .id("unsupported-1")
                .from("server@example.com")
                .to("user@example.com/resource")
                .childElement(GenericExtensionElement.builder("query", "urn:example:unsupported").build())
                .build();

        boolean handled = connection.handleIqRequest(unsupportedRequest);

        assertTrue(handled);
        XmppStanza sentStanza = connection.getLastSentStanza();
        assertInstanceOf(Iq.class, sentStanza);
        Iq errorResponse = (Iq) sentStanza;
        assertEquals(Iq.Type.ERROR, errorResponse.getType());
        assertEquals("unsupported-1", errorResponse.getId());
        assertNotNull(errorResponse.getError());
        assertEquals(XmppError.Condition.SERVICE_UNAVAILABLE, errorResponse.getError().getCondition());
        assertNull(errorResponse.getError().getText());
    }

    @Test
    @DisplayName("已知命名空间但不支持的 IQ 请求应返回 feature-not-implemented 错误")
    void testUnsupportedIqRequestReturnsFeatureNotImplementedForKnownNamespace() {
        connection.registerIqRequestHandler(new DummyKnownNamespaceHandler());

        Iq unsupportedRequest = new Iq.Builder(Iq.Type.GET)
                .id("unsupported-2")
                .from("server@example.com")
                .to("user@example.com/resource")
                .childElement(GenericExtensionElement.builder("other", Ping.NAMESPACE).build())
                .build();

        boolean handled = connection.handleIqRequest(unsupportedRequest);

        assertTrue(handled);
        XmppStanza sentStanza = connection.getLastSentStanza();
        assertInstanceOf(Iq.class, sentStanza);
        Iq errorResponse = (Iq) sentStanza;
        assertEquals(Iq.Type.ERROR, errorResponse.getType());
        assertNotNull(errorResponse.getError());
        assertEquals(XmppError.Condition.FEATURE_NOT_IMPLEMENTED, errorResponse.getError().getCondition());
        assertNull(errorResponse.getError().getText());
    }

    @Test
    @DisplayName("处理 null 处理器应抛出异常")
    void testNullHandlerThrowsException() {
        assertThrows(NullPointerException.class, () -> connection.registerIqRequestHandler(null));
        assertThrows(NullPointerException.class, () -> connection.unregisterIqRequestHandler(null));
    }

    @Test
    @DisplayName("非 GET/SET 类型的 IQ 请求不应被处理")
    void testHandleIqRequestReturnsFalseForNonRequestType() {
        Iq iq = new Iq.Builder(Iq.Type.RESULT)
                .id("result-1")
                .childElement(Ping.INSTANCE)
                .build();

        assertFalse(connection.handleIqRequest(iq));
        assertNull(connection.getLastSentStanza());
    }

    @Test
    @DisplayName("没有子元素的 IQ 请求不应被处理")
    void testHandleIqRequestReturnsFalseWhenChildElementMissing() {
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("get-1")
                .build();

        assertFalse(connection.handleIqRequest(iq));
        assertNull(connection.getLastSentStanza());
    }

    @Test
    @DisplayName("子元素缺少命名空间时不应分派给处理器")
    void testHandleIqRequestReturnsFalseWhenChildElementNamespaceMissing() {
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("get-2")
                .childElement(new NullNamespaceElement())
                .build();

        assertFalse(connection.handleIqRequest(iq));
        assertNull(connection.getLastSentStanza());
    }

    @Test
    @DisplayName("处理器元素名为空时注册应抛出异常")
    void testRegisterHandlerWithNullElementThrowsException() {
        IqRequestHandler handler = new NullElementHandler();

        assertThrows(IllegalArgumentException.class, () -> connection.registerIqRequestHandler(handler));
    }

    @Test
    @DisplayName("处理器 IQ 类型为空时注册应抛出异常")
    void testRegisterHandlerWithNullIqTypeThrowsException() {
        IqRequestHandler handler = new NullTypeHandler();

        assertThrows(IllegalArgumentException.class, () -> connection.registerIqRequestHandler(handler));
    }

    @Test
    @DisplayName("处理器注册方法应禁止子类覆盖")
    void testHandlerRegistrationMethodsAreFinal() throws NoSuchMethodException {
        assertTrue(Modifier.isFinal(AbstractXmppConnection.class
                .getMethod("registerIqRequestHandler", IqRequestHandler.class)
                .getModifiers()));
        assertTrue(Modifier.isFinal(AbstractXmppConnection.class
                .getMethod("unregisterIqRequestHandler", IqRequestHandler.class)
                .getModifiers()));
    }

    /**
     * 测试用 XMPP 连接。
     */
    private static class TestXmppConnection extends AbstractXmppConnection {

        private final XmppClientConfig config;
        private XmppStanza lastSentStanza;

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
        public void sendStanza(XmppStanza stanza) {
            lastSentStanza = stanza;
        }

        @Override
        protected CompletableFuture<Void> dispatchStanza(XmppStanza stanza) {
            lastSentStanza = stanza;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public XmppClientConfig getConfig() {
            return config;
        }

        XmppStanza getLastSentStanza() {
            return lastSentStanza;
        }
    }

    private static class DummyKnownNamespaceHandler extends AbstractIqRequestHandler {

        DummyKnownNamespaceHandler() {
            super("known", Ping.NAMESPACE, Iq.Type.GET);
        }

        @Override
        public Iq handleIqRequest(Iq iqRequest) {
            return Iq.createResultResponse(iqRequest, null);
        }
    }

    private static class NullElementHandler extends AbstractIqRequestHandler {

        NullElementHandler() {
            super(null, Ping.NAMESPACE, Iq.Type.GET);
        }

        @Override
        public Iq handleIqRequest(Iq iqRequest) {
            return null;
        }
    }

    private static class NullTypeHandler extends AbstractIqRequestHandler {

        NullTypeHandler() {
            super("null-type", Ping.NAMESPACE, null);
        }

        @Override
        public Iq handleIqRequest(Iq iqRequest) {
            return null;
        }
    }

    private static class NullNamespaceElement implements ExtensionElement {

        @Override
        public String getElementName() {
            return "no-namespace";
        }

        @Override
        public String getNamespace() {
            return null;
        }

        @Override
        public String toXml() {
            return "<no-namespace/>";
        }
    }
}
