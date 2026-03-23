package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.logic.PingManager;
import com.example.xmpp.logic.ReconnectionManager;

import lombok.extern.slf4j.Slf4j;

/**
 * XMPP 客户端入口。
 *
 * @since 2026-02-09
 */
@Slf4j
public class Main {

    /**
     * 程序主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        String domain = "example.com";
        String username = "user";
        String password = "password";

        if (args.length == 3) {
            domain = args[0];
            username = args[1];
            password = args[2];
        }

        try {
            runClient(domain, username, password);
        } catch (XmppException e) {
            log.error("XMPP connection failed: {}", e.getMessage(), e);
            throw new RuntimeException("XMPP connection failed");
        }
    }

    /**
     * 运行客户端。
     *
     * @param domain XMPP 服务域名
     * @param username 用户名
     * @param password 密码
     * @throws XmppException 如果连接失败
     */
    private static void runClient(String domain, String username, String password)
            throws XmppException {

        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(domain)
                .username(username)
                .password(password.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .reconnectionEnabled(true)
                .build();

        log.info("Security mode: {}", config.getSecurityMode());
        log.info("Using Direct TLS: {}", config.isUsingDirectTLS());

        XmppTcpConnection connection = new XmppTcpConnection(config);
        PingManager pingManager = null;
        if (config.isPingEnabled()) {
            pingManager = new PingManager(connection);
            pingManager.setPingInterval(config.getPingInterval());
        }
        if (config.isReconnectionEnabled()) {
            new ReconnectionManager(connection);
        }
        try {
            connection.connect();
            log.info("Connected to XMPP server: {}", domain);

            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            log.info("Application interrupted");
            Thread.currentThread().interrupt();
        } finally {
            connection.disconnect();
        }
    }
}
