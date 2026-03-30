package com.example.xmpp.mechanism;

import com.example.xmpp.mechanism.ScramSha256SaslMechanism;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SCRAM 机制单元测试。
 *
 * @since 2026-02-15
 */
class ScramMechanismTest {

    private static final String TEST_USERNAME = "user";
    private static final char[] TEST_PASSWORD = "pencil".toCharArray();

    private ScramSha256SaslMechanism mechanism;

    @BeforeEach
    void setUp() {
        mechanism = new ScramSha256SaslMechanism(TEST_USERNAME, TEST_PASSWORD);
    }

    @Test
    @DisplayName("测试机制名称")
    void testMechanismName() {
        assertEquals("SCRAM-SHA-256", mechanism.getMechanismName());
    }

    @Test
    @DisplayName("测试初始状态未完成")
    void testInitialNotComplete() {
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("测试客户端首次消息格式")
    void testClientFirstMessageFormat() throws SaslException {
        byte[] response = mechanism.processChallenge(null);
        String message = new String(response, StandardCharsets.UTF_8);

        // 格式应该是: n,,n=user,r=<nonce>
        assertTrue(message.startsWith("n,,"), "GS2 header should be 'n,,'");

        Pattern pattern = Pattern.compile("^n,,n=([^,]+),r=([A-Za-z0-9_-]+)$");
        Matcher matcher = pattern.matcher(message);

        assertTrue(matcher.matches(), "Client first message format should match pattern");
        assertEquals(TEST_USERNAME, matcher.group(1), "Username should match");
        assertFalse(matcher.group(2).isEmpty(), "Nonce should not be empty");
    }

    @Test
    @DisplayName("测试客户端首次消息包含正确的用户名")
    void testClientFirstMessageContainsUsername() throws SaslException {
        byte[] response = mechanism.processChallenge(null);
        String message = new String(response, StandardCharsets.UTF_8);

        assertTrue(message.contains("n=" + TEST_USERNAME),
                "Message should contain username");
    }

    @Test
    @DisplayName("测试完整认证流程")
    void testFullAuthenticationFlow() throws SaslException {
        // 步骤 1: 生成客户端首次消息
        byte[] clientFirst = mechanism.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        // 提取客户端 nonce
        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        assertTrue(matcher.find());
        String clientNonce = matcher.group(1);

        // 步骤 2: 模拟服务器首次消息
        // 格式: r=<combined_nonce>,s=<salt>,i=<iterations>
        String serverNonce = clientNonce + "ServerNonce";
        String salt = Base64.getEncoder().encodeToString("salt".getBytes(StandardCharsets.UTF_8));
        int iterations = 4096;
        String serverFirstMessage = String.format("r=%s,s=%s,i=%d",
                serverNonce, salt, iterations);

        byte[] clientFinal = mechanism.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8));
        String clientFinalMsg = new String(clientFinal, StandardCharsets.UTF_8);

        // 验证客户端最终消息格式
        assertTrue(clientFinalMsg.startsWith("c=biws,"), "Should have correct channel binding");
        assertTrue(clientFinalMsg.contains("r=" + serverNonce), "Should contain server nonce");
        assertTrue(clientFinalMsg.contains(",p="), "Should contain proof");

        // 在 CHALLENGE_RECEIVED 状态处理完挑战后，状态变为 FINAL_SUCCESS
        // 此时 isComplete() 返回 true
        assertTrue(mechanism.isComplete(), "Should be complete after processing challenge");
    }

    @Test
    @DisplayName("测试无效服务器 nonce 应抛出异常")
    void testInvalidServerNonceThrowsException() throws SaslException {
        // 首先获取客户端首次消息
        mechanism.processChallenge(null);

        // 服务器返回不包含客户端 nonce 的响应
        String invalidServerMessage = "r=invalidServerNonce,s=cmFuZG9tc2FsdA==,i=4096";

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(invalidServerMessage.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Invalid server nonce"),
                "Exception message should mention invalid nonce");
    }

    @Test
    @DisplayName("测试缺少 salt 应抛出异常")
    void testMissingSaltThrowsException() throws SaslException {
        mechanism.processChallenge(null);

        // 缺少 salt 的服务器消息
        String invalidServerMessage = "r=someNonce,i=4096";

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(invalidServerMessage.getBytes(StandardCharsets.UTF_8)));

        // 检查是否抛出了 SaslException（可能因为 null salt 导致 NPE 或其他错误）
        assertNotNull(exception, "Should throw SaslException for missing salt");
    }

    @Test
    @DisplayName("测试缺少迭代次数应抛出异常")
    void testMissingIterationsThrowsException() throws SaslException {
        mechanism.processChallenge(null);

        // 缺少迭代次数的服务器消息
        String invalidServerMessage = "r=someNonce,s=cmFuZG9tc2FsdA==";

        assertThrows(SaslException.class,
                () -> mechanism.processChallenge(invalidServerMessage.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("测试迭代次数过低应抛出异常")
    void testLowIterationsThrowsException() throws SaslException {
        // 获取客户端首次消息
        String clientFirst = new String(mechanism.processChallenge(null), StandardCharsets.UTF_8);
        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirst);
        matcher.find();
        String clientNonce = matcher.group(1);

        // 使用过低的迭代次数（低于 4096）
        String serverMessage = String.format("r=%s,s=cmFuZG9tc2FsdA==,i=1000", clientNonce + "Server");

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(serverMessage.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Iterations too low"),
                "Exception message should mention low iterations");
    }

    @Test
    @DisplayName("测试服务器错误响应")
    void testServerErrorResponse() throws SaslException {
        // 完成首次消息和挑战响应
        byte[] clientFirst = mechanism.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        matcher.find();
        String clientNonce = matcher.group(1);

        String serverNonce = clientNonce + "ServerNonce";
        String serverFirstMessage = String.format("r=%s,s=cmFuZG9tc2FsdA==,i=4096", serverNonce);
        mechanism.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8));

        // 服务器返回错误
        String serverFinalMessage = "e=authentication-failed";

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(serverFinalMessage.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Server returned error"),
                "Exception message should mention server error");
    }

    @Test
    @DisplayName("测试缺少验证器应抛出异常")
    void testMissingVerifierThrowsException() throws SaslException {
        byte[] clientFirst = mechanism.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        matcher.find();
        String clientNonce = matcher.group(1);

        String serverNonce = clientNonce + "ServerNonce";
        String serverFirstMessage = String.format("r=%s,s=cmFuZG9tc2FsdA==,i=4096", serverNonce);
        mechanism.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8));

        // 缺少验证器的服务器最终消息
        String serverFinalMessage = "x=invalid";

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(serverFinalMessage.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Missing verifier"),
                "Exception message should mention missing verifier");
    }

    @Test
    @DisplayName("测试非法 verifier Base64 应抛出异常")
    void testInvalidVerifierBase64ThrowsException() throws SaslException {
        byte[] clientFirst = mechanism.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        matcher.find();
        String clientNonce = matcher.group(1);

        String serverNonce = clientNonce + "ServerNonce";
        String serverFirstMessage = String.format("r=%s,s=cmFuZG9tc2FsdA==,i=4096", serverNonce);
        mechanism.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8));

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge("v=%%%".getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Invalid server verifier"));
    }

    @Test
    @DisplayName("重复 SCRAM 属性应抛出异常并清理密码")
    void testDuplicateScramAttributeThrowsExceptionAndClearsPassword() throws Exception {
        mechanism.processChallenge(null);
        char[] internalPassword = readPasswordField(mechanism);

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge("r=nonce,r=dup,s=cmFuZG9tc2FsdA==,i=4096".getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Duplicate SCRAM attribute"));
        assertNotNull(readPasswordField(mechanism));
        assertArrayEquals(new char[] {'\0', '\0', '\0', '\0', '\0', '\0'}, internalPassword);
    }

    @Test
    @DisplayName("非法 salt 应抛出异常并清理密码")
    void testInvalidSaltThrowsExceptionAndClearsPassword() throws Exception {
        String clientFirst = new String(mechanism.processChallenge(null), StandardCharsets.UTF_8);
        char[] internalPassword = readPasswordField(mechanism);
        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirst);
        matcher.find();
        String clientNonce = matcher.group(1);

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(
                        ("r=" + clientNonce + "Server,s=%%%,i=4096")
                                .getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Invalid SCRAM salt"));
        assertNotNull(readPasswordField(mechanism));
        assertArrayEquals(new char[] {'\0', '\0', '\0', '\0', '\0', '\0'}, internalPassword);
    }

    @Test
    @DisplayName("测试无效状态调用应抛出异常")
    void testInvalidStateThrowsException() throws SaslException {
        // 完成整个流程
        byte[] clientFirst = mechanism.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        matcher.find();
        String clientNonce = matcher.group(1);

        String serverNonce = clientNonce + "ServerNonce";
        String serverFirstMessage = String.format("r=%s,s=cmFuZG9tc2FsdA==,i=4096", serverNonce);
        mechanism.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8));

        // 提供错误的服务器签名会导致验证失败
        String serverFinalMessage = "v=aW52YWxpZF9zaWduYXR1cmU="; // Invalid signature

        assertThrows(SaslException.class,
                () -> mechanism.processChallenge(serverFinalMessage.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("XOR 操作数长度不一致应抛出异常")
    void testXorRejectsDifferentLengthInputs() throws Exception {
        Method method = ScramMechanism.class.getDeclaredMethod("xor", byte[].class, byte[].class);
        method.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> method.invoke(mechanism, new byte[] {1, 2}, new byte[] {1}));

        assertInstanceOf(SaslException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("same length"));
    }

    @Test
    @DisplayName("测试服务器最终消息校验完成后应返回空字节数组而不是 null")
    void testServerFinalVerificationReturnsEmptyBytes() throws SaslException {
        byte[] clientFirst = mechanism.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        matcher.find();
        String clientNonce = matcher.group(1);

        String serverNonce = clientNonce + "ServerNonce";
        String salt = Base64.getEncoder().encodeToString("salt".getBytes(StandardCharsets.UTF_8));
        String serverFirstMessage = String.format("r=%s,s=%s,i=%d", serverNonce, salt, 4096);
        String clientFinalMessage = new String(
                mechanism.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        String authMessage = clientFirstMsg.substring(3) + "," + serverFirstMessage + ","
                + clientFinalMessage.substring(0, clientFinalMessage.indexOf(",p="));
        byte[] saltedPassword = pbkdf2(TEST_PASSWORD, "salt".getBytes(StandardCharsets.UTF_8), 4096);
        byte[] serverKey = hmac(saltedPassword, "HmacSHA256", "Server Key".getBytes(StandardCharsets.UTF_8));
        byte[] serverSignature = hmac(serverKey, "HmacSHA256", authMessage.getBytes(StandardCharsets.UTF_8));
        String serverFinalMessage = "v=" + Base64.getEncoder().encodeToString(serverSignature);

        byte[] result = mechanism.processChallenge(serverFinalMessage.getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertEquals(0, result.length);
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws SaslException {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new SaslException("Failed to derive PBKDF2 key", e);
        }
    }

    private byte[] hmac(byte[] key, String algorithm, byte[] data) throws SaslException {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SaslException("Failed to compute HMAC", e);
        }
    }

    private char[] readPasswordField(ScramMechanism mechanism) throws Exception {
        Field field = ScramMechanism.class.getDeclaredField("password");
        field.setAccessible(true);
        return (char[]) field.get(mechanism);
    }

    /**
     * Test subclass that returns an invalid digest algorithm to trigger exception handling.
     */
    @Test
    @DisplayName("Constructor with null password should not throw")
    void testConstructorWithNullPassword() {
        ScramSha256SaslMechanism mechanismWithNull = new ScramSha256SaslMechanism(TEST_USERNAME, null);
        assertNotNull(mechanismWithNull);
        assertFalse(mechanismWithNull.isComplete());
    }

    @Test
    @DisplayName("Iterations below OWASP recommendation should log warning but not throw")
    void testOwaspWarningBranch() throws SaslException {
        // Use iterations between 4096 (EFFECTIVE_MIN_ITERATIONS) and 600000 (OWASP_RECOMMENDED_ITERATIONS)
        // This should trigger the OWASP warning log but not throw
        String clientFirst = new String(mechanism.processChallenge(null), StandardCharsets.UTF_8);
        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirst);
        matcher.find();
        String clientNonce = matcher.group(1);

        // Use 10000 iterations - between MIN (4096) and OWASP (600000)
        String serverNonce = clientNonce + "ServerNonce";
        String salt = Base64.getEncoder().encodeToString("salt".getBytes(StandardCharsets.UTF_8));
        int iterations = 10000; // Valid but below OWASP recommendation
        String serverFirstMessage = String.format("r=%s,s=%s,i=%d", serverNonce, salt, iterations);

        // Should NOT throw - just log warning
        byte[] clientFinal = mechanism.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8));
        assertNotNull(clientFinal);
        assertTrue(mechanism.isComplete());
    }

    /**
     * Test subclass with invalid digest algorithm to trigger hash() catch block.
     */
    private static class ScramMechanismHashFails extends ScramSha256SaslMechanism {
        ScramMechanismHashFails() {
            super("user", "password".toCharArray());
        }

        @Override
        protected String getDigestAlgorithm() {
            return "INVALID-DIGEST-ALGORITHM";
        }
    }

    /**
     * Test subclass with invalid HMAC algorithm to trigger hmac() catch block.
     */
    private static class ScramMechanismHmacFails extends ScramSha256SaslMechanism {
        ScramMechanismHmacFails() {
            super("user", "password".toCharArray());
        }

        @Override
        protected String getHmacAlgorithm() {
            return "INVALID-HMAC-ALGORITHM";
        }
    }

    /**
     * Test subclass with invalid PBKDF2 algorithm to trigger hi() catch block.
     */
    private static class ScramMechanismHiFails extends ScramSha256SaslMechanism {
        ScramMechanismHiFails() {
            super("user", "password".toCharArray());
        }

        @Override
        protected String getPBKDF2Algorithm() {
            return "INVALID-PBKDF2-ALGORITHM";
        }
    }

    @Test
    @DisplayName("hash() should catch NoSuchAlgorithmException and throw SaslException")
    void testHashCatchesNoSuchAlgorithmException() throws SaslException {
        ScramMechanismHashFails mechanismFails = new ScramMechanismHashFails();
        byte[] clientFirst = mechanismFails.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        assertTrue(matcher.find());
        String clientNonce = matcher.group(1);

        String serverNonce = clientNonce + "ServerNonce";
        String serverFirstMessage = String.format("r=%s,s=cmFuZG9tc2FsdA==,i=4096", serverNonce);

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanismFails.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Hash failed"));
    }

    @Test
    @DisplayName("hmac() should catch NoSuchAlgorithmException and throw SaslException")
    void testHmacCatchesNoSuchAlgorithmException() throws SaslException {
        ScramMechanismHmacFails mechanismFails = new ScramMechanismHmacFails();
        byte[] clientFirst = mechanismFails.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        assertTrue(matcher.find());
        String clientNonce = matcher.group(1);

        String serverNonce = clientNonce + "ServerNonce";
        String serverFirstMessage = String.format("r=%s,s=cmFuZG9tc2FsdA==,i=4096", serverNonce);

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanismFails.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("HMAC failed"));
    }

    @Test
    @DisplayName("hi() should catch NoSuchAlgorithmException and throw SaslException")
    void testHiCatchesNoSuchAlgorithmException() throws SaslException {
        ScramMechanismHiFails mechanismFails = new ScramMechanismHiFails();
        byte[] clientFirst = mechanismFails.processChallenge(null);
        String clientFirstMsg = new String(clientFirst, StandardCharsets.UTF_8);

        Pattern noncePattern = Pattern.compile("r=([A-Za-z0-9_-]+)");
        Matcher matcher = noncePattern.matcher(clientFirstMsg);
        assertTrue(matcher.find());
        String clientNonce = matcher.group(1);

        String serverNonce = clientNonce + "ServerNonce";
        String serverFirstMessage = String.format("r=%s,s=cmFuZG9tc2FsdA==,i=4096", serverNonce);

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanismFails.processChallenge(serverFirstMessage.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("PBKDF2 failed"));
    }

    @Test
    @DisplayName("createClientFinalMessage with missing nonce should throw SaslException")
    void testCreateClientFinalMessageWithMissingNonce() throws SaslException {
        mechanism.processChallenge(null);

        String serverFirstNoNonce = "s=cmFuZG9tc2FsdA==,i=4096";

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(serverFirstNoNonce.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Invalid server nonce"));
    }

    @Test
    @DisplayName("createClientFinalMessage with null iterations should throw SaslException")
    void testCreateClientFinalMessageWithNullIterations() throws Exception {
        mechanism.processChallenge(null);

        // Get the actual clientNonce via reflection so server nonce starts with it
        Field clientNonceField = ScramMechanism.class.getDeclaredField("clientNonce");
        clientNonceField.setAccessible(true);
        String clientNonce = (String) clientNonceField.get(mechanism);

        // Server nonce must start with clientNonce (validated at line 210)
        String serverNonce = clientNonce + "Server";
        String serverFirstNoIterations = "r=" + serverNonce + ",s=cmFuZG9tc2FsdA==";

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(serverFirstNoIterations.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Missing salt or iterations"));
    }

    @Test
    @DisplayName("createClientFinalMessage with non-numeric iterations should throw SaslException")
    void testCreateClientFinalMessageWithNonNumericIterations() throws Exception {
        mechanism.processChallenge(null);

        // Get the actual clientNonce via reflection so server nonce starts with it
        Field clientNonceField = ScramMechanism.class.getDeclaredField("clientNonce");
        clientNonceField.setAccessible(true);
        String clientNonce = (String) clientNonceField.get(mechanism);

        // Server nonce must start with clientNonce (validated at line 210)
        String serverNonce = clientNonce + "Server";
        String serverFirstWithNonNumericIterations = "r=" + serverNonce + ",s=cmFuZG9tc2FsdA==,i=abc";

        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(serverFirstWithNonNumericIterations.getBytes(StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("Invalid SCRAM iterations"));
    }

    @Test
    @DisplayName("parseAttributes should skip parts with missing equals sign")
    void testParseAttributesSkipsPartWithMissingEquals() throws Exception {
        Method parseAttributes = ScramMechanism.class.getDeclaredMethod("parseAttributes", String.class);
        parseAttributes.setAccessible(true);

        Map<Character, String> result = (Map<Character, String>) parseAttributes.invoke(mechanism, "r,s=salt,i=4096");

        assertEquals(2, result.size());
        assertEquals("salt", result.get('s'));
        assertEquals("4096", result.get('i'));
        assertFalse(result.containsKey('r'));
    }

    @Test
    @DisplayName("parseAttributes should skip parts with length less than 2")
    void testParseAttributesSkipsShortParts() throws Exception {
        Method parseAttributes = ScramMechanism.class.getDeclaredMethod("parseAttributes", String.class);
        parseAttributes.setAccessible(true);

        // "r,,s=salt" contains an empty string part (length 0) between the two commas
        Map<Character, String> result = (Map<Character, String>) parseAttributes.invoke(mechanism, "r,,s=salt,i=4096");

        assertEquals(2, result.size());
        assertEquals("salt", result.get('s'));
        assertEquals("4096", result.get('i'));
        assertFalse(result.containsKey('r'));
    }

    @Test
    @DisplayName("processChallenge should catch RuntimeException thrown in CHALLENGE_RECEIVED state")
    void testProcessChallengeCatchesRuntimeException() throws SaslException {
        ScramMechanismXorFails mechanismFails = new ScramMechanismXorFails();

        mechanismFails.processChallenge(null);

        assertThrows(SaslException.class,
                () -> mechanismFails.processChallenge("r=nonce,s=salt,i=4096".getBytes(StandardCharsets.UTF_8)));
    }

    // ========================================
    // Additional targeted coverage tests
    // ========================================

    /**
     * Covers line 174: default case branch and RuntimeException handler.
     * With state=null, the switch throws NPE which is caught as RuntimeException
     * and rethrown as SaslException("SCRAM processing failed").
     * The default: throw new SaslException("Invalid state") is unreachable through
     * normal enum values since all State enum constants are handled in cases.
     * 
     * @since 2026-03-30
     */
    @Test
    @DisplayName("processChallenge with null state should throw SaslException")
    void testProcessChallengeWithNullStateThrowsSaslException() throws Exception {
        // Set state to null via reflection - this causes switch(state) to throw NPE
        // which is caught by catch (RuntimeException e) and rethrown as SaslException
        Field stateField = ScramMechanism.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(mechanism, null);
        
        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(null));
        
        assertNotNull(exception.getMessage());
    }

    /**
     * Covers line 218: saltBase64 == null && iterationsStr != null branch.
     * Server message has i= but no s= attribute.
     * 
     * @since 2026-03-30
     */
    @Test
    @DisplayName("createClientFinalMessage with null salt but valid iterations should throw SaslException")
    void testCreateClientFinalMessageWithNullSalt() throws Exception {
        mechanism.processChallenge(null);
        
        Field clientNonceField = ScramMechanism.class.getDeclaredField("clientNonce");
        clientNonceField.setAccessible(true);
        String clientNonce = (String) clientNonceField.get(mechanism);
        
        // Server nonce must start with clientNonce (validated at line 210)
        String serverNonce = clientNonce + "Server";
        // Server message has i= (iterations) but no s= (salt)
        String serverFirstNoSalt = "r=" + serverNonce + ",i=4096";
        
        SaslException exception = assertThrows(SaslException.class,
                () -> mechanism.processChallenge(serverFirstNoSalt.getBytes(StandardCharsets.UTF_8)));
        
        assertTrue(exception.getMessage().contains("Missing salt or iterations"));
    }

    /**
     * Covers line 246: iterations >= OWASP_RECOMMENDED_ITERATIONS else branch.
     * Server message has iterations >= 600000 so the OWASP warning is NOT logged.
     * 
     * @since 2026-03-30
     */
    @Test
    @DisplayName("createClientFinalMessage with high iterations should not trigger OWASP warning branch")
    void testCreateClientFinalMessageWithHighIterations() throws Exception {
        mechanism.processChallenge(null);
        
        Field clientNonceField = ScramMechanism.class.getDeclaredField("clientNonce");
        clientNonceField.setAccessible(true);
        String clientNonce = (String) clientNonceField.get(mechanism);
        
        String serverNonce = clientNonce + "Server";
        // Use iterations >= 600000 (OWASP_RECOMMENDED_ITERATIONS = 600000)
        // This covers the else branch of: if (iterations < OWASP_RECOMMENDED_ITERATIONS)
        String serverFirstHighIterations = "r=" + serverNonce + ",s=cmFuZG9tc2FsdA==,i=600000";
        
        // Should NOT throw - just takes the else branch (no warning logged)
        byte[] result = mechanism.processChallenge(serverFirstHighIterations.getBytes(StandardCharsets.UTF_8));
        
        assertNotNull(result);
    }

    /**
     * Covers line 407: part.length() >= 2 but part.charAt(1) != '=' branch.
     * Message contains attribute "aX" (2+ chars, second char is not '=').
     * 
     * @since 2026-03-30
     */
    @Test
    @DisplayName("parseAttributes should skip parts where second char is not equals sign")
    void testParseAttributesSkipsPartWithInvalidSecondChar() throws Exception {
        Method parseAttributes = ScramMechanism.class.getDeclaredMethod("parseAttributes", String.class);
        parseAttributes.setAccessible(true);
        
        // "aXvalue" looks like an attribute (2+ chars) but second char is 'X' not '='
        // Should be skipped entirely
        Map<Character, String> result = (Map<Character, String>) parseAttributes.invoke(
                mechanism, "r=abc123,aXvalue,s=salt,i=4096");
        
        // Only r, s, i should be parsed; 'a' attribute should NOT be added
        assertEquals(3, result.size());
        assertEquals("abc123", result.get('r'));
        assertEquals("salt", result.get('s'));
        assertEquals("4096", result.get('i'));
        assertFalse(result.containsKey('a'), "Attribute 'a' should not be present due to invalid second char");
    }

    private static class ScramMechanismXorFails extends ScramSha256SaslMechanism {
        ScramMechanismXorFails() {
            super("user", "password".toCharArray());
        }

        @Override
        protected String getHmacAlgorithm() {
            throw new RuntimeException("HMAC failure for test");
        }
    }

}
