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
        
        assertEquals("PLAIN", auth.mechanism());
        assertEquals("dXNlcgBwYXNz", auth.content());
    }

    @Test
    @DisplayName("Auth 应支持空内容")
    void testAuthEmptyContent() {
        Auth auth = new Auth("ANONYMOUS", null);
        
        assertEquals("ANONYMOUS", auth.mechanism());
        assertNull(auth.content());
    }

    // SaslChallenge 测试

    @Test
    @DisplayName("SaslChallenge 应正确创建")
    void testSaslChallengeCreate() {
        SaslChallenge challenge = new SaslChallenge("cmVhbG09ImV4YW1wbGUi");
        
        assertEquals("cmVhbG09ImV4YW1wbGUi", challenge.content());
    }

    @Test
    @DisplayName("SaslChallenge 应支持空内容")
    void testSaslChallengeEmpty() {
        SaslChallenge challenge = new SaslChallenge(null);
        
        assertNull(challenge.content());
    }

    // SaslResponse 测试

    @Test
    @DisplayName("SaslResponse 应正确创建")
    void testSaslResponseCreate() {
        SaslResponse response = new SaslResponse("dXNlcgBwYXNz");
        
        assertEquals("dXNlcgBwYXNz", response.content());
    }

    @Test
    @DisplayName("SaslResponse 应支持空内容")
    void testSaslResponseEmpty() {
        SaslResponse response = new SaslResponse(null);
        
        assertNull(response.content());
    }

    // SaslSuccess 测试

    @Test
    @DisplayName("SaslSuccess 应正确创建")
    void testSaslSuccessCreate() {
        SaslSuccess success = new SaslSuccess("dmVyPTEscj1hYmM=");
        
        assertEquals("dmVyPTEscj1hYmM=", success.content());
    }

    @Test
    @DisplayName("SaslSuccess 应支持空内容")
    void testSaslSuccessEmpty() {
        SaslSuccess success = new SaslSuccess(null);
        
        assertNull(success.content());
    }

    // SaslFailure 测试

    @Test
    @DisplayName("SaslFailure 应正确创建")
    void testSaslFailureCreate() {
        SaslFailure failure = new SaslFailure("not-authorized", "Invalid credentials");
        
        assertEquals("not-authorized", failure.condition());
        assertEquals("Invalid credentials", failure.text());
    }

    @Test
    @DisplayName("SaslFailure 应支持只有 condition")
    void testSaslFailureConditionOnly() {
        SaslFailure failure = new SaslFailure("temporary-auth-failure", null);

        assertEquals("temporary-auth-failure", failure.condition());
        assertNull(failure.text());
    }

    // SaslFailure toXml 测试

    @Test
    @DisplayName("SaslFailure.toXml 应生成包含 condition 和 text 的 XML")
    void testSaslFailureToXmlWithConditionAndText() {
        SaslFailure failure = new SaslFailure("not-authorized", "Invalid credentials");

        String xml = failure.toXml();
        assertTrue(xml.contains("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("<not-authorized/>"));
        assertTrue(xml.contains("<text>Invalid credentials</text>"));
        assertTrue(xml.contains("</failure>"));
    }

    @Test
    @DisplayName("SaslFailure.toXml 应生成只有 condition 的 XML")
    void testSaslFailureToXmlConditionOnly() {
        SaslFailure failure = new SaslFailure("temporary-auth-failure", null);

        String xml = failure.toXml();
        assertTrue(xml.contains("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("<temporary-auth-failure/>"));
        assertFalse(xml.contains("<text>"));
        assertTrue(xml.contains("</failure>"));
    }

    @Test
    @DisplayName("SaslFailure.toXml 应处理 null condition")
    void testSaslFailureToXmlNullCondition() {
        SaslFailure failure = new SaslFailure(null, "Some error text");

        String xml = failure.toXml();
        assertTrue(xml.contains("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("<text>Some error text</text>"));
        assertTrue(xml.contains("</failure>"));
    }

    @Test
    @DisplayName("SaslFailure.toXml 在 text 为空时不应输出空 text 元素")
    void testSaslFailureToXmlWithoutTextElementWhenTextIsNull() {
        SaslFailure failure = new SaslFailure("not-authorized", null);

        String xml = failure.toXml();

        assertFalse(xml.contains("<text"));
    }

    @Test
    @DisplayName("SaslFailure.toXml 应转义 text 中的 XML 特殊字符")
    void testSaslFailureToXmlEscapesText() {
        SaslFailure failure = new SaslFailure("not-authorized", "bad <xml> & reason");

        String xml = failure.toXml();

        assertTrue(xml.contains("bad &lt;xml&gt; &amp; reason"));
    }

    // SaslChallenge toXml 测试

    @Test
    @DisplayName("SaslChallenge.toXml 应生成有效 XML")
    void testSaslChallengeToXml() {
        SaslChallenge challenge = new SaslChallenge("cmVhbG09ImV4YW1wbGUi");

        String xml = challenge.toXml();
        assertTrue(xml.contains("<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("cmVhbG09ImV4YW1wbGUi"));
        assertTrue(xml.contains("</challenge>"));
    }

    @Test
    @DisplayName("SaslChallenge.toXml 应处理 null 内容")
    void testSaslChallengeToXmlNullContent() {
        SaslChallenge challenge = new SaslChallenge(null);

        String xml = challenge.toXml();
        assertTrue(xml.contains("<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>"));
    }

    // SaslSuccess toXml 测试

    @Test
    @DisplayName("SaslSuccess.toXml 应生成有效 XML")
    void testSaslSuccessToXml() {
        SaslSuccess success = new SaslSuccess("dmVyPTEscj1hYmM=");

        String xml = success.toXml();
        assertTrue(xml.contains("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">"));
        assertTrue(xml.contains("dmVyPTEscj1hYmM="));
        assertTrue(xml.contains("</success>"));
    }

    @Test
    @DisplayName("SaslSuccess.toXml 应处理 null 内容")
    void testSaslSuccessToXmlNullContent() {
        SaslSuccess success = new SaslSuccess(null);

        String xml = success.toXml();
        assertTrue(xml.contains("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>"));
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
        assertTrue(xml.contains("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"ANONYMOUS\"/>"));
    }

    @Test
    @DisplayName("Auth.toXml 在 mechanism 为 null 时应抛出异常")
    void testAuthToXmlThrowsWhenMechanismIsNull() {
        Auth auth = new Auth(null, "dXNlcgBwYXNz");

        assertThrows(IllegalArgumentException.class, auth::toXml);
    }

    @Test
    @DisplayName("Auth.toXml 在 mechanism 为空白时应抛出异常")
    void testAuthToXmlThrowsWhenMechanismIsBlank() {
        Auth auth = new Auth("   ", "dXNlcgBwYXNz");

        assertThrows(IllegalArgumentException.class, auth::toXml);
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
        assertTrue(xml.contains("<response xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>"));
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
        assertEquals("test-content", challenge.content());
    }

    @Test
    @DisplayName("SaslChallenge 应暴露 ELEMENT 常量")
    void testSaslChallengeElementConstant() {
        assertEquals("challenge", SaslChallenge.ELEMENT);
        assertEquals(SaslChallenge.ELEMENT, new SaslChallenge(null).getElementName());
    }

    @Test
    @DisplayName("SaslSuccess 应暴露 ELEMENT 常量")
    void testSaslSuccessElementConstant() {
        assertEquals("success", SaslSuccess.ELEMENT);
        assertEquals(SaslSuccess.ELEMENT, new SaslSuccess(null).getElementName());
    }

    @Test
    @DisplayName("SaslFailure 应暴露 ELEMENT 常量")
    void testSaslFailureElementConstant() {
        assertEquals("failure", SaslFailure.ELEMENT);
        assertEquals(SaslFailure.ELEMENT, new SaslFailure(null, null).getElementName());
    }
}
