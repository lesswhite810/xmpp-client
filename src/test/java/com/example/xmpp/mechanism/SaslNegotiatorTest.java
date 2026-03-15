package com.example.xmpp.mechanism;

import com.example.xmpp.exception.XmppAuthException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * SaslNegotiator 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SaslNegotiatorTest {

    @Mock
    private SaslMechanism mechanism;

    @Mock
    private ChannelHandlerContext context;

    @Mock
    private ChannelPipeline pipeline;

    @Mock
    private SslHandler sslHandler;

    @Mock
    private Future<Channel> handshakeFuture;

    @Test
    void testPlainStartRejectsFailedTlsHandshake() {
        when(mechanism.getMechanismName()).thenReturn("PLAIN");
        when(context.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(sslHandler);
        when(sslHandler.handshakeFuture()).thenReturn(handshakeFuture);
        when(handshakeFuture.isDone()).thenReturn(true);
        when(handshakeFuture.isSuccess()).thenReturn(false);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, negotiator::start);
    }
}
