package com.example.xmpp.mechanism;

import com.example.xmpp.util.XmppConstants;
import lombok.extern.slf4j.Slf4j;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * SASL EXTERNAL 机制实现。
 *
 * <p>EXTERNAL 机制允许客户端使用外部验证渠道（如 TLS 客户端证书）进行身份验证，
 * 或者显式声明授权身份（authzid）。服务器通常在 TLS 握手后自动提取客户端证书
 * 信息，客户端只需发送 Base64 编码的 authzid（可选，通常为空或与认证身份相同）。</p>
 *
 * <p>RFC 4422 - SASL EXTERNAL mechanism</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public class ExternalSaslMechanism implements SaslMechanism {

    private byte[] authorizationIdentityBytes;
    private boolean complete = false;

    /**
     * 构造 EXTERNAL SASL 认证机制实例。
     *
     * @param authorizationIdentity 授权身份（可选，通常为 null 或空字符串表示使用认证身份）
     */
    public ExternalSaslMechanism(String authorizationIdentity) {
        this.authorizationIdentityBytes = authorizationIdentity != null 
            ? authorizationIdentity.getBytes(StandardCharsets.UTF_8) 
            : null;
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称
     */
    @Override
    public String getMechanismName() {
        return XmppConstants.SASL_MECH_EXTERNAL;
    }

    /**
     * 处理 SASL 挑战并生成响应。
     *
     * <p>EXTERNAL 机制是单轮机制，只需发送一次响应。
     * 响应内容为 Base64 编码的 authzid（授权身份）。
     * 如果 authzid 为空或 null，响应为 "="（表示空授权）。</p>
     *
     * @param challenge 服务器挑战（EXTERNAL 不使用，通常为空）
     * @return 认证消息（Base64 编码的 authzid）
     * @throws SaslException 认证已完成时抛出
     */
    @Override
    public byte[] processChallenge(byte[] challenge) throws SaslException {
        if (complete) {
            log.warn("SASL EXTERNAL authentication attempt after completion.");
            throw new SaslException("Authentication already completed.");
        }

        complete = true;

        try {
            byte[] responseBytes;
            if (authorizationIdentityBytes != null && authorizationIdentityBytes.length > 0) {
                responseBytes = authorizationIdentityBytes;
            } else {
                responseBytes = "=".getBytes(StandardCharsets.UTF_8);
            }
            return Base64.getEncoder().encode(responseBytes);
        } finally {
            if (authorizationIdentityBytes != null) {
                Arrays.fill(authorizationIdentityBytes, (byte) 0);
                authorizationIdentityBytes = null;
            }
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
