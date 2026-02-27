package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 非 TLS 连接测试。
 *
 * @since 2026-02-25
 */
@Slf4j
public class NoTlsConnectionTest {

    // 从系统属性或环境变量读取配置，方便测试
    private static final String XMPP_DOMAIN = System.getProperty("xmpp.domain", System.getenv().getOrDefault("XMPP_DOMAIN", "localhost"));
    private static final String USERNAME = System.getProperty("xmpp.user", System.getenv().getOrDefault("XMPP_USER", "test"));
    private static final String PASSWORD = System.getProperty("xmpp.password", System.getenv().getOrDefault("XMPP_PASSWORD", "test"));
    private static final String HOST = System.getProperty("xmpp.host", System.getenv().getOrDefault("XMPP_HOST", "localhost"));
    private static final int PORT = Integer.parseInt(System.getProperty("xmpp.port", System.getenv().getOrDefault("XMPP_PORT", "5222")));

    // 确保本地有 XMPP 服务器在运行
    @Test
    public void testConnectWithoutTls() throws Exception {
        log.info("=== Starting non-TLS connection test ===");
        log.info("Target: {}:{}, domain: {}, user: {}", HOST, PORT, XMPP_DOMAIN, USERNAME);

        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .username(USERNAME)
                .password(PASSWORD.toCharArray())
                .host(HOST)
                .port(PORT)
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)  // 禁用 TLS
                .sendPresence(true)
                .reconnectionEnabled(false)
                .build();

        XmppTcpConnection connection = new XmppTcpConnection(config);

        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch closeLatch = new CountDownLatch(1);

        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.ConnectedEvent e ->
                    log.info(">>> TCP connection established");
                case ConnectionEvent.AuthenticatedEvent e -> {
                    log.info(">>> Authentication successful! resumed={}", e.resumed());
                    authLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedEvent e -> {
                    log.info(">>> Connection closed");
                    closeLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedOnErrorEvent e -> {
                    log.error(">>> Connection error: {}", e.error().getMessage(), e.error());
                    closeLatch.countDown();
                }
            }
        });

        try {
            log.info("Starting connection...");
            connection.connect();

            log.info("Waiting for authentication...");
            boolean authenticated = authLatch.await(15, TimeUnit.SECONDS);

            if (authenticated) {
                log.info("=== Test passed: Authentication completed ===");
                log.info("Connection state: connected={}, authenticated={}",
                        connection.isConnected(), connection.isAuthenticated());

                // 保持连接 5 秒，观察日志
                Thread.sleep(5000);
            } else {
                log.error("=== Test failed: Authentication timeout ===");
            }

        } finally {
            log.info("Disconnecting...");
            connection.disconnect();
            closeLatch.await(5, TimeUnit.SECONDS);
            log.info("=== Test finished ===");
        }
    }
}
