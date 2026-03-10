package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IQ 协议错误传播测试。
 */
class XmppIqErrorPropagationTest {

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
    @DisplayName("sendIqPacketAsync 收到 iq error 时应异常完成")
    void testSendIqPacketAsyncCompletesExceptionallyOnIqError() {
        Iq request = new Iq.Builder(Iq.Type.GET)
                .id("iq-error-1")
                .to("server@example.com")
                .build();

        CompletableFuture<XmppStanza> future = connection.sendIqPacketAsync(request);

        Iq errorResponse = Iq.createErrorResponse(
                request,
                new XmppError.Builder(XmppError.Condition.service_unavailable)
                        .text("service unavailable")
                        .build()
        );
        connection.notifyStanzaReceived(errorResponse);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        XmppStanzaErrorException cause = assertInstanceOf(XmppStanzaErrorException.class, exception.getCause());
        assertEquals("iq-error-1", cause.getErrorIq().getId());
        assertEquals(Iq.Type.ERROR, cause.getErrorIq().getType());
        assertNotNull(cause.getXmppError());
        assertEquals(XmppError.Condition.service_unavailable, cause.getXmppError().getCondition());
    }

    private static final class TestXmppConnection extends AbstractXmppConnection {

        private final XmppClientConfig config;

        private TestXmppConnection(XmppClientConfig config) {
            this.config = config;
        }

        @Override
        public void connect() {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public void sendStanza(XmppStanza stanza) {
        }

        @Override
        public XmppClientConfig getConfig() {
            return config;
        }

        @Override
        public void resetHandlerState() {
        }
    }
}