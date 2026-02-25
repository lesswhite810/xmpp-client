package com.example.xmpp.sasl;

import com.example.xmpp.XmppConstants;

/**
 * SCRAM-SHA-1 SASL 认证机制实现。
 *
 * <p>基于 RFC 5802 实现的 SCRAM-SHA-1 机制，使用 SHA-1 哈希算法进行密码验证。
 * SHA-1 产生 160 位（20 字节）的哈希值。</p>
 *
 * <p>虽然 SHA-1 已不推荐用于新系统，但为了兼容旧版 XMPP 服务器仍需支持。
 * 建议优先使用 ScramSha256SaslMechanism 或 ScramSha512SaslMechanism。</p>
 *
 * @since 2026-02-09
 */
public class ScramSha1SaslMechanism extends ScramMechanism {

    /**
     * 构造 SCRAM-SHA-1 认证机制实例。
     *
     * @param username 用户名
     * @param password 密码（char[]）
     */
    public ScramSha1SaslMechanism(String username, char[] password) {
        super(username, password);
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称 "SCRAM-SHA-1"
     */
    @Override
    public String getMechanismName() {
        return "SCRAM-SHA-1";
    }

    /**
     * 获取 HMAC 算法名称。
     *
     * @return HMAC 算法名称 "HmacSHA1"
     */
    @Override
    protected String getHmacAlgorithm() {
        return "HmacSHA1";
    }

    /**
     * 获取摘要算法名称。
     *
     * @return 摘要算法名称 "SHA-1"
     */
    @Override
    protected String getDigestAlgorithm() {
        return "SHA-1";
    }

    /**
     * 获取 PBKDF2 算法名称。
     *
     * @return PBKDF2 算法名称 "PBKDF2WithHmacSHA1"
     */
    @Override
    protected String getPBKDF2Algorithm() {
        return "PBKDF2WithHmacSHA1";
    }

    /**
     * 获取哈希值大小。
     *
     * @return SHA-1 哈希值大小（20 字节）
     */
    @Override
    protected int hashSize() {
        return XmppConstants.SHA1_HASH_SIZE_BYTES;
    }
}
