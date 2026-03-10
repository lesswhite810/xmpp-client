package com.example.xmpp.net;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppSaslFailureException;
import com.example.xmpp.exception.XmppStreamErrorException;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.stream.StreamError;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 连接级协议错误传播测试。
 */
class XmppConnectionLifecycleErrorTest {

    @Test
    void testSaslFailureCompletesConnectionFutureExceptionally() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .enabledSaslMechanisms(Set.of("SCRAM-SHA-1"))
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new XmppNettyHandler(config, connection));

        channel.writeInbound(StreamFeatures.builder()
                .mechanisms(List.of("SCRAM-SHA-1"))
                .build());
        channel.writeInbound(SaslFailure.builder()
                .condition("not-authorized")
                .text("Invalid credentials")
                .build());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.getConnectionReadyFuture().join());
        XmppSaslFailureException cause = assertInstanceOf(XmppSaslFailureException.class, exception.getCause());
        assertEquals("not-authorized", cause.getSaslFailure().getCondition());
        assertEquals("Invalid credentials", cause.getSaslFailure().getText());
    }

    @Test
    void testStreamErrorCompletesConnectionFutureExceptionally() {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
        XmppTcpConnection connection = new XmppTcpConnection(config);
        EmbeddedChannel channel = new EmbeddedChannel(new XmppNettyHandler(config, connection));

        channel.writeInbound(StreamError.builder()
                .condition(StreamError.Condition.NOT_AUTHORIZED)
                .text("Authentication required")
                .by("example.com")
                .build());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> connection.getConnectionReadyFuture().join());
        XmppStreamErrorException cause = assertInstanceOf(XmppStreamErrorException.class, exception.getCause());
        assertEquals(StreamError.Condition.NOT_AUTHORIZED, cause.getStreamError().getCondition());
        assertEquals("Authentication required", cause.getStreamError().getText());
    }
}