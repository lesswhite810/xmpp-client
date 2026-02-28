package com.example.xmpp.config;

import com.example.xmpp.util.XmppConstants;
import lombok.Builder;
import lombok.Getter;

/**
 * 心跳/保活 配置。
 */
@Getter
@Builder
public class KeepAliveConfig {

    /** 是否启用自动重连 */
    @Builder.Default
    private boolean reconnectionEnabled = false;

    /** 重连基础延迟（秒） */
    @Builder.Default
    private int reconnectionBaseDelay = XmppConstants.RECONNECT_BASE_DELAY_SECONDS;

    /** 重连最大延迟（秒） */
    @Builder.Default
    private int reconnectionMaxDelay = XmppConstants.RECONNECT_MAX_DELAY_SECONDS;

    /** 是否启用 Ping 心跳 */
    @Builder.Default
    private boolean pingEnabled = false;

    /** Ping 间隔（秒） */
    @Builder.Default
    private int pingInterval = XmppConstants.DEFAULT_PING_INTERVAL_SECONDS;
}
