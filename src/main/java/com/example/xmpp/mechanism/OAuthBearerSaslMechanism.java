package com.example.xmpp.mechanism;

import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.XmppConstants;
import lombok.extern.slf4j.Slf4j;

import javax.security.sasl.SaslException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * SASL OAUTHBEARER 机制实现。
 *
 * <p>OAUTHBEER 机制使用 OAuth 2.0 令牌进行身份验证，兼容 Google、Microsoft 等
 * 第三方 OAuth 提供商。客户端发送 Base64 编码的 JSON 令牌，格式遵循 RFC 7628。</p>
 *
 * <p>消息格式（编码前）：authzid \0 authcid \0 token</p>
 * <p>其中 authzid（授权身份）和 token（OAuth 令牌）为可选项。</p>
 *
 * <p>RFC 7628 - SASL OAUTHBEARER mechanism</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public class OAuthBearerSaslMechanism implements SaslMechanism {

    private static final byte NUL_BYTE = 0;

    /**
     * OAuth 令牌（必需）。
     * 对于 Google OAuth，此字段包含 Bearer token。
     */
    private char[] token;

    /**
     * 授权身份（可选，authzid）。
     * 表示代表另一个用户行事的能力。
     */
    private final String authorizationIdentity;

    /**
     * 认证身份（必需，authcid）。
     * 通常是用户的电子邮件地址或账户名。
     */
    private final String username;

    /**
     * 是否已完成认证。
     */
    private boolean complete = false;

    /**
     * 构造 OAUTHBEARER SASL 认证机制实例。
     *
     * @param username      认证身份（authcid）
     * @param token         OAuth 令牌
     * @param authorizationIdentity 授权身份（可选，可为 null）
     */
    public OAuthBearerSaslMechanism(String username, char[] token, String authorizationIdentity) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.token = token != null ? token.clone() : null;
        this.authorizationIdentity = authorizationIdentity;
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称
     */
    @Override
    public String getMechanismName() {
        return XmppConstants.SASL_MECH_OAUTHBEARER;
    }

    /**
     * 处理 SASL 挑战并生成响应。
     *
     * <p>OAUTHBEARER 机制是单轮机制，只需发送一次响应。
     * 响应内容为 Base64 编码的 JSON 对象（RFC 7628 定义的二进制格式）：</p>
     * <pre>
     * {
     *   "authzid": "...",
     *   "authcid": "...",
     *   "token": "..."
     * }
     * </pre>
     *
     * @param challenge 服务器挑战（OAUTHBEARER 不使用，通常为空）
     * @return 认证消息（Base64 编码的令牌数据）
     * @throws SaslException 认证已完成时抛出
     */
    @Override
    public byte[] processChallenge(byte[] challenge) throws SaslException {
        if (complete) {
            log.warn("SASL OAUTHBEARER authentication attempt after completion.");
            throw new SaslException("Authentication already completed.");
        }

        complete = true;

        try {
            byte[] tokenBytes = this.token != null
                    ? SecurityUtils.toBytes(this.token)
                    : new byte[0];

            byte[] authzidBytes = authorizationIdentity != null
                    ? authorizationIdentity.getBytes(StandardCharsets.UTF_8)
                    : new byte[0];

            byte[] authcidBytes = username.getBytes(StandardCharsets.UTF_8);

            ByteBuffer buffer = ByteBuffer.allocate(
                    authzidBytes.length + 1 + authcidBytes.length + 1 + tokenBytes.length);
            buffer.put(authzidBytes).put(NUL_BYTE);
            buffer.put(authcidBytes).put(NUL_BYTE);
            buffer.put(tokenBytes);

            byte[] message = buffer.array();

            // 清理中间字节
            Arrays.fill(tokenBytes, (byte) 0);
            Arrays.fill(authzidBytes, (byte) 0);
            Arrays.fill(authcidBytes, (byte) 0);

            return Base64.getEncoder().encode(message);
        } finally {
            if (this.token != null) {
                SecurityUtils.clear(this.token);
                this.token = null;
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
