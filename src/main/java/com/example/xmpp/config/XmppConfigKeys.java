package com.example.xmpp.config;

/**
 * XMPP 配置键常量。
 *
 * <p>统一定义 {@link SystemService} 中使用的配置键，避免业务代码散落字符串字面量。</p>
 *
 * @since 2026-03-11
 */
public final class XmppConfigKeys {

    public static final String XMPP_SERVICE_DOMAIN = "xmpp.service.domain";
    public static final String HOST = "xmpp.host";
    public static final String HOST_ADDRESS = "xmpp.host.address";
    public static final String PORT = "xmpp.port";
    public static final String RESOURCE = "xmpp.resource";
    public static final String USERNAME = "xmpp.username";
    public static final String PASSWORD = "xmpp.password";
    public static final String AUTHZID = "xmpp.authzid";
    public static final String SECURITY_MODE = "xmpp.security.mode";
    public static final String CONNECT_TIMEOUT = "xmpp.connect.timeout.ms";
    public static final String READ_TIMEOUT = "xmpp.read.timeout.ms";
    public static final String SEND_PRESENCE = "xmpp.send.presence";
    public static final String RECONNECTION_ENABLED = "xmpp.reconnection.enabled";
    public static final String RECONNECTION_BASE_DELAY = "xmpp.reconnection.base.delay.seconds";
    public static final String RECONNECTION_MAX_DELAY = "xmpp.reconnection.max.delay.seconds";
    public static final String PING_ENABLED = "xmpp.ping.enabled";
    public static final String PING_INTERVAL = "xmpp.ping.interval.seconds";
    public static final String DIRECT_TLS = "xmpp.direct.tls";
    public static final String HANDSHAKE_TIMEOUT = "xmpp.handshake.timeout.ms";
    public static final String TLS_AUTHENTICATION_MODE = "xmpp.tls.authentication.mode";
    public static final String ENABLED_SASL_MECHANISMS = "xmpp.enabled.sasl.mechanisms";

    private XmppConfigKeys() {
    }
}
