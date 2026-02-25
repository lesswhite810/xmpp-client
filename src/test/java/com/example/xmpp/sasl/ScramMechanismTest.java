package com.example.xmpp.sasl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SCRAM 机制单元测试。
 *
 * <p>测试 SCRAM 认证流程的各个阶段：</p>
 * <ul>
 *   <li>客户端首次消息生成</li>
 *   <li>挑战响应处理</li>
 *   <li>服务器签名验证</li>
 *   <li>错误处理</li>
 * </ul>
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
    @DisplayName("测试 SCRAM 机制有初始响应")
    void testHasInitialResponse() {
        assertTrue(mechanism.hasInitialResponse());
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
}
