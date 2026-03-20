package com.example.xmpp.mechanism;

import com.example.xmpp.exception.XmppAuthException;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.security.sasl.SaslException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    @DisplayName("start 在有初始响应时应发送认证节")
    void testStartWithInitialResponseSendsAuthStanza() throws Exception {
        when(mechanism.getMechanismName()).thenReturn("SCRAM-SHA-256");
        when(mechanism.processChallenge(null)).thenReturn(new byte[] {1, 2, 3});
        when(context.alloc()).thenReturn(allocator);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());
        when(allocator.buffer(anyInt(), anyInt())).thenReturn(Unpooled.buffer());
        when(context.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertSame(channelFuture, negotiator.start());
        verify(context).writeAndFlush(any());
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
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());
        when(allocator.buffer(anyInt(), anyInt())).thenReturn(Unpooled.buffer());
        when(context.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        negotiator.handleChallenge("AQID");
    }

    @Test
    @DisplayName("handleChallenge 处理挑战失败时应转换为认证异常")
    void testHandleChallengeWrapsSaslException() throws Exception {
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenThrow(new SaslException("bad challenge"));

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, () -> negotiator.handleChallenge("AQID"));
    }

    @Test
    void testHandleChallengeRejectsInvalidBase64Content() {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, () -> negotiator.handleChallenge("%%%"));
    }

    @Test
    @DisplayName("handleSuccess 无法解码时应抛出认证异常")
    void testHandleSuccessRejectsInvalidBase64Content() {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, () -> negotiator.handleSuccess("%%%"));
    }

    @Test
    @DisplayName("handleSuccess 应返回机制完成状态")
    void testHandleSuccessReturnsMechanismCompletionState() throws Exception {
        when(mechanism.isComplete()).thenReturn(false, true);
        when(mechanism.processChallenge(eq(new byte[] {1, 2, 3}))).thenReturn(new byte[0]);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertFalse(negotiator.handleSuccess(null));
        assertTrue(negotiator.handleSuccess("AQID"));
    }

    @Test
    void testStartRejectsNullMechanismName() {
        when(mechanism.getMechanismName()).thenReturn(null);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, negotiator::start);
    }

    @Test
    @DisplayName("PLAIN 在缺少 SslHandler 时应拒绝启动")
    void testPlainStartRejectsMissingSslHandler() {
        when(mechanism.getMechanismName()).thenReturn("PLAIN");
        when(context.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(null);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, negotiator::start);
    }

    @Test
    @DisplayName("PLAIN 在 TLS 握手被取消时应拒绝启动")
    void testPlainStartRejectsCancelledTlsHandshake() {
        when(mechanism.getMechanismName()).thenReturn("PLAIN");
        when(context.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(sslHandler);
        when(sslHandler.handshakeFuture()).thenReturn(handshakeFuture);
        when(handshakeFuture.isDone()).thenReturn(true);
        when(handshakeFuture.isSuccess()).thenReturn(true);
        when(handshakeFuture.isCancelled()).thenReturn(true);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, negotiator::start);
    }
}
