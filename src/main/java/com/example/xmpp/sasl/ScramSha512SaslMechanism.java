package com.example.xmpp.sasl;

import com.example.xmpp.XmppConstants;

/**
 * SCRAM-SHA-512 SASL 认证机制实现。
 *
 * <p>基于 RFC 7677 扩展实现的 SCRAM-SHA-512 机制，使用 SHA-512 哈希算法进行密码验证。
 * SHA-512 产生 512 位（64 字节）的哈希值，提供最高级别的安全性。</p>
 *
 * <p>推荐用于对安全性要求最高的场景。注意：并非所有 XMPP 服务器都支持此机制。</p>
 *
 * @since 2026-02-09
 */
public class ScramSha512SaslMechanism extends ScramMechanism {

    /**
     * 构造 SCRAM-SHA-512 认证机制实例。
     *
     * @param username 用户名
     * @param password 密码（char[]）
     */
    public ScramSha512SaslMechanism(String username, char[] password) {
        super(username, password);
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称 "SCRAM-SHA-512"
     */
    @Override
    public String getMechanismName() {
        return "SCRAM-SHA-512";
    }

    /**
     * 获取 HMAC 算法名称。
     *
     * @return HMAC 算法名称 "HmacSHA512"
     */
    @Override
    protected String getHmacAlgorithm() {
        return "HmacSHA512";
    }

    /**
     * 获取摘要算法名称。
     *
     * @return 摘要算法名称 "SHA-512"
     */
    @Override
    protected String getDigestAlgorithm() {
        return "SHA-512";
    }

    /**
     * 获取 PBKDF2 算法名称。
     *
     * @return PBKDF2 算法名称 "PBKDF2WithHmacSHA512"
     */
    @Override
    protected String getPBKDF2Algorithm() {
        return "PBKDF2WithHmacSHA512";
    }

    /**
     * 获取哈希值大小。
     *
     * @return SHA-512 哈希值大小（64 字节）
     */
    @Override
    protected int hashSize() {
        return XmppConstants.SHA512_HASH_SIZE_BYTES;
    }
}
