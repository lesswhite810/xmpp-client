package com.example.xmpp.sasl;

import com.example.xmpp.util.SecurityUtils;

import javax.security.sasl.SaslException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * SASL PLAIN 机制实现。
 *
 * <p>实现 RFC 4616 定义的 PLAIN 认证机制。
 * 格式：[authzid] \0 [authcid] \0 [passwd]</p>
 *
 * <p>注意：PLAIN 机制以明文传输凭据，必须在 TLS 加密通道中使用。</p>
 *
 * @since 2026-02-09
 */
public class PlainSaslMechanism implements SaslMechanism {

    private final String username;
    private char[] password;
    private boolean complete = false;

    /**
     * 构造 PLAIN SASL 认证机制实例。
     *
     * @param username 用户名
     * @param password 密码（char[]）
     */
    public PlainSaslMechanism(String username, char[] password) {
        this.username = username;
        this.password = password != null ? password.clone() : null;
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称 "PLAIN"
     */
    @Override
    public String getMechanismName() {
        return "PLAIN";
    }

    /**
     * 检查是否具有初始响应。
     *
     * @return PLAIN 机制始终返回 true
     */
    @Override
    public boolean hasInitialResponse() {
        return true;
    }

    /**
     * 处理 SASL 挑战并生成响应。
     *
     * <p>PLAIN 机制在初始响应中发送凭据，忽略后续挑战。</p>
     *
     * @param challenge 服务器挑战（PLAIN 机制不使用）
     * @return PLAIN 认证消息
     * @throws SaslException 如果认证已完成
     */
    @Override
    public byte[] processChallenge(byte[] challenge) throws SaslException {
        if (complete) {
            throw new SaslException("Authentication already completed.");
        }

        complete = true;

        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] passwordBytes = password != null ? SecurityUtils.toBytes(password) : new byte[0];

        ByteBuffer buffer = ByteBuffer.allocate(1 + usernameBytes.length + 1 + passwordBytes.length);
        buffer.put((byte) 0).put(usernameBytes).put((byte) 0).put(passwordBytes);

        SecurityUtils.clear(passwordBytes);
        SecurityUtils.clear(password);
        password = null;

        return buffer.array();
    }

    /**
     * 检查认证是否完成。
     *
     * @return 如果认证完成返回 true
     */
    @Override
    public boolean isComplete() {
        return complete;
    }
}
