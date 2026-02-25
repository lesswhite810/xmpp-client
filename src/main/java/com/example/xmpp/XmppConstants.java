package com.example.xmpp;

/**
 * XMPP 协议和网络相关的常量定义。
 *
 * <p>集中存放所有魔法数字（Magic Numbers），提高代码可读性和可维护性。</p>
 *
 * @since 2026-02-09
 */
public final class XmppConstants {

    /**
     * 工具类私有构造函数，禁止实例化。
     */
    private XmppConstants() {
    }

    // ==================== 端口常量 ====================

    /** 标准 XMPP 端口（STARTTLS） */
    public static final int DEFAULT_XMPP_PORT = 5222;

    /** Direct TLS（旧称 SSL）端口 */
    public static final int DIRECT_TLS_PORT = 5223;

    // ==================== 超时时间常量（毫秒） ====================

    /** 默认连接超时时间：30 秒 */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30000;

    /** 默认读取超时时间：60 秒 */
    public static final int DEFAULT_READ_TIMEOUT_MS = 60000;

    // ==================== 超时时间常量（秒） ====================

    /** DNS 查询超时时间：5 秒 */
    public static final int DNS_QUERY_TIMEOUT_SECONDS = 5;

    /** Ping 响应超时时间：10 秒 */
    public static final int PING_RESPONSE_TIMEOUT_SECONDS = 10;

    /** 认证等待超时时间：10 秒 */
    public static final int AUTH_TIMEOUT_SECONDS = 10;

    /** EventLoopGroup 优雅关闭等待时间：5 秒 */
    public static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    // ==================== 重连策略常量 ====================

    /** 重连基础延迟：2 秒 */
    public static final int RECONNECT_BASE_DELAY_SECONDS = 2;

    /** 重连最大延迟：60 秒 */
    public static final int RECONNECT_MAX_DELAY_SECONDS = 60;

    /** 最大重连尝试次数 */
    public static final int MAX_RECONNECT_ATTEMPTS = 10;

    // ==================== SASL 机制优先级常量 ====================

    /** SCRAM-SHA-512 优先级（最高） */
    public static final int PRIORITY_SCRAM_SHA512 = 400;

    /** SCRAM-SHA-256 优先级 */
    public static final int PRIORITY_SCRAM_SHA256 = 300;

    /** SCRAM-SHA-1 优先级 */
    public static final int PRIORITY_SCRAM_SHA1 = 200;

    /** PLAIN 机制优先级（最低） */
    public static final int PRIORITY_PLAIN = 100;

    // ==================== 哈希算法大小常量（字节） ====================

    /** SHA-1 哈希输出大小：20 字节（160 位） */
    public static final int SHA1_HASH_SIZE_BYTES = 20;

    /** SHA-256 哈希输出大小：32 字节（256 位） */
    public static final int SHA256_HASH_SIZE_BYTES = 32;

    /** SHA-512 哈希输出大小：64 字节（512 位） */
    public static final int SHA512_HASH_SIZE_BYTES = 64;

    // ==================== 网络缓冲区常量 ====================

    /** 最大 XML 帧大小：10 MB */
    public static final int MAX_XML_FRAME_SIZE_BYTES = 10 * 1024 * 1024;

    // ==================== Ping 管理器常量 ====================

    /** 默认 Ping 间隔：60 秒 */
    public static final int DEFAULT_PING_INTERVAL_SECONDS = 60;

    // ==================== XML 协议常量 ====================

    /** XMPP 客户端命名空间 */
    public static final String NS_JABBER_CLIENT = "jabber:client";

    /** XMPP 流命名空间 */
    public static final String NS_XMPP_STREAMS = "http://etherx.jabber.org/streams";

    /** XMPP 流特性命名空间 */
    public static final String NS_XMPP_STREAM_FEATURES = "urn:ietf:params:xml:ns:xmpp-streams";

    /** STARTTLS 命名空间 */
    public static final String NS_XMPP_TLS = "urn:ietf:params:xml:ns:xmpp-tls";

    /** SASL 命名空间 */
    public static final String NS_XMPP_SASL = "urn:ietf:params:xml:ns:xmpp-sasl";

    /** 资源绑定命名空间 */
    public static final String NS_XMPP_BIND = "urn:ietf:params:xml:ns:xmpp-bind";

    /** XMPP Ping 命名空间 */
    public static final String NS_XMPP_PING = "urn:xmpp:ping";

    /** XMPP 节错误命名空间 */
    public static final String NS_XMPP_STANZAS = "urn:ietf:params:xml:ns:xmpp-stanzas";

    // ==================== 协议版本常量 ====================

    /** XMPP 协议版本 */
    public static final String XMPP_VERSION = "1.0";

    // ==================== SASL 机制名称常量 ====================

    /** SASL 机制：SCRAM-SHA-1 */
    public static final String SASL_MECH_SCRAM_SHA1 = "SCRAM-SHA-1";

    /** SASL 机制：SCRAM-SHA-256 */
    public static final String SASL_MECH_SCRAM_SHA256 = "SCRAM-SHA-256";

    /** SASL 机制：SCRAM-SHA-512 */
    public static final String SASL_MECH_SCRAM_SHA512 = "SCRAM-SHA-512";

    /** SASL 机制：PLAIN */
    public static final String SASL_MECH_PLAIN = "PLAIN";

    // ==================== IQ 超时常量 ====================

    /** 默认 IQ 请求超时时间：30 秒（毫秒） */
    public static final long DEFAULT_IQ_TIMEOUT_MS = 30000;

    // ==================== 缓冲区大小常量 ====================

    /** 默认 XML StringBuilder 初始容量 */
    public static final int DEFAULT_XML_BUILDER_CAPACITY = 256;

    /** XMPP 流解码器初始缓冲区大小：8 KB */
    public static final int INITIAL_BUFFER_SIZE = 8192;

    /** UTF-8 编码的最大字节/字符比率（安全余量） */
    public static final int UTF8_MAX_BYTES_PER_CHAR = 3;

    // ==================== SSL/TLS 会话常量 ====================

    /** SSL 会话缓存大小 */
    public static final int SSL_SESSION_CACHE_SIZE = 2048;

    /** SSL 会话超时时间（秒） */
    public static final int SSL_SESSION_TIMEOUT_SECONDS = 300;

    /** SSL 握手超时时间（毫秒） */
    public static final int SSL_HANDSHAKE_TIMEOUT_MS = 10000;

    // ==================== Netty 关闭常量 ====================

    /** Netty EventLoopGroup 优雅关闭静默期（毫秒） */
    public static final int SHUTDOWN_QUIET_PERIOD_MS = 100;

    /** Netty EventLoopGroup 关闭超时时间（毫秒） */
    public static final int SHUTDOWN_TIMEOUT_MS = 3000;
}
