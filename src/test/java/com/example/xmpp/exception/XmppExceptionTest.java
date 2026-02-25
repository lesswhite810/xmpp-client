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
}
