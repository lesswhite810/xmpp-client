package com.example.xmpp.mechanism;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuthBearerSaslMechanism 单元测试。
 */
class OAuthBearerSaslMechanismTest {

    @Test
    @DisplayName("构造函数 username 为 null 应抛出 NullPointerException")
    void testConstructorRejectsNullUsername() {
        assertThrows(NullPointerException.class,
                () -> new OAuthBearerSaslMechanism(null, "token".toCharArray(), null));
    }

    @Test
    @DisplayName("构造函数 username 正常，token 为 null 应正确设置")
    void testConstructorNullToken() {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism("user@example.com", null, null);

        assertEquals("OAUTHBEARER", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("构造函数 username 和 token 正常应正确设置")
    void testConstructorWithUsernameAndToken() {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", "ya29.xxx".toCharArray(), null);

        assertEquals("OAUTHBEARER", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("构造函数 authzid 正常应正确设置")
    void testConstructorWithAuthzid() {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", "ya29.xxx".toCharArray(), "admin@example.com");

        assertEquals("OAUTHBEARER", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 完整参数应返回正确编码")
    void testProcessChallengeFullParams() throws SaslException {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", "ya29.xxx".toCharArray(), "admin@example.com");

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        byte[] decoded = Base64.getDecoder().decode(response);
        ByteBuffer buffer = ByteBuffer.wrap(decoded);

        byte[] authzidBytes = readUntilNul(buffer);
        byte[] authcidBytes = readUntilNul(buffer);
        byte[] tokenBytes = readRemaining(buffer);

        assertEquals("admin@example.com", new String(authzidBytes, StandardCharsets.UTF_8));
        assertEquals("user@example.com", new String(authcidBytes, StandardCharsets.UTF_8));
        assertEquals("ya29.xxx", new String(tokenBytes, StandardCharsets.UTF_8));
        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 无 authzid 应返回正确编码")
    void testProcessChallengeWithoutAuthzid() throws SaslException {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", "ya29.xxx".toCharArray(), null);

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        byte[] decoded = Base64.getDecoder().decode(response);
        ByteBuffer buffer = ByteBuffer.wrap(decoded);

        byte[] authzidBytes = readUntilNul(buffer);
        byte[] authcidBytes = readUntilNul(buffer);
        byte[] tokenBytes = readRemaining(buffer);

        assertEquals(0, authzidBytes.length);
        assertEquals("user@example.com", new String(authcidBytes, StandardCharsets.UTF_8));
        assertEquals("ya29.xxx", new String(tokenBytes, StandardCharsets.UTF_8));
        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge null token 应返回正确编码")
    void testProcessChallengeNullToken() throws SaslException {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", null, null);

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        byte[] decoded = Base64.getDecoder().decode(response);
        ByteBuffer buffer = ByteBuffer.wrap(decoded);

        byte[] authzidBytes = readUntilNul(buffer);
        byte[] authcidBytes = readUntilNul(buffer);
        byte[] tokenBytes = readRemaining(buffer);

        assertEquals(0, authzidBytes.length);
        assertEquals("user@example.com", new String(authcidBytes, StandardCharsets.UTF_8));
        assertEquals(0, tokenBytes.length);
        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 完成后再次调用应抛出 SaslException")
    void testProcessChallengeAfterComplete() throws Exception {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", "token".toCharArray(), null);

        mechanism.processChallenge(new byte[0]);

        assertThrows(SaslException.class, () -> mechanism.processChallenge(new byte[0]));
    }

    @Test
    @DisplayName("isComplete 初始应返回 false")
    void testIsCompleteInitial() {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", "token".toCharArray(), null);

        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("isComplete 处理挑战后应返回 true")
    void testIsCompleteAfterChallenge() throws SaslException {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", "token".toCharArray(), null);

        mechanism.processChallenge(new byte[0]);

        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 应清理 token 字节")
    void testProcessChallengeClearsTokenBytes() throws Exception {
        OAuthBearerSaslMechanism mechanism = new OAuthBearerSaslMechanism(
                "user@example.com", "secret-token".toCharArray(), null);

        mechanism.processChallenge(new byte[0]);

        char[] clearedToken = readTokenField(mechanism);
        assertNull(clearedToken);
    }

    private byte[] readUntilNul(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        int i = 0;
        while (buffer.hasRemaining() && buffer.get(buffer.position()) != 0) {
            result[i++] = buffer.get();
        }
        if (buffer.hasRemaining()) {
            buffer.get();
        }
        return Arrays.copyOf(result, i);
    }

    private byte[] readRemaining(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        int i = 0;
        while (buffer.hasRemaining()) {
            result[i++] = buffer.get();
        }
        return Arrays.copyOf(result, i);
    }

    private char[] readTokenField(OAuthBearerSaslMechanism mechanism) throws Exception {
        Field field = OAuthBearerSaslMechanism.class.getDeclaredField("token");
        field.setAccessible(true);
        return (char[]) field.get(mechanism);
    }
}
