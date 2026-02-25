package com.example.xmpp.protocol.model.sasl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SASL 模型类单元测试。
 */
class SaslModelTest {

    // ==================== Auth 测试 ====================

    @Test
    @DisplayName("Auth 应正确创建")
    void testAuthCreate() {
        Auth auth = new Auth("PLAIN", "dXNlcgBwYXNz");
        
        assertEquals("PLAIN", auth.getMechanism());
        assertEquals("dXNlcgBwYXNz", auth.getContent());
    }

    @Test
    @DisplayName("Auth 应支持空内容")
    void testAuthEmptyContent() {
        Auth auth = new Auth("ANONYMOUS", null);
        
        assertEquals("ANONYMOUS", auth.getMechanism());
        assertNull(auth.getContent());
    }

    // ==================== SaslChallenge 测试 ====================

    @Test
    @DisplayName("SaslChallenge 应正确创建")
    void testSaslChallengeCreate() {
        SaslChallenge challenge = SaslChallenge.builder()
                .content("cmVhbG09ImV4YW1wbGUi")
                .build();
        
        assertEquals("cmVhbG09ImV4YW1wbGUi", challenge.getContent());
    }

    @Test
    @DisplayName("SaslChallenge 应支持空内容")
    void testSaslChallengeEmpty() {
        SaslChallenge challenge = SaslChallenge.builder().build();
        
        assertNull(challenge.getContent());
    }

    // ==================== SaslResponse 测试 ====================

    @Test
    @DisplayName("SaslResponse 应正确创建")
    void testSaslResponseCreate() {
        SaslResponse response = new SaslResponse("dXNlcgBwYXNz");
        
        assertEquals("dXNlcgBwYXNz", response.getContent());
    }

    @Test
    @DisplayName("SaslResponse 应支持空内容")
    void testSaslResponseEmpty() {
        SaslResponse response = new SaslResponse(null);
        
        assertNull(response.getContent());
    }

    // ==================== SaslSuccess 测试 ====================

    @Test
    @DisplayName("SaslSuccess 应正确创建")
    void testSaslSuccessCreate() {
        SaslSuccess success = SaslSuccess.builder()
                .content("dmVyPTEscj1hYmM=")
                .build();
        
        assertEquals("dmVyPTEscj1hYmM=", success.getContent());
    }

    @Test
    @DisplayName("SaslSuccess 应支持空内容")
    void testSaslSuccessEmpty() {
        SaslSuccess success = SaslSuccess.builder().build();
        
        assertNull(success.getContent());
    }

    // ==================== SaslFailure 测试 ====================

    @Test
    @DisplayName("SaslFailure 应正确创建")
    void testSaslFailureCreate() {
        SaslFailure failure = SaslFailure.builder()
                .condition("not-authorized")
                .text("Invalid credentials")
                .build();
        
        assertEquals("not-authorized", failure.getCondition());
        assertEquals("Invalid credentials", failure.getText());
    }

    @Test
    @DisplayName("SaslFailure 应支持只有 condition")
    void testSaslFailureConditionOnly() {
        SaslFailure failure = SaslFailure.builder()
                .condition("temporary-auth-failure")
                .build();
        
        assertEquals("temporary-auth-failure", failure.getCondition());
        assertNull(failure.getText());
    }
}
