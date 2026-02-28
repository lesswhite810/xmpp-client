package com.example.xmpp.config;

import lombok.Builder;
import lombok.Getter;

/**
 * 认证配置。
 */
@Getter
@Builder
public class AuthConfig {

    /** 用户名 */
    private String username;

    /** 密码 */
    private char[] password;

    /** 授权标识符（authzid） */
    private String authzid;

    /**
     * 安全获取密码（返回克隆副本）。
     */
    public char[] getPassword() {
        return password != null ? password.clone() : null;
    }
}
