package com.example.xmpp.mechanism;

import javax.security.sasl.SaslException;

/**
 * SASL 认证机制接口。
 *
 * @since 2026-02-09
 */
public interface SaslMechanism {

    /**
     * 获取机制名称。
     *
     * @return 机制名称
     */
    String getMechanismName();

    /**
     * 处理服务器挑战并生成响应。
     *
     * @param challenge 服务器挑战数据
     * @return 响应数据
     * @throws SaslException 认证失败
     */
    byte[] processChallenge(byte[] challenge) throws SaslException;

    /**
     * 检查认证是否完成。
     *
     * @return 是否完成
     */
    boolean isComplete();
}
