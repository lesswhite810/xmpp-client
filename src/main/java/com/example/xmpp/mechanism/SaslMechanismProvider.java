package com.example.xmpp.mechanism;

/**
 * SASL 机制提供者接口。
 *
 * @since 2026-02-09
 */
public interface SaslMechanismProvider {

    /**
     * 获取机制名称。
     *
     * @return 机制名称
     */
    String getMechanismName();

    /**
     * 获取优先级。
     *
     * @return 优先级
     */
    int getPriority();

    /**
     * 创建 SASL 机制实例。
     *
     * @param username 用户名
     * @param password 密码
     * @return SASL 机制实例
     */
    SaslMechanism create(String username, char[] password);
}
