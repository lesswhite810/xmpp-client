package com.example.xmpp.protocol.model.sasl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaslChallenge 单元测试。
 */
class SaslChallengeTest {

    @Test
    @DisplayName("SaslChallenge 应正确创建")
    void testCreate() {
        SaslChallenge challenge = SaslChallenge.of("cmVhbG09ZXhhbXBsZS5jb20=");
        
        assertNotNull(challenge);
        assertEquals("cmVhbG09ZXhhbXBsZS5jb20=", challenge.getContent());
    }

    @Test
    @DisplayName("SaslChallenge 空负载")
    void testEmptyPayload() {
        SaslChallenge challenge = SaslChallenge.of("");
        
        assertNotNull(challenge);
    }

    @Test
    @DisplayName("getElementName 应返回 challenge")
    void testGetElementName() {
        SaslChallenge challenge = SaslChallenge.of("test");
        
        assertEquals("challenge", challenge.getElementName());
    }
}
