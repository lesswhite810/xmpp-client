package com.example.xmpp.mechanism;

import com.example.xmpp.util.XmppConstants;
import lombok.extern.slf4j.Slf4j;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * SASL ANONYMOUS 机制实现。
 *
 * <p>ANONYMOUS 机制允许用户在不提供凭据的情况下进行身份验证，
 * 使用可选的跟踪信息（trace）来标识请求来源。跟踪信息是 Base64 编码的
 * UTF-8 字符串，通常用于审计或调试目的。</p>
 *
 * <p>RFC 4505 - SASL ANONYMOUS mechanism</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public class AnonymousSaslMechanism implements SaslMechanism {

    private boolean complete = false;

    /**
     * 构造 ANONYMOUS SASL 认证机制实例。
     */
    public AnonymousSaslMechanism() {
        // 无需凭据
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称
     */
    @Override
    public String getMechanismName() {
        return XmppConstants.SASL_MECH_ANONYMOUS;
    }

    /**
     * 处理 SASL 挑战并生成响应。
     *
     * <p>ANONYMOUS 机制是单轮机制，只需发送一次响应。
     * 响应内容为 Base64 编码的跟踪信息（trace），可以包含任意 UTF-8 字符串。
     * 常见实现使用随机 UUID 作为跟踪标识。</p>
     *
     * @param challenge 服务器挑战（ANONYMOUS 不使用，通常为空）
     * @return 认证消息（Base64 编码的 trace）
     * @throws SaslException 认证已完成时抛出
     */
    @Override
    public byte[] processChallenge(byte[] challenge) throws SaslException {
        if (complete) {
            log.warn("SASL ANONYMOUS authentication attempt after completion.");
            throw new SaslException("Authentication already completed.");
        }

        complete = true;

        // 使用随机 UUID 作为跟踪标识
        String trace = UUID.randomUUID().toString();
        byte[] traceBytes = trace.getBytes(StandardCharsets.UTF_8);
        try {
            return Base64.getEncoder().encode(traceBytes);
        } finally {
            Arrays.fill(traceBytes, (byte) 0);
        }
    }

    /**
     * 检查认证是否完成。
     *
     * @return 是否完成
     */
    @Override
    public boolean isComplete() {
        return complete;
    }
}
