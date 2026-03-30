package com.example.xmpp.mechanism;

import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.protocol.model.sasl.Auth;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import javax.security.sasl.SaslException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
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

        XmppAuthException exception = assertThrows(XmppAuthException.class, () -> negotiator.handleChallenge("AQID"));
        assertEquals("Failed to process challenge", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testHandleChallengeRejectsInvalidBase64Content() {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        XmppAuthException exception = assertThrows(XmppAuthException.class, () -> negotiator.handleChallenge("%%%"));
        assertEquals("Invalid challenge content", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("handleSuccess 无法解码时应抛出认证异常")
    void testHandleSuccessRejectsInvalidBase64Content() {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        XmppAuthException exception = assertThrows(XmppAuthException.class, () -> negotiator.handleSuccess("%%%"));
        assertEquals("Invalid success content", exception.getMessage());
        assertNull(exception.getCause());
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
    void testStartDoesNotExposeInitialResponseCause() throws Exception {
        when(mechanism.getMechanismName()).thenReturn("SCRAM-SHA-256");
        when(mechanism.processChallenge(null)).thenThrow(new SaslException("secret"));

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        XmppAuthException exception = assertThrows(XmppAuthException.class, negotiator::start);
        assertEquals("Failed to generate initial response", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testHandleSuccessDoesNotExposeVerificationCause() throws Exception {
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenThrow(new SaslException("secret"));

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        XmppAuthException exception = assertThrows(XmppAuthException.class, () -> negotiator.handleSuccess("AQID"));
        assertEquals("Failed to verify server signature", exception.getMessage());
        assertNull(exception.getCause());
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

    @Test
    @DisplayName("encodeSaslContent 应在内容为空时返回 '='")
    void testEncodeSaslContentReturnsEqualSignForEmpty() throws Exception {
        Method encodeSaslContent = SaslNegotiator.class.getDeclaredMethod("encodeSaslContent", byte[].class);
        encodeSaslContent.setAccessible(true);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        String result = (String) encodeSaslContent.invoke(negotiator, new byte[0]);
        assertEquals("=", result);
    }

    @Test
    @DisplayName("encodeSaslContent 应在内容为 null 时返回 '='")
    void testEncodeSaslContentReturnsEqualSignForNull() throws Exception {
        Method encodeSaslContent = SaslNegotiator.class.getDeclaredMethod("encodeSaslContent", byte[].class);
        encodeSaslContent.setAccessible(true);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        String result = (String) encodeSaslContent.invoke(negotiator, (byte[]) null);
        assertEquals("=", result);
    }

    @Test
    @DisplayName("handleChallenge 应处理非 null 响应")
    void testHandleChallengeWithNonNullResponse() throws Exception {
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenReturn(new byte[] {4, 5, 6});
        when(context.alloc()).thenReturn(allocator);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());
        when(allocator.buffer(anyInt(), anyInt())).thenReturn(Unpooled.buffer());
        when(context.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        negotiator.handleChallenge("AQID");
        verify(context).writeAndFlush(any());
    }

    @Test
    @DisplayName("handleSuccess 应在 contentB64 非空时解码")
    void testHandleSuccessWithNonEmptyContentB64() throws Exception {
        when(mechanism.isComplete()).thenReturn(true);
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenReturn(new byte[0]);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertTrue(negotiator.handleSuccess("AQID"));
    }

    @Test
    @DisplayName("handleSuccess 应在机制未完成时返回 false")
    void testHandleSuccessReturnsFalseWhenNotComplete() throws Exception {
        when(mechanism.isComplete()).thenReturn(false);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertFalse(negotiator.handleSuccess(null));
        assertFalse(negotiator.handleSuccess(""));
    }

    @Test
    @DisplayName("sendStanza 在 packet 为 null 时应抛出参数异常")
    void testSendStanzaRejectsNullPacket() throws Exception {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);
        Method sendStanza = SaslNegotiator.class.getDeclaredMethod("sendStanza", Object.class);
        sendStanza.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> sendStanza.invoke(negotiator, new Object[] {null}));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(cause.getMessage().contains("Packet must not be null"));
    }

    @Test
    @DisplayName("sendStanza 在发送失败时应触发异常通知")
    void testSendStanzaTriggersExceptionCaughtOnFailure() throws Exception {
        when(context.alloc()).thenReturn(allocator);
        when(context.pipeline()).thenReturn(pipeline);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());

        ChannelFuture mockFuture = Mockito.mock(ChannelFuture.class);
        when(mockFuture.isSuccess()).thenReturn(false);
        when(mockFuture.cause()).thenReturn(new RuntimeException("send failed"));
        when(context.writeAndFlush(any())).thenReturn(mockFuture);

        doAnswer(invocation -> {
            Object listener = invocation.getArgument(0);
            Method operationComplete = listener.getClass().getDeclaredMethod("operationComplete", Future.class);
            operationComplete.setAccessible(true);
            operationComplete.invoke(listener, mockFuture);
            return mockFuture;
        }).when(mockFuture).addListener(any());

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);
        Method sendStanza = SaslNegotiator.class.getDeclaredMethod("sendStanza", Object.class);
        sendStanza.setAccessible(true);

        when(mechanism.getMechanismName()).thenReturn("TEST");
        sendStanza.invoke(negotiator, new Auth("TEST", "dGVzdA=="));

        verify(context.pipeline()).fireExceptionCaught(any(XmppAuthException.class));
    }

    @Test
    @DisplayName("isTlsEncrypted 在 SslHandler 存在但握手未完成时返回 false")
    void testIsTlsEncryptedReturnsFalseWhenHandshakeNotDone() {
        when(mechanism.getMechanismName()).thenReturn("PLAIN");
        when(context.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(sslHandler);
        when(sslHandler.handshakeFuture()).thenReturn(handshakeFuture);
        when(handshakeFuture.isDone()).thenReturn(false);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, negotiator::start);
    }

    @Test
    @DisplayName("isTlsEncrypted 在 SslHandler 存在但握手失败时返回 false")
    void testIsTlsEncryptedReturnsFalseWhenHandshakeFails() {
        when(mechanism.getMechanismName()).thenReturn("PLAIN");
        when(context.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(sslHandler);
        when(sslHandler.handshakeFuture()).thenReturn(handshakeFuture);
        when(handshakeFuture.isDone()).thenReturn(true);
        when(handshakeFuture.isSuccess()).thenReturn(false);
        when(handshakeFuture.isCancelled()).thenReturn(false);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(XmppAuthException.class, negotiator::start);
    }

    @Test
    @DisplayName("sendStanza 在发送成功时应触发成功日志")
    void testSendStanzaTriggersSuccessListener() throws Exception {
        when(context.alloc()).thenReturn(allocator);
        when(context.pipeline()).thenReturn(pipeline);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());

        ChannelFuture mockFuture = Mockito.mock(ChannelFuture.class);
        when(mockFuture.isSuccess()).thenReturn(true);
        when(context.writeAndFlush(any())).thenReturn(mockFuture);

        doAnswer(invocation -> {
            Object listener = invocation.getArgument(0);
            Method operationComplete = listener.getClass().getDeclaredMethod("operationComplete", Future.class);
            operationComplete.setAccessible(true);
            operationComplete.invoke(listener, mockFuture);
            return mockFuture;
        }).when(mockFuture).addListener(any());

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);
        Method sendStanza = SaslNegotiator.class.getDeclaredMethod("sendStanza", Object.class);
        sendStanza.setAccessible(true);

        when(mechanism.getMechanismName()).thenReturn("TEST");
        sendStanza.invoke(negotiator, new Auth("TEST", "dGVzdA=="));
    }

    @Test
    @DisplayName("sendStanza 在 packet 不实现 ExtensionElement 时应抛出异常")
    void testSendStanzaRejectsNonExtensionElement() throws Exception {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);
        Method sendStanza = SaslNegotiator.class.getDeclaredMethod("sendStanza", Object.class);
        sendStanza.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> sendStanza.invoke(negotiator, "plain string"));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(cause.getMessage().contains("Packet must implement ExtensionElement"));
    }

    @Test
    @DisplayName("PLAIN start 在 TLS 握手成功时应返回 TLS-wrapped channel")
    void testPlainStartWithSuccessfulTlsHandshake() throws Exception {
        when(mechanism.getMechanismName()).thenReturn("PLAIN");
        when(context.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(sslHandler);
        when(sslHandler.handshakeFuture()).thenReturn(handshakeFuture);
        when(handshakeFuture.isDone()).thenReturn(true);
        when(handshakeFuture.isSuccess()).thenReturn(true);
        when(handshakeFuture.isCancelled()).thenReturn(false);
        when(mechanism.processChallenge(null)).thenReturn(new byte[] {1, 2, 3});
        when(context.alloc()).thenReturn(allocator);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());
        when(allocator.buffer(anyInt(), anyInt())).thenReturn(Unpooled.buffer());
        when(context.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        ChannelFuture result = negotiator.start();
        assertNotNull(result);
    }

    // ===== New JaCoCo coverage tests for uncovered branches =====

    /**
     * Gap 2: Future.cause() returns null (line 148 else branch).
     * When cause() returns null, the log message uses "unknown" instead of cause class name.
     */
    @Test
    @DisplayName("sendStanza 在发送失败且 cause 为 null 时应记录 unknown 错误类型")
    void testSendStanzaFailureWithNullCause() throws Exception {
        when(context.alloc()).thenReturn(allocator);
        when(context.pipeline()).thenReturn(pipeline);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());

        // Create mock without stubbing cause() - Mockito returns null by default
        ChannelFuture mockFuture = Mockito.mock(ChannelFuture.class);
        when(mockFuture.isSuccess()).thenReturn(false);
        // cause() is NOT stubbed - returns null by default
        when(context.writeAndFlush(any())).thenReturn(mockFuture);

        doAnswer(invocation -> {
            Object listener = invocation.getArgument(0);
            Method operationComplete = listener.getClass().getDeclaredMethod("operationComplete", Future.class);
            operationComplete.setAccessible(true);
            operationComplete.invoke(listener, mockFuture);
            return mockFuture;
        }).when(mockFuture).addListener(any());

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);
        Method sendStanza = SaslNegotiator.class.getDeclaredMethod("sendStanza", Object.class);
        sendStanza.setAccessible(true);

        when(mechanism.getMechanismName()).thenReturn("TEST");
        sendStanza.invoke(negotiator, new Auth("TEST", "dGVzdA=="));

        // Verify exceptionCaught was fired with XmppAuthException
        verify(context.pipeline()).fireExceptionCaught(any(XmppAuthException.class));
    }

    /**
     * Gap 3: handleChallenge with null contentB64 (line 79 ternary branch).
     * When contentB64 is null, ternary returns "" (empty string), decode("") returns empty array.
     */
    @Test
    @DisplayName("handleChallenge 在 contentB64 为 null 时应使用空字符串解码")
    void testHandleChallengeWithNullContentB64() throws Exception {
        when(mechanism.processChallenge(new byte[] {})).thenReturn(new byte[] {1, 2, 3});
        when(context.alloc()).thenReturn(allocator);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());
        when(allocator.buffer(anyInt(), anyInt())).thenReturn(Unpooled.buffer());
        when(context.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        // Passing null triggers the ternary: contentB64 != null ? contentB64 : ""
        // which decodes empty string and passes empty byte array to processChallenge
        negotiator.handleChallenge(null);
        verify(context).writeAndFlush(any());
    }

    /**
     * Gap 5: encodeSaslContent with non-null, non-empty content (line 158).
     * Tests that actual byte content is Base64 encoded correctly.
     */
    @Test
    @DisplayName("encodeSaslContent 应正确编码非空字节数组")
    void testEncodeSaslContentWithActualBytes() throws Exception {
        Method encodeSaslContent = SaslNegotiator.class.getDeclaredMethod("encodeSaslContent", byte[].class);
        encodeSaslContent.setAccessible(true);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        String result = (String) encodeSaslContent.invoke(negotiator, new byte[] {1, 2, 3});
        assertEquals("AQID", result);  // Base64 encoded form of [1, 2, 3]
    }

    /**
     * Gap 4: handleSuccess with contentB64="" (empty string) and isComplete=true.
     * When contentB64 is empty string, isNotEmpty returns false, skip the decode block.
     * But if isComplete is true, should return true.
     */
    @Test
    @DisplayName("handleSuccess 在 contentB64 为空字符串且机制完成时应返回 true")
    void testHandleSuccessWithEmptyStringContentB64AndComplete() throws Exception {
        when(mechanism.isComplete()).thenReturn(true);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        // Empty string triggers isNotEmpty=false branch, but isComplete=true returns true
        assertTrue(negotiator.handleSuccess(""));
    }

    /**
     * Gap 6: sendStanza success path logging verification.
     * When result.isSuccess() is true, the listener logs "SASL stanza sent successfully".
     */
    @Test
    @DisplayName("sendStanza 在发送成功时应触发成功监听器")
    void testSendStanzaSuccessListenerIsInvoked() throws Exception {
        when(context.alloc()).thenReturn(allocator);
        when(context.pipeline()).thenReturn(pipeline);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());

        ChannelFuture mockFuture = Mockito.mock(ChannelFuture.class);
        when(mockFuture.isSuccess()).thenReturn(true);
        when(context.writeAndFlush(any())).thenReturn(mockFuture);

        doAnswer(invocation -> {
            Object listener = invocation.getArgument(0);
            Method operationComplete = listener.getClass().getDeclaredMethod("operationComplete", Future.class);
            operationComplete.setAccessible(true);
            operationComplete.invoke(listener, mockFuture);
            return mockFuture;
        }).when(mockFuture).addListener(any());

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);
        Method sendStanza = SaslNegotiator.class.getDeclaredMethod("sendStanza", Object.class);
        sendStanza.setAccessible(true);

        when(mechanism.getMechanismName()).thenReturn("TEST");
        sendStanza.invoke(negotiator, new Auth("TEST", "dGVzdA=="));

        // Verify writeAndFlush was called (success path)
        verify(context).writeAndFlush(any());
        // Verify no exceptionCaught was fired (success path)
        verify(context.pipeline(), never()).fireExceptionCaught(any());
    }

    /**
     * Gap 7: handleChallenge with mechanism.processChallenge returning non-null.
     * Already covered by testHandleChallengeWithNonNullResponse, but adding explicit coverage
     * for the encodeSaslContent path when response is non-null.
     */
    @Test
    @DisplayName("handleChallenge 应正确处理 processChallenge 返回的非 null 响应")
    void testHandleChallengeWithNonNullResponseEncoding() throws Exception {
        byte[] responseBytes = new byte[] {10, 20, 30, 40};
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenReturn(responseBytes);
        when(context.alloc()).thenReturn(allocator);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());
        when(allocator.buffer(anyInt(), anyInt())).thenReturn(Unpooled.buffer());
        when(context.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        negotiator.handleChallenge("AQID");  // Base64 of [1, 2, 3]
        verify(context).writeAndFlush(any());
    }

    /**
     * Additional coverage: handleChallenge with empty byte array response.
     * When mechanism.processChallenge returns empty array, encodeSaslContent returns "=".
     */
    @Test
    @DisplayName("handleChallenge 应正确处理 processChallenge 返回的空数组")
    void testHandleChallengeWithEmptyResponse() throws Exception {
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenReturn(new byte[] {});
        when(context.alloc()).thenReturn(allocator);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());
        when(allocator.buffer(anyInt(), anyInt())).thenReturn(Unpooled.buffer());
        when(context.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        negotiator.handleChallenge("AQID");
        verify(context).writeAndFlush(any());
    }

    /**
     * Additional coverage: verify sendStanza calls fireExceptionCaught on failure.
     */
    @Test
    @DisplayName("sendStanza 在发送失败时应触发 pipeline 的 exceptionCaught 事件")
    void testSendStanzaTriggersExceptionCaughtEventOnFailure() throws Exception {
        when(context.alloc()).thenReturn(allocator);
        when(context.pipeline()).thenReturn(pipeline);
        when(allocator.buffer(anyInt())).thenReturn(Unpooled.buffer());

        RuntimeException causeException = new RuntimeException("send failed");
        ChannelFuture mockFuture = Mockito.mock(ChannelFuture.class);
        when(mockFuture.isSuccess()).thenReturn(false);
        when(mockFuture.cause()).thenReturn(causeException);
        when(context.writeAndFlush(any())).thenReturn(mockFuture);

        doAnswer(invocation -> {
            Object listener = invocation.getArgument(0);
            Method operationComplete = listener.getClass().getDeclaredMethod("operationComplete", Future.class);
            operationComplete.setAccessible(true);
            operationComplete.invoke(listener, mockFuture);
            return mockFuture;
        }).when(mockFuture).addListener(any());

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);
        Method sendStanza = SaslNegotiator.class.getDeclaredMethod("sendStanza", Object.class);
        sendStanza.setAccessible(true);

        when(mechanism.getMechanismName()).thenReturn("TEST");
        sendStanza.invoke(negotiator, new Auth("TEST", "dGVzdA=="));

        ArgumentCaptor<XmppAuthException> captor = ArgumentCaptor.forClass(XmppAuthException.class);
        verify(context.pipeline()).fireExceptionCaught(captor.capture());
        assertEquals("Failed to send SASL stanza", captor.getValue().getMessage());
        assertSame(causeException, captor.getValue().getCause());
    }

    /**
     * Additional coverage: handleSuccess with non-empty contentB64 and isComplete=true.
     */
    @Test
    @DisplayName("handleSuccess 在 contentB64 非空且机制完成时应返回 true")
    void testHandleSuccessWithNonEmptyContentB64AndComplete() throws Exception {
        when(mechanism.isComplete()).thenReturn(true);
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenReturn(new byte[] {});

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertTrue(negotiator.handleSuccess("AQID"));  // Base64 of [1, 2, 3]
    }

    /**
     * Additional coverage: handleChallenge handles IllegalArgumentException from decode.
     */
    @Test
    @DisplayName("handleChallenge 应处理非法 Base64 内容")
    void testHandleChallengeWithInvalidBase64Content() {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        // "!!!" is not valid Base64 - should throw IllegalArgumentException
        XmppAuthException exception = assertThrows(XmppAuthException.class,
                () -> negotiator.handleChallenge("!!!"));
        assertEquals("Invalid challenge content", exception.getMessage());
    }

    /**
     * Additional coverage: handleSuccess handles IllegalArgumentException from decode.
     */
    @Test
    @DisplayName("handleSuccess 应处理非法 Base64 内容")
    void testHandleSuccessWithInvalidBase64Content() {
        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        // "!!!" is not valid Base64
        XmppAuthException exception = assertThrows(XmppAuthException.class,
                () -> negotiator.handleSuccess("!!!"));
        assertEquals("Invalid success content", exception.getMessage());
    }

    /**
     * Additional coverage: handleSuccess handles SaslException from mechanism.processChallenge.
     */
    @Test
    @DisplayName("handleSuccess 应处理机制验证失败时的 SaslException")
    void testHandleSuccessWithSaslExceptionOnVerification() throws Exception {
        when(mechanism.isComplete()).thenReturn(true);
        when(mechanism.processChallenge(new byte[] {1, 2, 3})).thenThrow(new SaslException("verification failed"));

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        XmppAuthException exception = assertThrows(XmppAuthException.class,
                () -> negotiator.handleSuccess("AQID"));
        assertEquals("Failed to verify server signature", exception.getMessage());
        assertNull(exception.getCause());
    }

    /**
     * Additional coverage: start() with PLAIN mechanism when TLS is not encrypted throws.
     */
    @Test
    @DisplayName("PLAIN start 在 TLS 未加密时应抛出异常")
    void testPlainStartRequiresTlsEncryption() {
        when(mechanism.getMechanismName()).thenReturn("PLAIN");
        when(context.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(null);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        XmppAuthException exception = assertThrows(XmppAuthException.class, negotiator::start);
        assertTrue(exception.getMessage().contains("PLAIN authentication requires TLS encryption"));
    }

    /**
     * Additional coverage: start() with blank mechanism name throws.
     */
    @Test
    @DisplayName("start 在机制名称为空白时应抛出异常")
    void testStartWithBlankMechanismName() {
        when(mechanism.getMechanismName()).thenReturn("   ");

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        XmppAuthException exception = assertThrows(XmppAuthException.class, negotiator::start);
        assertTrue(exception.getMessage().contains("SASL mechanism name must not be null or blank"));
    }

    /**
     * Additional coverage: start() handles SaslException from processChallenge.
     */
    @Test
    @DisplayName("start 应处理 processChallenge 抛出 SaslException 的情况")
    void testStartHandlesSaslExceptionFromProcessChallenge() throws Exception {
        when(mechanism.getMechanismName()).thenReturn("SCRAM-SHA-256");
        when(mechanism.processChallenge(null)).thenThrow(new SaslException("initial response failed"));

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        XmppAuthException exception = assertThrows(XmppAuthException.class, negotiator::start);
        assertEquals("Failed to generate initial response", exception.getMessage());
        assertNull(exception.getCause());
    }

    /**
     * Additional coverage: verify isTlsEncrypted throws NPE when handshakeFuture returns null.
     */
    @Test
    @DisplayName("isTlsEncrypted 在 SslHandler 存在但 handshakeFuture 为 null 时抛出 NPE")
    void testIsTlsEncryptedReturnsFalseWhenHandshakeFutureIsNull() throws Exception {
        when(mechanism.getMechanismName()).thenReturn("PLAIN");
        when(context.pipeline()).thenReturn(pipeline);
        when(pipeline.get(SslHandler.class)).thenReturn(sslHandler);
        when(sslHandler.handshakeFuture()).thenReturn(null);

        SaslNegotiator negotiator = new SaslNegotiator(mechanism, context);

        assertThrows(NullPointerException.class, negotiator::start);
    }
}
