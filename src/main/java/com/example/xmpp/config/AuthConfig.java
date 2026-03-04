package com.example.xmpp.config;

import lombok.Builder;
import lombok.Getter;

/**
 * 认证配置。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class AuthConfig {

    /** 用户名 *
 * @since 2026-02-09
 */
    private String username;

    /** 密码 *
 * @since 2026-02-09
 */
    private char[] password;

    /** 授权标识符（authzid） *
 * @since 2026-02-09
 */
    private String authzid;

    /**
     * 安全获取密码（返回克隆副本）。
     *
 * @since 2026-02-09
 */
    public char[] getPassword() {
        return password != null ? password.clone() : null;
    }
}
