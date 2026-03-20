package com.example.xmpp.exception;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.stream.StreamError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XMPP 异常类单元测试。
 */
class XmppExceptionTest {

    @Test
    @DisplayName("XmppException 无参构造器")
    void testXmppExceptionNoArgs() {
        XmppException ex = new XmppException();
        
        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("XmppException 带消息构造器")
    void testXmppExceptionWithMessage() {
        XmppException ex = new XmppException("Test error");
        
        assertEquals("Test error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("XmppException 带消息和原因构造器")
    void testXmppExceptionWithMessageAndCause() {
        Throwable cause = new RuntimeException("Cause");
        XmppException ex = new XmppException("Test error", cause);
        
        assertEquals("Test error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("XmppException 带原因构造器")
    void testXmppExceptionWithCause() {
        Throwable cause = new RuntimeException("Cause");
        XmppException ex = new XmppException(cause);
        
        assertSame(cause, ex.getCause());
        assertTrue(ex.getMessage().contains("Cause"));
    }

    @Test
    @DisplayName("XmppAuthException 测试")
    void testXmppAuthException() {
        XmppAuthException ex1 = new XmppAuthException("Auth failed");
        XmppAuthException ex2 = new XmppAuthException("Auth failed", new RuntimeException());
        
        assertEquals("Auth failed", ex1.getMessage());
        assertNotNull(ex2.getCause());
        assertTrue(ex1 instanceof XmppException);
    }

    @Test
    @DisplayName("XmppNetworkException 测试")
    void testXmppNetworkException() {
        XmppNetworkException ex1 = new XmppNetworkException("Network error");
        XmppNetworkException ex2 = new XmppNetworkException("Network error", new RuntimeException());
        
        assertEquals("Network error", ex1.getMessage());
        assertNotNull(ex2.getCause());
        assertTrue(ex1 instanceof XmppException);
    }

    @Test
    @DisplayName("XmppParseException 测试")
    void testXmppParseException() {
        XmppParseException ex1 = new XmppParseException("Parse error");
        XmppParseException ex2 = new XmppParseException("Parse error", new RuntimeException());
        
        assertEquals("Parse error", ex1.getMessage());
        assertNotNull(ex2.getCause());
        assertTrue(ex1 instanceof XmppException);
    }

    // XmppProtocolException 测试

    @Test
    @DisplayName("XmppProtocolException 测试")
    void testXmppProtocolException() {
        XmppProtocolException ex1 = new XmppProtocolException("Protocol error");
        XmppProtocolException ex2 = new XmppProtocolException("Protocol error", new RuntimeException());

        assertEquals("Protocol error", ex1.getMessage());
        assertNotNull(ex2.getCause());
        assertTrue(ex1 instanceof XmppException);
    }

    // XmppSaslFailureException 测试

    @Test
    @DisplayName("XmppSaslFailureException 测试")
    void testXmppSaslFailureException() {
        SaslFailure saslFailure = new SaslFailure(SaslFailure.Condition.NOT_AUTHORIZED.getValue(), null);
        XmppSaslFailureException ex = new XmppSaslFailureException(saslFailure);

        assertEquals("SASL authentication failed: not-authorized", ex.getMessage());
    }

    @Test
    @DisplayName("XmppSaslFailureException 为空 failure 时应使用默认消息")
    void testXmppSaslFailureExceptionWithNullFailure() {
        XmppSaslFailureException ex = new XmppSaslFailureException(null);

        assertEquals("SASL authentication failed: no failure element received", ex.getMessage());
    }

    @Test
    @DisplayName("XmppSaslFailureException 条件为空白时不应追加条件文本")
    void testXmppSaslFailureExceptionWithBlankCondition() {
        SaslFailure saslFailure = new SaslFailure("  ", null);
        XmppSaslFailureException ex = new XmppSaslFailureException(saslFailure);

        assertEquals("SASL authentication failed", ex.getMessage());
    }

    @Test
    @DisplayName("XmppStanzaErrorException 测试")
    void testXmppStanzaErrorException() {
        XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST).build();
        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                .id("iq-error")
                .error(error)
                .build();
        XmppStanzaErrorException ex = new XmppStanzaErrorException("Stanza error", errorIq);

        assertEquals("Stanza error", ex.getMessage());
        assertSame(error, ex.getXmppError());
    }

    @Test
    @DisplayName("XmppStreamErrorException 测试")
    void testXmppStreamErrorException() {
        StreamError streamError = StreamError.builder()
                .condition(StreamError.Condition.NOT_AUTHORIZED)
                .text("Authentication required")
                .build();
        XmppStreamErrorException ex = new XmppStreamErrorException(streamError);

        assertEquals("Received stream error: not-authorized (Authentication required)", ex.getMessage());
    }

    @Test
    @DisplayName("XmppStreamErrorException 为空 stream error 时应使用默认消息")
    void testXmppStreamErrorExceptionWithNullStreamError() {
        XmppStreamErrorException ex = new XmppStreamErrorException(null);

        assertEquals("Received stream error", ex.getMessage());
    }

    @Test
    @DisplayName("XmppStreamErrorException 无条件和文本时应保持基础消息")
    void testXmppStreamErrorExceptionWithoutConditionOrText() {
        StreamError streamError = StreamError.builder().build();
        XmppStreamErrorException ex = new XmppStreamErrorException(streamError);

        assertEquals("Received stream error", ex.getMessage());
    }

    // AdminCommandException 测试

    @Test
    @DisplayName("AdminCommandException 测试")
    void testAdminCommandException() {
        AdminCommandException ex1 = new AdminCommandException("add-user", "Admin command error");
        AdminCommandException ex2 = new AdminCommandException("add-user", "Admin command error", new RuntimeException());

        assertEquals("Admin command error", ex1.getMessage());
        assertNotNull(ex2.getCause());
        assertTrue(ex1 instanceof XmppException);
    }

    @Test
    @DisplayName("AdminCommandException 应正确暴露错误响应")
    void testAdminCommandExceptionWithErrorResponse() {
        Iq errorResponse = new Iq.Builder(Iq.Type.ERROR)
                .id("iq-error")
                .build();
        AdminCommandException exception = new AdminCommandException("delete-user", "Admin command error", errorResponse);

        assertEquals("Admin command error", exception.getMessage());
        assertTrue(exception.hasErrorResponse());
    }

    @Test
    @DisplayName("AdminCommandException 无错误响应时应返回 false")
    void testAdminCommandExceptionWithoutErrorResponse() {
        AdminCommandException exception = new AdminCommandException("delete-user", "Admin command error");

        assertFalse(exception.hasErrorResponse());
    }
}
