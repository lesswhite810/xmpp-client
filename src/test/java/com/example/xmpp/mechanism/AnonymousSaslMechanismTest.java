package com.example.xmpp.mechanism;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnonymousSaslMechanism 单元测试。
 */
class AnonymousSaslMechanismTest {

    @Test
    @DisplayName("无参构造函数应正确创建实例")
    void testNoArgConstructor() {
        AnonymousSaslMechanism mechanism = new AnonymousSaslMechanism();

        assertEquals("ANONYMOUS", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("getMechanismName 应返回 ANONYMOUS")
    void testGetMechanismName() {
        AnonymousSaslMechanism mechanism = new AnonymousSaslMechanism();

        assertEquals("ANONYMOUS", mechanism.getMechanismName());
    }

    @Test
    @DisplayName("processChallenge 应返回有效 Base64 编码的 UUID")
    void testProcessChallengeReturnsValidBase64Uuid() throws SaslException {
        AnonymousSaslMechanism mechanism = new AnonymousSaslMechanism();

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        assertTrue(response.length > 0);
        String responseStr = new String(response, StandardCharsets.UTF_8);
        assertFalse(responseStr.isBlank());
        byte[] decoded = Base64.getDecoder().decode(responseStr);
        String decodedStr = new String(decoded, StandardCharsets.UTF_8);
        assertFalse(decodedStr.isBlank());
    }

    @Test
    @DisplayName("processChallenge 应标记认证为完成")
    void testProcessChallengeMarksComplete() throws SaslException {
        AnonymousSaslMechanism mechanism = new AnonymousSaslMechanism();

        mechanism.processChallenge(new byte[0]);

        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 完成后再次调用应抛出 SaslException")
    void testProcessChallengeAfterComplete() throws Exception {
        AnonymousSaslMechanism mechanism = new AnonymousSaslMechanism();

        mechanism.processChallenge(new byte[0]);

        assertThrows(SaslException.class, () -> mechanism.processChallenge(new byte[0]));
    }

    @Test
    @DisplayName("isComplete 初始应返回 false")
    void testIsCompleteInitial() {
        AnonymousSaslMechanism mechanism = new AnonymousSaslMechanism();

        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("isComplete 处理挑战后应返回 true")
    void testIsCompleteAfterChallenge() throws SaslException {
        AnonymousSaslMechanism mechanism = new AnonymousSaslMechanism();

        mechanism.processChallenge(new byte[0]);

        assertTrue(mechanism.isComplete());
    }
}
