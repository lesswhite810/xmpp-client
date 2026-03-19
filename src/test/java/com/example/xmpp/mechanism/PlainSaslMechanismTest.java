package com.example.xmpp.mechanism;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlainSaslMechanism 单元测试。
 */
class PlainSaslMechanismTest {

    @Test
    @DisplayName("PlainSaslMechanism 构造函数应正确设置用户名和密码")
    void testConstructor() {
        PlainSaslMechanism mechanism = new PlainSaslMechanism("testuser", "password".toCharArray());

        assertEquals("PLAIN", mechanism.getMechanismName());
        assertTrue(mechanism.hasInitialResponse());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("PlainSaslMechanism 构造函数应处理 null 密码")
    void testConstructorNullPassword() {
        PlainSaslMechanism mechanism = new PlainSaslMechanism("testuser", null);

        assertEquals("PLAIN", mechanism.getMechanismName());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("PlainSaslMechanism.processChallenge 应生成正确的响应")
    void testProcessChallenge() throws Exception {
        PlainSaslMechanism mechanism = new PlainSaslMechanism("testuser", "password".toCharArray());

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        assertTrue(mechanism.isComplete());
        // PLAIN 格式: \0username\0password
        String responseStr = new String(response, "UTF-8");
        assertTrue(responseStr.contains("testuser"));
        assertTrue(responseStr.contains("password"));
    }

    @Test
    @DisplayName("PlainSaslMechanism.processChallenge null 密码应生成正确响应")
    void testProcessChallengeNullPassword() throws Exception {
        PlainSaslMechanism mechanism = new PlainSaslMechanism("testuser", null);

        byte[] response = mechanism.processChallenge(new byte[0]);

        assertNotNull(response);
        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("PlainSaslMechanism.processChallenge 完成后再次调用应抛出异常")
    void testProcessChallengeAfterComplete() throws Exception {
        PlainSaslMechanism mechanism = new PlainSaslMechanism("testuser", "password".toCharArray());

        mechanism.processChallenge(new byte[0]);

        assertThrows(SaslException.class, () ->
                mechanism.processChallenge(new byte[0]));
    }

    @Test
    @DisplayName("PlainSaslMechanism.isComplete 初始应返回 false")
    void testIsCompleteInitial() {
        PlainSaslMechanism mechanism = new PlainSaslMechanism("testuser", "password".toCharArray());

        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("PlainSaslMechanism.isComplete 处理挑战后应返回 true")
    void testIsCompleteAfterChallenge() throws Exception {
        PlainSaslMechanism mechanism = new PlainSaslMechanism("testuser", "password".toCharArray());

        mechanism.processChallenge(new byte[0]);

        assertTrue(mechanism.isComplete());
    }

    @Test
    @DisplayName("PlainSaslMechanism 用户名为 null 应抛出异常")
    void testConstructorRejectsNullUsername() {
        assertThrows(NullPointerException.class, () -> new PlainSaslMechanism(null, "password".toCharArray()));
    }

    @Test
    @DisplayName("PlainSaslMechanism 空用户名应抛出异常并清理密码")
    void testBlankUsernameClearsPasswordOnFailure() throws Exception {
        PlainSaslMechanism mechanism = new PlainSaslMechanism("   ", "password".toCharArray());
        char[] internalPassword = readPasswordField(mechanism);

        assertThrows(SaslException.class, () -> mechanism.processChallenge(new byte[0]));

        assertNull(readPasswordField(mechanism));
        assertArrayEquals(new char[] {'\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0'}, internalPassword);
    }

    private char[] readPasswordField(PlainSaslMechanism mechanism) throws Exception {
        Field field = PlainSaslMechanism.class.getDeclaredField("password");
        field.setAccessible(true);
        return (char[]) field.get(mechanism);
    }
}
