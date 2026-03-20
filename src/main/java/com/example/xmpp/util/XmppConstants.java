package com.example.xmpp.util;

import java.util.UUID;

/**
 * XMPP 协议和网络常量。
 *
 * @since 2026-02-09
 */
public final class XmppConstants {

    /**
     * 工具类私有构造函数，禁止实例化。
     */
    private XmppConstants() {
    }

    /**
     * 生成唯一的 Stanza ID。
     *
     * @return 唯一的 Stanza ID 字符串，格式为 "xmpp-{UUID}"
     */
    public static String generateStanzaId() {
        return "xmpp-" + UUID.randomUUID().toString();
    }

    public static final int DEFAULT_XMPP_PORT = 5222;

    public static final int DIRECT_TLS_PORT = 5223;

    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;

    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 60;

    public static final int PING_RESPONSE_TIMEOUT_SECONDS = 10;

    public static final int AUTH_TIMEOUT_SECONDS = 10;

    public static final int SHUTDOWN_TIMEOUT_SECONDS = 3;

    public static final int RECONNECT_BASE_DELAY_SECONDS = 2;

    public static final int RECONNECT_MAX_DELAY_SECONDS = 60;

    public static final int MAX_RECONNECT_ATTEMPTS = 10;

    public static final int PRIORITY_SCRAM_SHA512 = 400;

    public static final int PRIORITY_SCRAM_SHA256 = 300;

    public static final int PRIORITY_SCRAM_SHA1 = 200;

    public static final int PRIORITY_PLAIN = 100;

    public static final int SHA1_HASH_SIZE_BYTES = 20;

    public static final int SHA256_HASH_SIZE_BYTES = 32;

    public static final int SHA512_HASH_SIZE_BYTES = 64;

    public static final int MAX_XML_FRAME_SIZE_BYTES = 10 * 1024 * 1024;

    public static final int DEFAULT_PING_INTERVAL_SECONDS = 60;

    public static final String NS_JABBER_CLIENT = "jabber:client";

    public static final String NS_XMPP_STREAMS = "http://etherx.jabber.org/streams";

    public static final String NS_XMPP_STREAM_FEATURES = "urn:ietf:params:xml:ns:xmpp-streams";

    public static final String NS_XMPP_TLS = "urn:ietf:params:xml:ns:xmpp-tls";

    public static final String NS_XMPP_SASL = "urn:ietf:params:xml:ns:xmpp-sasl";

    public static final String NS_XMPP_BIND = "urn:ietf:params:xml:ns:xmpp-bind";

    public static final String NS_XMPP_PING = "urn:xmpp:ping";

    public static final String NS_XMPP_STANZAS = "urn:ietf:params:xml:ns:xmpp-stanzas";

    public static final String XMPP_VERSION = "1.0";

    public static final String SASL_MECH_SCRAM_SHA1 = "SCRAM-SHA-1";

    public static final String SASL_MECH_SCRAM_SHA256 = "SCRAM-SHA-256";

    public static final String SASL_MECH_SCRAM_SHA512 = "SCRAM-SHA-512";

    public static final String SASL_MECH_PLAIN = "PLAIN";

    public static final long DEFAULT_IQ_TIMEOUT_SECONDS = 30L;

    public static final int DEFAULT_XML_BUILDER_CAPACITY = 256;

    public static final int INITIAL_BUFFER_SIZE = 8192;

    public static final int UTF8_MAX_BYTES_PER_CHAR = 3;

    public static final int SSL_SESSION_CACHE_SIZE = 2048;

    public static final int SSL_SESSION_TIMEOUT_SECONDS = 300;

    public static final int SSL_HANDSHAKE_TIMEOUT_SECONDS = 10;

    public static final int SHUTDOWN_QUIET_PERIOD_SECONDS = 1;
}
