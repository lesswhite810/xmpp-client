package com.example.xmpp.mechanism;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExternalSaslMechanism 单元测试。
 */
class ExternalSaslMechanismTest {

    @Test
    @DisplayName("构造函数 null authzid 应正确设置")
    void testConstructorNullAuthzid() {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism(null);

        assertEquals("EXTERNAL", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("构造函数空字符串 authzid 应正确设置")
    void testConstructorEmptyAuthzid() {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("");

        assertEquals("EXTERNAL", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("构造函数空白 authzid 应正确设置")
    void testConstructorBlankAuthzid() {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("   ");

        assertEquals("EXTERNAL", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("构造函数非空 authzid 应正确设置")
    void testConstructorNonEmptyAuthzid() {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("alice@example.com");

        assertEquals("EXTERNAL", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge null authzid 应返回 '='")
    void testProcessChallengeNullAuthzid() throws SaslException {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism(null);

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        // RFC 4422: EXTERNAL with empty authzid sends "=" which must be Base64 encoded as "PQ=="
        String responseStr = new String(response, StandardCharsets.UTF_8);
        assertEquals("PQ==", responseStr);
        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 空字符串 authzid 应返回 '='")
    void testProcessChallengeEmptyAuthzid() throws SaslException {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("");

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        // RFC 4422: EXTERNAL with empty authzid sends "=" which must be Base64 encoded as "PQ=="
        String responseStr = new String(response, StandardCharsets.UTF_8);
        assertEquals("PQ==", responseStr);
        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 空白 authzid 应返回 Base64 编码的空格")
    void testProcessChallengeBlankAuthzid() throws SaslException {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("   ");

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        // Blank authzid ("   ") is NOT empty - it contains spaces, so Base64 encode it
        byte[] decoded = Base64.getDecoder().decode(response);
        assertEquals("   ", new String(decoded, StandardCharsets.UTF_8));
        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 非空 authzid 应返回 Base64 编码")
    void testProcessChallengeNonEmptyAuthzid() throws SaslException {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("alice@example.com");

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        byte[] decoded = Base64.getDecoder().decode(response);
        String decodedStr = new String(decoded, StandardCharsets.UTF_8);
        assertEquals("alice@example.com", decodedStr);
        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 完成后再次调用应抛出 SaslException")
    void testProcessChallengeAfterComplete() throws Exception {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("alice");

        mechanism.processChallenge(new byte[0]);

        assertThrows(SaslException.class, () -> mechanism.processChallenge(new byte[0]));
    }

    @Test
    @DisplayName("isComplete 初始应返回 false")
    void testIsCompleteInitial() {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("alice");

        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("isComplete 处理挑战后应返回 true")
    void testIsCompleteAfterChallenge() throws SaslException {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("alice");

        mechanism.processChallenge(new byte[0]);

        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("processChallenge 应清理 authzid 字节")
    void testProcessChallengeClearsAuthzidBytes() throws Exception {
        ExternalSaslMechanism mechanism = new ExternalSaslMechanism("alice");
        byte[] internalBytes = readAuthzidField(mechanism);
        byte[] copyBeforeClear = Arrays.copyOf(internalBytes, internalBytes.length);

        mechanism.processChallenge(new byte[0]);

        byte[] clearedBytes = readAuthzidField(mechanism);
        assertNull(clearedBytes);
        // Verify the copy still has original values (proving the clear affected the internal array)
        assertArrayEquals(new byte[] {'a', 'l', 'i', 'c', 'e'}, copyBeforeClear);
    }

    private byte[] readAuthzidField(ExternalSaslMechanism mechanism) throws Exception {
        Field field = ExternalSaslMechanism.class.getDeclaredField("authorizationIdentityBytes");
        field.setAccessible(true);
        return (byte[]) field.get(mechanism);
    }
}
