package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.Presence;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Openfire 服务器连接测试。
 */
class OpenfireConnectionTest {

    private static final Logger log = LoggerFactory.getLogger(OpenfireConnectionTest.class);

    /** XMPP 域名（Openfire 服务器配置的域名） */
    private static final String XMPP_DOMAIN = "lesswhite";

    /** 服务器主机地址 */
    private static final String HOST = "localhost";

    /** 服务器端口 */
    private static final int PORT = 5222;

    /** 用户名 */
    private static final String USERNAME = "acs";

    /** 密码 */
    private static final String PASSWORD = "acs";

    /** 信任所有证书的 TrustManager（仅用于测试） */
    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    @Test
    void testConnectAndAuthenticate() throws Exception {
        log.info("========================================");
        log.info("XMPP Connection Test");
        log.info("========================================");
        log.info("Domain: {}", XMPP_DOMAIN);
        log.info("Host: {}", HOST);
        log.info("Port: {}", PORT);
        log.info("User: {}", USERNAME);
        log.info("========================================");

        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .username(USERNAME)
                .password(PASSWORD.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.IF_POSSIBLE)
                .customTrustManager(new TrustManager[]{TRUST_ALL_MANAGER})  // 信任自签名证书
                .sendPresence(false)
                .build();

        XmppTcpConnection connection = new XmppTcpConnection(config);

        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch closeLatch = new CountDownLatch(1);

        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.ConnectedEvent e ->
                    log.info("[Event] Connected to server");
                case ConnectionEvent.AuthenticatedEvent e -> {
                    log.info("[Event] Authentication successful (resumed={})", e.resumed());
                    authLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedEvent e -> {
                    log.info("[Event] Connection closed");
                    closeLatch.countDown();
                }
                case ConnectionEvent.ConnectionClosedOnErrorEvent e -> {
                    log.error("[Event] Connection closed with error: {}", e.error().getMessage(), e.error());
                    closeLatch.countDown();
                }
            }
        });

        try {
            log.info("[Step1] Starting connection...");
            long startTime = System.currentTimeMillis();

            connection.connect();

            log.info("[Step2] Waiting for authentication...");
            boolean authenticated = authLatch.await(15, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (authenticated) {
                log.info("========================================");
                log.info("Connection test successful!");
                log.info("Elapsed: {}ms", elapsed);
                log.info("Connection state: {}", connection.isConnected() ? "connected" : "disconnected");
                log.info("Authentication state: {}", connection.isAuthenticated() ? "authenticated" : "not authenticated");
                log.info("========================================");

                testPing(connection);
            } else {
                log.error("Authentication timeout!");
                fail("Authentication timeout, please check username and password");
            }

        } finally {
            log.info("[Cleanup] Disconnecting...");
            connection.disconnect();
            closeLatch.await(5, TimeUnit.SECONDS);
            log.info("[Done] Test finished");
        }
    }

    private void testPing(XmppTcpConnection connection) throws Exception {
        log.info("[Step3] Testing Ping...");

        String pingId = "ping-" + System.currentTimeMillis();
        Iq pingRequest = PingIq.createPingRequest(pingId, XMPP_DOMAIN);

        // 打印发送的 XML
        log.debug("Sending Ping XML:\n{}", pingRequest.toXml().indent(2));

        CountDownLatch pingLatch = new CountDownLatch(1);

        connection.sendIqPacketAsync(pingRequest)
                .thenAccept(response -> {
                    if (response instanceof Iq iq) {
                        log.info("Received response: id={}, type={}", iq.getId(), iq.getType());
                        if (iq.getType() == Iq.Type.RESULT) {
                            log.info("Ping successful!");
                        } else if (iq.getType() == Iq.Type.ERROR) {
                            log.warn("Ping returned error");
                            // 打印错误详情
                            if (iq.getError() != null) {
                                log.warn("  Error type: {}", iq.getError().getType());
                                log.warn("  Error condition: {}", iq.getError().getCondition());
                                if (iq.getError().getText() != null) {
                                    log.warn("  Error text: {}", iq.getError().getText());
                                }
                            }
                        }
                        // 打印响应 XML
                        log.debug("Response XML:\n{}", iq.toXml().indent(2));
                    }
                    pingLatch.countDown();
                })
                .exceptionally(ex -> {
                    log.error("Ping failed: {}", ex.getMessage(), ex);
                    pingLatch.countDown();
                    return null;
                });

        boolean received = pingLatch.await(10, TimeUnit.SECONDS);
        if (!received) {
            log.warn("Ping response timeout (server may not support XEP-0199)");
        }

        log.info("[Step4] Sending presence...");
        Presence presence = new Presence.Builder()
                .type(Presence.Type.AVAILABLE)
                .show("chat")
                .status("Online test")
                .build();

        connection.sendStanza(presence);
        log.info("Presence sent");
    }
}
