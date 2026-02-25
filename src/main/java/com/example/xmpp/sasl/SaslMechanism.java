package com.example.xmpp.sasl;

import javax.security.sasl.SaslException;

/**
 * SASL 认证机制接口。
 *
 * <p>定义了 SASL 认证过程中处理服务器挑战（Challenge）和生成响应（Response）的通用方法。
 * 所有具体的 SASL 机制实现（如 PLAIN、SCRAM-SHA-1 等）都必须实现此接口。</p>
 *
 * @since 2026-02-09
 */
public interface SaslMechanism {

    /**
     * 获取机制名称。
     *
     * @return 机制名称，如 "PLAIN"、"SCRAM-SHA-1"、"SCRAM-SHA-256"
     */
    String getMechanismName();

    /**
     * 检查是否具有初始响应数据。
     *
     * <p>对于 PLAIN 和 SCRAM 机制，由于需要发送 Client First Message，通常返回 true。</p>
     *
     * @return 如果有初始响应则返回 true
     */
    boolean hasInitialResponse();

    /**
     * 处理服务器的挑战数据并生成响应。
     *
     * <p>同时也用于生成初始响应（如果 challenge 为 null 或空）。</p>
     *
     * @param challenge 服务器发送的挑战数据（Base64 解码后），初次调用可能为 null
     * @return 响应数据（Base64 编码前）
     * @throws SaslException 如果认证过程出错
     */
    byte[] processChallenge(byte[] challenge) throws SaslException;

    /**
     * 检查认证是否完成。
     *
     * @return 如果认证完成返回 true
     */
    boolean isComplete();
}
