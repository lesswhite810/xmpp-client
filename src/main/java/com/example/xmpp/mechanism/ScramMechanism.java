package com.example.xmpp.mechanism;

import com.example.xmpp.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SCRAM 机制基础实现。
 *
 * @since 2026-02-09
 */
@Slf4j
public abstract class ScramMechanism implements SaslMechanism {

    private static final byte[] EMPTY_RESPONSE = new byte[0];
    private static final String GS2_HEADER = "n,,";
    private static final String CLIENT_KEY_LABEL = "Client Key";
    private static final String SERVER_KEY_LABEL = "Server Key";
    private static final String CLIENT_FIRST_PREFIX = "n=";
    private static final String NONCE_PREFIX = ",r=";
    private static final String CHANNEL_BINDING_AND_NONCE_PREFIX = "c=biws,r=";
    private static final String PROOF_PREFIX = ",p=";
    private static final String PBKDF2_OPERATION = "PBKDF2";
    private static final String HMAC_OPERATION = "HMAC";
    private static final int ATTRIBUTE_MIN_LENGTH = 2;
    private static final int ATTRIBUTE_SEPARATOR_INDEX = 1;
    private static final int ATTRIBUTE_VALUE_INDEX = 2;

    /**
     * SCRAM nonce 字节数。
     */
    private static final int NONCE_SIZE_BYTES = 32;

    /**
     * 最小迭代次数。
     */
    private static final int MIN_ITERATIONS = 4_096;

    /**
     * OWASP 推荐的最小迭代次数。
     */
    private static final int OWASP_RECOMMENDED_ITERATIONS = 600_000;

    /**
     * 实际最小迭代次数。
     */
    private static final int EFFECTIVE_MIN_ITERATIONS = Integer.getInteger(
            "xmpp.scram.minIterations", MIN_ITERATIONS);

    /**
     * SecureRandom 实例。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * SCRAM 认证状态。
     */
    protected enum State {
        /**
         * 初始状态。
         */
        INITIAL,
        /**
         * 已接收挑战。
         */
        CHALLENGE_RECEIVED,
        /**
         * 最终成功。
         */
        FINAL_SUCCESS
    }

    protected final String username;
    protected final char[] password;

    protected State state = State.INITIAL;
    protected String clientNonce;
    protected String serverNonce;
    protected String clientFirstMessageBare;
    protected byte[] saltedPassword;
    protected String authMessage;

    /**
     * 构造 SCRAM 认证机制实例。
     *
     * @param username 用户名
     * @param password 密码
     */
    protected ScramMechanism(String username, char[] password) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.password = password != null ? password.clone() : null;
        this.clientNonce = generateSecureNonce();
    }

    /**
     * 生成 nonce。
     *
     * @return nonce
     */
    private static String generateSecureNonce() {
        byte[] nonceBytes = new byte[NONCE_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(nonceBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    }

    /**
     * 获取 HMAC 算法名称。
     *
     * @return HMAC 算法名称
     */
    protected abstract String getHmacAlgorithm();

    /**
     * 获取摘要算法名称。
     *
     * @return 摘要算法名称
     */
    protected abstract String getDigestAlgorithm();

    /**
     * 获取 PBKDF2 算法名称。
     *
     * @return PBKDF2 算法名称
     */
    protected abstract String getPBKDF2Algorithm();

    /**
     * 获取哈希值大小。
     *
     * @return 哈希值大小
     */
    protected abstract int hashSize();

    /**
     * 处理服务器挑战。
     *
     * @param challenge 服务器挑战数据
     * @return 响应数据
     * @throws SaslException 认证失败
     */
    @Override
    public byte[] processChallenge(byte[] challenge) throws SaslException {
        try {
            switch (state) {
                case INITIAL:
                    state = State.CHALLENGE_RECEIVED;
                    return createClientFirstMessage();

                case CHALLENGE_RECEIVED:
                    String serverFirstMessage = new String(challenge, StandardCharsets.UTF_8);
                    return createClientFinalMessage(serverFirstMessage);

                case FINAL_SUCCESS:
                    String serverFinalMessage = new String(challenge, StandardCharsets.UTF_8);
                    verifyServerFinalMessage(serverFinalMessage);
                    return EMPTY_RESPONSE;

                default:
                    throw new SaslException("Invalid state");
            }
        } catch (SaslException e) {
            log.warn("SCRAM {} authentication failed: {}", getMechanismName(), e.getMessage());
            clearSensitiveState();
            throw e;
        } catch (RuntimeException e) {
            log.error("SCRAM {} unexpected error during authentication.", getMechanismName(), e);
            clearSensitiveState();
            throw new SaslException("SCRAM processing failed", e);
        }
    }

    /**
     * 创建客户端初始消息。
     *
     * @return 初始消息字节数组
     */
    private byte[] createClientFirstMessage() {
        clientFirstMessageBare = CLIENT_FIRST_PREFIX + username + NONCE_PREFIX + clientNonce;
        return (GS2_HEADER + clientFirstMessageBare).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 创建客户端最终消息。
     *
     * @param serverFirstMessage 服务器首次消息
     * @return 最终消息字节数组
     * @throws SaslException 创建失败
     */
    private byte[] createClientFinalMessage(String serverFirstMessage) throws SaslException {
        Map<Character, String> attributes = parseAttributes(serverFirstMessage);

        serverNonce = attributes.get('r');
        if (serverNonce == null || !serverNonce.startsWith(clientNonce)) {
            log.warn("SCRAM {} server nonce validation failed.", getMechanismName());
            throw new SaslException("Invalid server nonce");
        }

        String saltBase64 = attributes.get('s');
        String iterationsStr = attributes.get('i');

        if (saltBase64 == null || iterationsStr == null) {
            log.warn("SCRAM {} server message missing required attributes (salt or iterations).", getMechanismName());
            throw new SaslException("Missing salt or iterations");
        }

        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(saltBase64);
        } catch (IllegalArgumentException e) {
            log.warn("SCRAM {} failed to decode salt (invalid Base64).", getMechanismName());
            throw new SaslException("Invalid SCRAM salt", e);
        }

        int iterations;
        try {
            iterations = Integer.parseInt(iterationsStr);
        } catch (NumberFormatException e) {
            log.warn("SCRAM {} failed to parse iterations value.", getMechanismName());
            throw new SaslException("Invalid SCRAM iterations", e);
        }

        if (iterations < EFFECTIVE_MIN_ITERATIONS) {
            log.warn("SCRAM {} iterations ({}) below minimum threshold ({}).",
                    getMechanismName(), iterations, EFFECTIVE_MIN_ITERATIONS);
            throw new SaslException(
                    "Iterations too low: " + iterations + ", minimum is " + EFFECTIVE_MIN_ITERATIONS);
        }

        if (iterations < OWASP_RECOMMENDED_ITERATIONS) {
            log.warn("Server SCRAM iterations ({}) below OWASP 2023 recommendation ({}). " +
                    "Consider updating server configuration for better security.",
                    iterations, OWASP_RECOMMENDED_ITERATIONS);
        }

        saltedPassword = hi(password, salt, iterations);

        String clientFinalMessageWithoutProof = CHANNEL_BINDING_AND_NONCE_PREFIX + serverNonce;

        authMessage = clientFirstMessageBare + "," + serverFirstMessage + ","
                + clientFinalMessageWithoutProof;

        byte[] clientKey = hmac(saltedPassword, CLIENT_KEY_LABEL.getBytes(StandardCharsets.UTF_8));
        byte[] storedKey = hash(clientKey);
        byte[] clientSignature = hmac(storedKey, authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] clientProof = xor(clientKey, clientSignature);

        state = State.FINAL_SUCCESS;
        return (clientFinalMessageWithoutProof + PROOF_PREFIX
                + Base64.getEncoder().encodeToString(clientProof)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 验证服务器最终消息。
     *
     * @param serverFinalMessage 服务器最终消息
     * @throws SaslException 验证失败
     */
    private void verifyServerFinalMessage(String serverFinalMessage) throws SaslException {
        Map<Character, String> attributes = parseAttributes(serverFinalMessage);
        if (attributes.containsKey('e')) {
            log.warn("SCRAM {} server returned error (type=e).", getMechanismName());
            throw new SaslException("Server returned error: " + attributes.get('e'));
        }

        String verifierBase64 = attributes.get('v');
        if (verifierBase64 == null) {
            log.warn("SCRAM {} server final message missing verifier attribute.", getMechanismName());
            throw new SaslException("Missing verifier in server final message");
        }

        byte[] serverKey = hmac(saltedPassword, SERVER_KEY_LABEL.getBytes(StandardCharsets.UTF_8));
        byte[] serverSignature = hmac(serverKey, authMessage.getBytes(StandardCharsets.UTF_8));

        byte[] serverSignatureExpected;
        try {
            serverSignatureExpected = Base64.getDecoder().decode(verifierBase64);
        } catch (IllegalArgumentException e) {
            log.warn("SCRAM {} failed to decode server verifier (invalid Base64).", getMechanismName());
            throw new SaslException("Invalid server verifier", e);
        }

        if (!MessageDigest.isEqual(serverSignature, serverSignatureExpected)) {
            log.error("SCRAM {} server signature verification failed.", getMechanismName());
            throw new SaslException("Server signature verification failed");
        }
        clearSensitiveState();
    }

    /**
     * 检查认证是否完成。
     *
     * @return 是否完成
     */
    @Override
    public boolean isComplete() {
        return state == State.FINAL_SUCCESS;
    }

    /**
     * 生成 PBKDF2 密钥。
     *
     * @param password 密码
     * @param salt 盐值
     * @param iterations 迭代次数
     * @return 派生密钥
     * @throws SaslException 生成失败
     */
    private byte[] hi(char[] password, byte[] salt, int iterations) throws SaslException {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, hashSize() * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(getPBKDF2Algorithm());
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("SCRAM {} key derivation failed: {}.", PBKDF2_OPERATION, e.getMessage());
            throw new SaslException(PBKDF2_OPERATION + " failed", e);
        }
    }

    /**
     * 计算 HMAC。
     *
     * @param key 密钥
     * @param data 数据
     * @return HMAC 值
     * @throws SaslException 计算失败
     */
    private byte[] hmac(byte[] key, byte[] data) throws SaslException {
        try {
            Mac mac = Mac.getInstance(getHmacAlgorithm());
            mac.init(new SecretKeySpec(key, getHmacAlgorithm()));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("SCRAM {} HMAC computation failed.", getMechanismName(), e);
            throw new SaslException(HMAC_OPERATION + " failed", e);
        }
    }

    /**
     * 计算哈希。
     *
     * @param data 数据
     * @return 哈希值
     * @throws SaslException 计算失败
     */
    private byte[] hash(byte[] data) throws SaslException {
        try {
            MessageDigest digest = MessageDigest.getInstance(getDigestAlgorithm());
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            log.error("SCRAM {} hash computation failed for algorithm '{}'.", getMechanismName(), getDigestAlgorithm(), e);
            throw new SaslException("Hash failed: " + getDigestAlgorithm(), e);
        }
    }

    /**
     * XOR 字节数组。
     *
     * @param a 第一个字节数组
     * @param b 第二个字节数组
     * @return XOR 结果
     * @throws SaslException 长度不一致时抛出
     */
    private byte[] xor(byte[] a, byte[] b) throws SaslException {
        if (a.length != b.length) {
            log.error("SCRAM {} XOR operand length mismatch (a={}, b={}).",
                    getMechanismName(), a.length, b.length);
            throw new SaslException("SCRAM XOR operands must have the same length");
        }
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /**
     * 解析 SCRAM 消息属性。
     *
     * @param message SCRAM 消息
     * @return 属性映射表
     * @throws SaslException 属性非法时抛出
     */
    private Map<Character, String> parseAttributes(String message) throws SaslException {
        Map<Character, String> attributes = new HashMap<>();
        String[] parts = message.split(",");
        for (String part : parts) {
            if (part.length() >= ATTRIBUTE_MIN_LENGTH && part.charAt(ATTRIBUTE_SEPARATOR_INDEX) == '=') {
                char attributeName = part.charAt(0);
                if (attributes.containsKey(attributeName)) {
                    log.warn("SCRAM {} duplicate attribute '{}' in server message.", getMechanismName(), attributeName);
                    throw new SaslException("Duplicate SCRAM attribute: " + attributeName);
                }
                attributes.put(attributeName, part.substring(ATTRIBUTE_VALUE_INDEX));
            }
        }
        return attributes;
    }

    private void clearSensitiveState() {
        SecurityUtils.clear(saltedPassword);
        saltedPassword = null;
        SecurityUtils.clear(password);
    }
}
