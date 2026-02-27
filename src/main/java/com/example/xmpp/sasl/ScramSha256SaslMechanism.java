package com.example.xmpp.sasl;

import com.example.xmpp.util.XmppConstants;

/**
 * SCRAM-SHA-256 SASL 认证机制实现。
 *
 * <p>基于 RFC 7677 实现的 SCRAM-SHA-256 机制，使用 SHA-256 哈希算法进行密码验证。
 * SHA-256 产生 256 位（32 字节）的哈希值，提供比 SHA-1 更强的安全性。</p>
 *
 * <p>推荐用于需要较高安全性的场景，是 SCRAM-SHA-1 的安全替代方案。</p>
 *
 * @since 2026-02-09
 */
public class ScramSha256SaslMechanism extends ScramMechanism {

    /**
     * 构造 SCRAM-SHA-256 认证机制实例。
     *
     * @param username 用户名
     * @param password 密码（char[]）
     */
    public ScramSha256SaslMechanism(String username, char[] password) {
        super(username, password);
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称 "SCRAM-SHA-256"
     */
    @Override
    public String getMechanismName() {
        return "SCRAM-SHA-256";
    }

    /**
     * 获取 HMAC 算法名称。
     *
     * @return HMAC 算法名称 "HmacSHA256"
     */
    @Override
    protected String getHmacAlgorithm() {
        return "HmacSHA256";
    }

    /**
     * 获取摘要算法名称。
     *
     * @return 摘要算法名称 "SHA-256"
     */
    @Override
    protected String getDigestAlgorithm() {
        return "SHA-256";
    }

    /**
     * 获取 PBKDF2 算法名称。
     *
     * @return PBKDF2 算法名称 "PBKDF2WithHmacSHA256"
     */
    @Override
    protected String getPBKDF2Algorithm() {
        return "PBKDF2WithHmacSHA256";
    }

    /**
     * 获取哈希值大小。
     *
     * @return SHA-256 哈希值大小（32 字节）
     */
    @Override
    protected int hashSize() {
        return XmppConstants.SHA256_HASH_SIZE_BYTES;
    }
}
