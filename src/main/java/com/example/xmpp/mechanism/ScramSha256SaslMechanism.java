package com.example.xmpp.mechanism;

import com.example.xmpp.util.XmppConstants;

/**
 * SCRAM-SHA-256 SASL 认证机制实现。
 *
 * @since 2026-02-09
 */
public class ScramSha256SaslMechanism extends ScramMechanism {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * 构造 SCRAM-SHA-256 认证机制实例。
     *
     * @param username 用户名
     * @param password 密码
     */
    public ScramSha256SaslMechanism(String username, char[] password) {
        super(username, password);
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称
     */
    @Override
    public String getMechanismName() {
        return XmppConstants.SASL_MECH_SCRAM_SHA256;
    }

    /**
     * 获取 HMAC 算法名称。
     *
     * @return HMAC 算法名称
     */
    @Override
    protected String getHmacAlgorithm() {
        return HMAC_ALGORITHM;
    }

    /**
     * 获取摘要算法名称。
     *
     * @return 摘要算法名称
     */
    @Override
    protected String getDigestAlgorithm() {
        return DIGEST_ALGORITHM;
    }

    /**
     * 获取 PBKDF2 算法名称。
     *
     * @return PBKDF2 算法名称
     */
    @Override
    protected String getPBKDF2Algorithm() {
        return PBKDF2_ALGORITHM;
    }

    /**
     * 获取哈希值大小。
     *
     * @return 哈希值大小
     */
    @Override
    protected int hashSize() {
        return XmppConstants.SHA256_HASH_SIZE_BYTES;
    }
}
