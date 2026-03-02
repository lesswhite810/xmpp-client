package com.example.xmpp.config;

import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.XmppConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;

/**
 * XMPP 客户端配置类（不可变）。
 *
 * 使用模块化配置类构建：
 * - ConnectionConfig：连接配置
 * - AuthConfig：认证配置
 * - SecurityConfig：安全配置
 * - KeepAliveConfig：心跳/重连配置
 *
 * @since 2026-02-09
 */
@Getter
@Builder
@FieldDefaults(makeFinal = true)
public class XmppClientConfig {

    /** 连接配置 */
    @Builder.Default
    private ConnectionConfig connection = ConnectionConfig.builder().build();

    /** 认证配置 */
    @Builder.Default
    private AuthConfig auth = AuthConfig.builder().build();

    /** 安全配置 */
    @Builder.Default
    private SecurityConfig security = SecurityConfig.builder().build();

    /** 心跳/保活配置 */
    @Builder.Default
    private KeepAliveConfig keepAlive = KeepAliveConfig.builder().build();

    /** 语言区域设置 */
    @Builder.Default
    private Locale language = Locale.getDefault();

    // ==================== 便捷方法 ====================

    /**
     * 安全获取密码（返回克隆副本）。
     *
     * @return 密码字符数组的克隆，如果未设置则返回 null
     */
    public char[] getPassword() {
        return auth != null ? auth.getPassword() : null;
    }

    /**
     * 清除内存中的密码。
     *
     * <p>安全地清除认证密码的内存副本，防止密码泄露。</p>
     */
    public void clearPassword() {
        if (auth != null && auth.getPassword() != null) {
            SecurityUtils.clear(auth.getPassword());
        }
    }

    /**
     * 获取 XML 语言标签。
     *
     * @return RFC 5646 语言标签，如果未设置则返回 null
     */
    public String getXmlLang() {
        if (language == null) return null;
        String tag = language.toLanguageTag();
        return "und".equals(tag) ? null : tag;
    }

    // ==================== Null 安全访问方法 ====================

    /**
     * 获取端口号，默认值 5222。
     *
     * @return XMPP 服务器端口号
     */
    public int getPort() {
        if (connection != null && connection.getPort() > 0) {
            return connection.getPort();
        }
        return XmppConstants.DEFAULT_XMPP_PORT;
    }

    /**
     * 获取连接超时，默认值 30000ms。
     *
     * @return 连接超时时间（毫秒）
     */
    public int getConnectTimeout() {
        if (connection != null && connection.getConnectTimeout() > 0) {
            return connection.getConnectTimeout();
        }
        return XmppConstants.DEFAULT_CONNECT_TIMEOUT_MS;
    }

    /**
     * 获取读取超时，默认值 60000ms。
     *
     * @return 读取超时时间（毫秒）
     */
    public int getReadTimeout() {
        if (connection != null && connection.getReadTimeout() > 0) {
            return connection.getReadTimeout();
        }
        return XmppConstants.DEFAULT_READ_TIMEOUT_MS;
    }

    /**
     * 是否发送在线状态，默认 true。
     *
     * @return 如果连接成功后发送 Presence 节则返回 true
     */
    public boolean isSendPresence() {
        return connection == null || connection.isSendPresence();
    }
}
