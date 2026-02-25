package com.example.xmpp.sasl;

import com.example.xmpp.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * SASL SCRAM 机制的基础抽象实现。
 *
 * <p>实现 RFC 5802 定义的 SCRAM（Salted Challenge Response Authentication Mechanism）。
 * 支持 SCRAM-SHA-1、SCRAM-SHA-256、SCRAM-SHA-512 变体。</p>
 *
 * <p>认证流程：</p>
 * <ol>
 *   <li>发送 Client First Message（n,,n=user,r=clientNonce）</li>
 *   <li>接收 Server First Message（r=nonce,s=salt,i=iterations）</li>
 *   <li>计算 SaltedPassword、ClientKey、StoredKey、ClientSignature、ClientProof</li>
 *   <li>发送 Client Final Message（c=biws,r=nonce,p=clientProof）</li>
 *   <li>验证 Server Final Message（v=serverSignature）</li>
 * </ol>
 *
 * <h2>安全配置</h2>
 * <p>迭代次数安全性说明：</p>
 * <ul>
 *   <li>OWASP 2023 建议 PBKDF2-SHA256 至少 600,000 次迭代</li>
 *   <li>RFC 7677 建议至少 4,096 次迭代</li>
 *   <li>本实现使用 4,096 作为最小值以保持兼容性，但会对低于 OWASP 建议的情况发出警告</li>
 * </ul>
 *
 * @since 2026-02-09
 */
public abstract class ScramMechanism implements SaslMechanism {

    private static final Logger log = LoggerFactory.getLogger(ScramMechanism.class);

    /** SCRAM nonce 字节数（推荐至少与哈希输出长度相同） */
    private static final int NONCE_SIZE_BYTES = 32;

    /**
     * 最小迭代次数（RFC 7677 建议）。
     *
     * <p>设置为 4,096 以保持与大多数 XMPP 服务器的兼容性。如果服务器返回的迭代次数低于此值，认证将失败。</p>
     *
     * <p>注意：OWASP 2023 建议至少 600,000 次迭代。可通过系统属性 {@code xmpp.scram.minIterations=600000} 提高安全性。</p>
     */
    private static final int MIN_ITERATIONS = 4_096;

    /**
     * OWASP 2023 推荐的最小迭代次数（PBKDF2-SHA256）。
     *
     * <p>如果服务器返回的迭代次数低于此值，将发出警告日志。</p>
     */
    private static final int OWASP_RECOMMENDED_ITERATIONS = 600_000;

    /**
     * 实际使用的最小迭代次数，可通过系统属性配置。
     *
     * <p>系统属性：{@code xmpp.scram.minIterations}</p>
     * <p>默认值：4096（RFC 7677，兼容大多数服务器）</p>
     * <p>安全建议：600000（OWASP 2023 推荐）</p>
     */
    private static final int EFFECTIVE_MIN_ITERATIONS = Integer.getInteger(
            "xmpp.scram.minIterations", MIN_ITERATIONS);

    /**
     * 线程安全的 SecureRandom 实例，用于生成 SCRAM nonce。
     *
     * <p>SecureRandom 实例创建成本较高（涉及熵源初始化），复用实例可显著提升性能。SecureRandom 本身是线程安全的，可在多线程环境下并发调用。</p>
     *
     * <p>注意：虽然 SecureRandom 实例可复用，但在安全性要求极高的场景，可考虑使用阻塞模式（SecureRandom.getInstanceStrong()）。此处使用默认实现以平衡性能与安全性。</p>
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** SCRAM 认证状态枚举 */
    protected enum State {
        /** 初始状态 */
        INITIAL,
        /** 已接收挑战 */
        CHALLENGE_RECEIVED,
        /** 最终成功 */
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
     * @param password 密码（char[]）
     */
    protected ScramMechanism(String username, char[] password) {
        this.username = username;
        // 创建密码副本，原始密码由调用者负责清除
        this.password = password != null ? password.clone() : null;
        this.clientNonce = generateSecureNonce();
    }

    /**
     * 使用密码学安全的随机数生成器生成 nonce。
     *
     * <p>复用类级别的 SecureRandom 实例，避免重复初始化开销。SecureRandom 的线程安全性保证并发调用无冲突。</p>
     *
     * @return Base64 URL 安全编码的 nonce 字符串
     */
    private static String generateSecureNonce() {
        byte[] nonceBytes = new byte[NONCE_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(nonceBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    }

    /**
     * 获取 HMAC 算法名称。
     *
     * @return HMAC 算法名称（如 "HmacSHA256"）
     */
    protected abstract String getHmacAlgorithm();

    /**
     * 获取摘要算法名称。
     *
     * @return 摘要算法名称（如 "SHA-256"）
     */
    protected abstract String getDigestAlgorithm();

    /**
     * 获取 PBKDF2 算法名称。
     *
     * @return PBKDF2 算法名称（如 "PBKDF2WithHmacSHA256"）
     */
    protected abstract String getPBKDF2Algorithm();

    /**
     * 获取哈希值大小（字节）。
     *
     * @return 哈希值大小
     */
    protected abstract int hashSize();

    /**
     * 检查是否具有初始响应。
     *
     * @return SCRAM 机制始终返回 true
     */
    @Override
    public boolean hasInitialResponse() {
        return true;
    }

    /**
     * 处理服务器挑战并生成响应。
     *
     * @param challenge 服务器发送的挑战数据
     * @return 响应数据
     * @throws SaslException 如果处理失败
     */
    @Override
    public byte[] processChallenge(byte[] challenge) throws SaslException {
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
                return null;

            default:
                throw new SaslException("Invalid state");
        }
    }

    /**
     * 创建客户端初始消息（Client First Message）。
     *
     * @return 客户端初始消息字节数组
     */
    private byte[] createClientFirstMessage() {
        String gs2Header = "n,,";
        clientFirstMessageBare = "n=" + username + ",r=" + clientNonce;
        return (gs2Header + clientFirstMessageBare).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 创建客户端最终消息（Client Final Message）。
     *
     * @param serverFirstMessage 服务器首次消息
     * @return 客户端最终消息字节数组
     * @throws SaslException 如果创建失败
     */
    private byte[] createClientFinalMessage(String serverFirstMessage) throws SaslException {
        Map<Character, String> attributes = parseAttributes(serverFirstMessage);

        serverNonce = attributes.get('r');
        if (serverNonce == null || !serverNonce.startsWith(clientNonce)) {
            throw new SaslException("Invalid server nonce");
        }

        String saltBase64 = attributes.get('s');
        String iterationsStr = attributes.get('i');

        if (saltBase64 == null || iterationsStr == null) {
            throw new SaslException("Missing salt or iterations");
        }

        byte[] salt = Base64.getDecoder().decode(saltBase64);
        int iterations = Integer.parseInt(iterationsStr);

        // 验证迭代次数最小值，防止降级攻击
        if (iterations < EFFECTIVE_MIN_ITERATIONS) {
            throw new SaslException(
                    "Iterations too low: " + iterations + ", minimum is " + EFFECTIVE_MIN_ITERATIONS);
        }

        // 警告低于 OWASP 建议值的迭代次数
        if (iterations < OWASP_RECOMMENDED_ITERATIONS) {
            log.warn("Server SCRAM iterations ({}) below OWASP 2023 recommendation ({}). " +
                    "Consider updating server configuration for better security.",
                    iterations, OWASP_RECOMMENDED_ITERATIONS);
        }

        saltedPassword = hi(password, salt, iterations);

        String clientFinalMessageWithoutProof = "c=biws,r=" + serverNonce;

        authMessage = clientFirstMessageBare + "," + serverFirstMessage + ","
                + clientFinalMessageWithoutProof;

        byte[] clientKey = hmac(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8));
        byte[] storedKey = hash(clientKey);
        byte[] clientSignature = hmac(storedKey, authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] clientProof = xor(clientKey, clientSignature);

        state = State.FINAL_SUCCESS;
        return (clientFinalMessageWithoutProof + ",p="
                + Base64.getEncoder().encodeToString(clientProof)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 验证服务器最终消息（Server Final Message）。
     *
     * @param serverFinalMessage 服务器最终消息
     * @throws SaslException 如果验证失败
     */
    private void verifyServerFinalMessage(String serverFinalMessage) throws SaslException {
        Map<Character, String> attributes = parseAttributes(serverFinalMessage);
        if (attributes.containsKey('e')) {
            throw new SaslException("Server returned error: " + attributes.get('e'));
        }

        String verifierBase64 = attributes.get('v');
        if (verifierBase64 == null) {
            throw new SaslException("Missing verifier in server final message");
        }

        byte[] serverKey = hmac(saltedPassword, "Server Key".getBytes(StandardCharsets.UTF_8));
        byte[] serverSignature = hmac(serverKey, authMessage.getBytes(StandardCharsets.UTF_8));

        byte[] serverSignatureExpected = Base64.getDecoder().decode(verifierBase64);

        if (!MessageDigest.isEqual(serverSignature, serverSignatureExpected)) {
            // 验证失败也要清除敏感数据
            SecurityUtils.clear(saltedPassword);
            saltedPassword = null;
            throw new SaslException("Server signature verification failed");
        }

        // 验证成功，清除敏感密钥材料和密码副本
        SecurityUtils.clear(saltedPassword);
        saltedPassword = null;

        // 清除存储的密码副本，防止敏感数据在内存中驻留
        // 认证流程已结束，密码不再需要
        SecurityUtils.clear(password);
    }

    /**
     * 检查认证是否完成。
     *
     * @return 如果认证完成返回 true
     */
    @Override
    public boolean isComplete() {
        return state == State.FINAL_SUCCESS;
    }

    /**
     * 执行加密操作并统一处理异常（代码质量优化 - 提取公共异常处理）。
     *
     * @param operation 加密操作名称（用于错误消息）
     * @param supplier 要执行的操作
     * @return 操作结果
     * @throws SaslException 如果操作失败
     */
    private byte[] executeCryptoOperation(String operation, java.util.function.Supplier<byte[]> supplier)
            throws SaslException {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new SaslException(operation + " failed", e);
        }
    }

    /**
     * PBKDF2 密钥派生函数（HI 函数）。
     *
     * @param password 密码
     * @param salt 盐值
     * @param iterations 迭代次数
     * @return 派生的密钥
     * @throws SaslException 如果派生失败
     */
    private byte[] hi(char[] password, byte[] salt, int iterations) throws SaslException {
        return executeCryptoOperation("PBKDF2", () -> {
            try {
                PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, hashSize() * 8);
                javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory
                        .getInstance(getPBKDF2Algorithm());
                return skf.generateSecret(spec).getEncoded();
            } catch (java.security.NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * HMAC 计算函数。
     *
     * @param key 密钥
     * @param data 数据
     * @return HMAC 值
     * @throws SaslException 如果计算失败
     */
    private byte[] hmac(byte[] key, byte[] data) throws SaslException {
        return executeCryptoOperation("HMAC", () -> {
            try {
                Mac mac = Mac.getInstance(getHmacAlgorithm());
                mac.init(new SecretKeySpec(key, getHmacAlgorithm()));
                return mac.doFinal(data);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 哈希计算函数。
     *
     * @param data 数据
     * @return 哈希值
     * @throws SaslException 如果计算失败
     */
    private byte[] hash(byte[] data) throws SaslException {
        try {
            MessageDigest digest = MessageDigest.getInstance(getDigestAlgorithm());
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new SaslException("Hash failed: " + getDigestAlgorithm(), e);
        }
    }

    /**
     * XOR 字节数组。
     *
     * @param a 第一个字节数组
     * @param b 第二个字节数组
     * @return XOR 结果
     */
    private byte[] xor(byte[] a, byte[] b) {
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
     */
    private Map<Character, String> parseAttributes(String message) {
        Map<Character, String> attributes = new HashMap<>();
        String[] parts = message.split(",");
        for (String part : parts) {
            if (part.length() >= 2 && part.charAt(1) == '=') {
                attributes.put(part.charAt(0), part.substring(2));
            }
        }
        return attributes;
    }
}
