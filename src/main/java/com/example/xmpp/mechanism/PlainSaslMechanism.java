package com.example.xmpp.mechanism;

import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.XmppConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.security.sasl.SaslException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * SASL PLAIN 机制实现。
 *
 * @since 2026-02-09
 */
@Slf4j
public class PlainSaslMechanism implements SaslMechanism {

    private static final byte NUL_BYTE = 0;
    private static final byte[] EMPTY_PASSWORD_BYTES = new byte[0];

    private final String username;
    private char[] password;
    private boolean complete = false;

    /**
     * 构造 PLAIN SASL 认证机制实例。
     *
     * @param username 用户名
     * @param password 密码
     */
    public PlainSaslMechanism(String username, char[] password) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.password = password != null ? password.clone() : null;
    }

    /**
     * 获取机制名称。
     *
     * @return 机制名称
     */
    @Override
    public String getMechanismName() {
        return XmppConstants.SASL_MECH_PLAIN;
    }

    /**
     * 处理 SASL 挑战并生成响应。
     *
     * @param challenge 服务器挑战
     * @return 认证消息
     * @throws SaslException 认证已完成时抛出
     */
    @Override
    public byte[] processChallenge(byte[] challenge) throws SaslException {
        if (complete) {
            log.warn("SASL PLAIN authentication attempt after completion.");
            throw new SaslException("Authentication already completed.");
        }

        if (StringUtils.isBlank(username)) {
            log.warn("SASL PLAIN authentication rejected: username is blank.");
            SecurityUtils.clear(password);
            password = null;
            throw new SaslException("Username cannot be blank");
        }

        complete = true;

        byte[] passwordBytes = password != null ? SecurityUtils.toBytes(password) : EMPTY_PASSWORD_BYTES;
        try {
            byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(1 + usernameBytes.length + 1 + passwordBytes.length);
            buffer.put(NUL_BYTE).put(usernameBytes).put(NUL_BYTE).put(passwordBytes);
            return buffer.array();
        } finally {
            if (passwordBytes != EMPTY_PASSWORD_BYTES) {
                SecurityUtils.clear(passwordBytes);
            }
            SecurityUtils.clear(password);
            password = null;
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
