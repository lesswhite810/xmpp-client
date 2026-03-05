package com.example.xmpp.config;

import lombok.Builder;
import lombok.Getter;

/**
 * 认证配置。
 *
 * <p>包含 XMPP 客户端身份验证所需的所有凭据信息。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class AuthConfig {

    /**
     * 用户名。
     *
     * <p>XMPP 用户的本地部分（不包含域名）。例如：{@code john}</p>
     */
    private String username;

    /**
     * 密码。
     *
     * <p>用户密码。注意：此字段为字符数组，用于安全地存储和清除密码。</p>
     */
    private char[] password;

    /**
     * 授权标识符（authzid）。
     *
     * <p>用于 SASL 认证的授权身份。
     * 如果未设置，默认使用用户名作为授权身份。
     * 格式：{@code user@domain} 或仅 {@code user}</p>
     */
    private String authzid;

    /**
     * 安全获取密码（返回克隆副本）。
     *
     * <p>返回密码的克隆副本以保护原始数据不被修改。
     * 调用者使用完毕后应清除返回的字符数组。</p>
     *
     * @return 密码字符数组的克隆，如果未设置则返回 null
     */
    public char[] getPassword() {
        return password != null ? password.clone() : null;
    }
}
