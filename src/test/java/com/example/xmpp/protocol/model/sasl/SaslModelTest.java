package com.example.xmpp.protocol.model.sasl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SASL 模型类单元测试。
 */
class SaslModelTest {

    // Auth 测试

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

    // SaslChallenge 测试

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

    // SaslResponse 测试

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

    // SaslSuccess 测试

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

    // SaslFailure 测试

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

    // SaslFailure toXml 测试

    @Test
    @DisplayName("SaslFailure.toXml 应生成包含 condition 和 text 的 XML")
    void testSaslFailureToXmlWithConditionAndText() {
        SaslFailure failure = SaslFailure.builder()
                .condition("not-authorized")
                .text("Invalid credentials")
                .build();

        String xml = failure.toXml();
        assertTrue(xml.contains("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("<not-authorized/>"));
        assertTrue(xml.contains("<text>Invalid credentials</text>"));
        assertTrue(xml.contains("</failure>"));
    }

    @Test
    @DisplayName("SaslFailure.toXml 应生成只有 condition 的 XML")
    void testSaslFailureToXmlConditionOnly() {
        SaslFailure failure = SaslFailure.builder()
                .condition("temporary-auth-failure")
                .build();

        String xml = failure.toXml();
        assertTrue(xml.contains("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("<temporary-auth-failure/>"));
        assertFalse(xml.contains("<text>"));
        assertTrue(xml.contains("</failure>"));
    }

    @Test
    @DisplayName("SaslFailure.toXml 应处理 null condition")
    void testSaslFailureToXmlNullCondition() {
        SaslFailure failure = SaslFailure.builder()
                .text("Some error text")
                .build();

        String xml = failure.toXml();
        assertTrue(xml.contains("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("<text>Some error text</text>"));
        assertTrue(xml.contains("</failure>"));
    }

    // SaslChallenge toXml 测试

    @Test
    @DisplayName("SaslChallenge.toXml 应生成有效 XML")
    void testSaslChallengeToXml() {
        SaslChallenge challenge = SaslChallenge.builder()
                .content("cmVhbG09ImV4YW1wbGUi")
                .build();

        String xml = challenge.toXml();
        assertTrue(xml.contains("<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("cmVhbG09ImV4YW1wbGUi"));
        assertTrue(xml.contains("</challenge>"));
    }

    @Test
    @DisplayName("SaslChallenge.toXml 应处理 null 内容")
    void testSaslChallengeToXmlNullContent() {
        SaslChallenge challenge = SaslChallenge.builder()
                .build();

        String xml = challenge.toXml();
        assertTrue(xml.contains("<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("</challenge>"));
    }

    // SaslSuccess toXml 测试

    @Test
    @DisplayName("SaslSuccess.toXml 应生成有效 XML")
    void testSaslSuccessToXml() {
        SaslSuccess success = SaslSuccess.builder()
                .content("dmVyPTEscj1hYmM=")
                .build();

        String xml = success.toXml();
        assertTrue(xml.contains("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("dmVyPTEscj1hYmM="));
        assertTrue(xml.contains("</success>"));
    }

    @Test
    @DisplayName("SaslSuccess.toXml 应处理 null 内容")
    void testSaslSuccessToXmlNullContent() {
        SaslSuccess success = SaslSuccess.builder()
                .build();

        String xml = success.toXml();
        assertTrue(xml.contains("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("</success>"));
    }

    // Auth toXml 测试

    @Test
    @DisplayName("Auth.toXml 应生成有效 XML")
    void testAuthToXml() {
        Auth auth = new Auth("PLAIN", "dXNlcgBwYXNz");

        String xml = auth.toXml();
        assertTrue(xml.contains("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"PLAIN\">"));
        assertTrue(xml.contains("dXNlcgBwYXNz"));
        assertTrue(xml.contains("</auth>"));
    }

    @Test
    @DisplayName("Auth.toXml 应处理 null 内容")
    void testAuthToXmlNullContent() {
        Auth auth = new Auth("ANONYMOUS", null);

        String xml = auth.toXml();
        assertTrue(xml.contains("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"ANONYMOUS\">"));
        assertTrue(xml.contains("</auth>"));
    }

    // SaslResponse toXml 测试

    @Test
    @DisplayName("SaslResponse.toXml 应生成有效 XML")
    void testSaslResponseToXml() {
        SaslResponse response = new SaslResponse("dXNlcgBwYXNz");

        String xml = response.toXml();
        assertTrue(xml.contains("<response xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("dXNlcgBwYXNz"));
        assertTrue(xml.contains("</response>"));
    }

    @Test
    @DisplayName("SaslResponse.toXml 应处理 null 内容")
    void testSaslResponseToXmlNullContent() {
        SaslResponse response = new SaslResponse(null);

        String xml = response.toXml();
        assertTrue(xml.contains("<response xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("</response>"));
    }

    // Condition 枚举测试

    @Test
    @DisplayName("SaslFailure.Condition 枚举应包含所有标准条件")
    void testSaslFailureConditionEnum() {
        assertEquals(10, SaslFailure.Condition.values().length);
        assertEquals("not-authorized", SaslFailure.Condition.NOT_AUTHORIZED.getValue());
        assertEquals("aborted", SaslFailure.Condition.ABORTED.getValue());
        assertEquals("credentials-expired", SaslFailure.Condition.CREDENTIALS_EXPIRED.getValue());
        assertEquals("encryption-required", SaslFailure.Condition.ENCRYPTION_REQUIRED.getValue());
        assertEquals("incorrect-encoding", SaslFailure.Condition.INCORRECT_ENCODING.getValue());
        assertEquals("invalid-authzid", SaslFailure.Condition.INVALID_AUTHZID.getValue());
        assertEquals("invalid-mechanism", SaslFailure.Condition.INVALID_MECHANISM.getValue());
        assertEquals("invalid-realm", SaslFailure.Condition.INVALID_REALM.getValue());
        assertEquals("mechanism-too-weak", SaslFailure.Condition.MECHANISM_TOO_WEAK.getValue());
        assertEquals("temporary-auth-failure", SaslFailure.Condition.TEMPORARY_AUTH_FAILURE.getValue());
    }

    // SaslChallenge.of 工厂方法测试

    @Test
    @DisplayName("SaslChallenge.of 工厂方法应创建实例")
    void testSaslChallengeOf() {
        SaslChallenge challenge = SaslChallenge.of("test-content");
        assertEquals("test-content", challenge.getContent());
    }
}
