package com.example.xmpp.config;

/**
 * 系统配置服务接口。
 *
 * <p>用于从外部 Bean 或配置中心按键获取 XMPP 客户端配置值。</p>
 *
 * @since 2026-03-11
 */
public interface SystemService {

    /**
     * 根据配置键获取配置值。
     *
     * @param value 配置键
     * @return 配置值；如果不存在则返回 {@code null}
     */
    String getValue(String value);
}