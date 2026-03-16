package com.example.xmpp.exception;

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

    @Test
    @DisplayName("XmppDnsException 测试")
    void testXmppDnsException() {
        XmppDnsException ex1 = new XmppDnsException("DNS error");
        XmppDnsException ex2 = new XmppDnsException("DNS error", new RuntimeException());
        
        assertEquals("DNS error", ex1.getMessage());
        assertNotNull(ex2.getCause());
        assertTrue(ex1 instanceof XmppException);
    }

    @Test
    @DisplayName("异常可以被抛出和捕获")
    void testExceptionThrowCatch() {
        assertThrows(XmppException.class, () -> {
            throw new XmppException("Test");
        });
        
        assertThrows(XmppAuthException.class, () -> {
            throw new XmppAuthException("Test");
        });
    }

    @Test
    @DisplayName("异常继承链正确")
    void testExceptionInheritance() {
        XmppException authEx = new XmppAuthException("auth");
        XmppException netEx = new XmppNetworkException("net");
        XmppException parseEx = new XmppParseException("parse");
        XmppException dnsEx = new XmppDnsException("dns");

        assertTrue(authEx instanceof Exception);
        assertTrue(netEx instanceof Exception);
        assertTrue(parseEx instanceof Exception);
        assertTrue(dnsEx instanceof Exception);
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
        // XmppSaslFailureException 需要 SaslFailure 参数，这里只测试它继承自 XmppException
        XmppException ex = new XmppSaslFailureException(null);
        assertTrue(ex instanceof XmppException);
    }

    // XmppStanzaErrorException 测试

    @Test
    @DisplayName("XmppStanzaErrorException 测试")
    void testXmppStanzaErrorException() {
        // XmppStanzaErrorException 需要 Iq 参数，这里只测试它继承自 XmppException
        XmppException ex = new XmppStanzaErrorException("Stanza error", null);
        assertTrue(ex instanceof XmppException);
    }

    // XmppStreamErrorException 测试

    @Test
    @DisplayName("XmppStreamErrorException 测试")
    void testXmppStreamErrorException() {
        // XmppStreamErrorException 需要 StreamError 参数，这里只测试它继承自 XmppException
        XmppException ex = new XmppStreamErrorException(null);
        assertTrue(ex instanceof XmppException);
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

    // 异常链测试

    @Test
    @DisplayName("异常链应该保持完整")
    void testExceptionChaining() {
        RuntimeException rootCause = new RuntimeException("Root cause");
        XmppException ex = new XmppException("Wrapper message", rootCause);

        assertSame(rootCause, ex.getCause());
        assertEquals("Wrapper message", ex.getMessage());
        assertTrue(ex.getCause().getMessage().contains("Root cause"));
    }

    // 异常序列化测试（验证异常可以正常序列化）

    @Test
    @DisplayName("所有异常应该可以正常创建和抛出")
    void testAllExceptionsCanBeThrown() {
        assertThrows(XmppException.class, () -> { throw new XmppException("test"); });
        assertThrows(XmppAuthException.class, () -> { throw new XmppAuthException("test"); });
        assertThrows(XmppNetworkException.class, () -> { throw new XmppNetworkException("test"); });
        assertThrows(XmppParseException.class, () -> { throw new XmppParseException("test"); });
        assertThrows(XmppDnsException.class, () -> { throw new XmppDnsException("test"); });
        assertThrows(XmppProtocolException.class, () -> { throw new XmppProtocolException("test"); });
        assertThrows(XmppSaslFailureException.class, () -> { throw new XmppSaslFailureException(null); });
        assertThrows(XmppStanzaErrorException.class, () -> { throw new XmppStanzaErrorException("test", null); });
        assertThrows(XmppStreamErrorException.class, () -> { throw new XmppStreamErrorException(null); });
        assertThrows(AdminCommandException.class, () -> { throw new AdminCommandException("add-user", "test"); });
    }
}
