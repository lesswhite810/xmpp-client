package com.example.xmpp.sasl;

/**
 * SASL 机制提供者接口（SPI）。
 *
 * <p>实现此接口并在 META-INF/services/com.example.xmpp.sasl.SaslMechanismProvider 中注册，
 * 即可自动扩展 SASL 机制。SaslMechanismFactory 会通过 Java ServiceLoader 自动发现并加载。</p>
 *
 * @since 2026-02-09
 */
public interface SaslMechanismProvider {

    /**
     * 获取机制名称。
     *
     * @return 机制名称（如 "SCRAM-SHA-1"）
     */
    String getMechanismName();

    /**
     * 获取优先级。
     *
     * <p>数值越大优先级越高，用于决定多个机制可用时的选择顺序。</p>
     *
     * @return 优先级数值
     */
    int getPriority();

    /**
     * 创建 SASL 机制实例。
     *
     * @param username 用户名
     * @param password 密码（char[]）
     * @return 创建的 SaslMechanism 实例
     */
    SaslMechanism create(String username, char[] password);
}
