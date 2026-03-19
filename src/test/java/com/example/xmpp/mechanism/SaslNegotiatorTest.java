package com.example.xmpp.mechanism;

import com.example.xmpp.exception.XmppAuthException;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * SaslNegotiator 单元测试。
 */
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

    @Mock
    private ByteBufAllocator allocator;

    @Mock
    private ChannelFuture channelFuture;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

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

    @Test
    void testHandleChallengeTreatsNullMechanismResponseAsEmptySaslPayload() throws Exception {
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenReturn(null);
        when(context.alloc()).thenReturn(allocator);
        when(allocator.buffer(anyInt())).thenReturn(io.netty.buffer.Unpooled.buffer());
        when(allocator.buffer(anyInt(), anyInt())).thenReturn(io.netty.buffer.Unpooled.buffer());
        when(context.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        negotiator.handleChallenge("AQID");
    }

    @Test
    void testHandleChallengeRejectsInvalidBase64Content() {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, () -> negotiator.handleChallenge("%%%"));
    }
}
