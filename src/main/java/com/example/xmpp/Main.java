package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;

import lombok.extern.slf4j.Slf4j;

/**
 * XMPP 客户端示例程序主入口。
 *
 * <p>演示如何使用 XMPP 客户端库连接到 XMPP 服务器。</p>
 *
 * <h3>使用方法</h3>
 * <pre>
 * java -jar xmpp-client.jar &lt;domain&gt; &lt;username&gt; &lt;password&gt;
 * </pre>
 *
 * @since 2026-02-09
 */
@Slf4j
public class Main {

    /**
     * 程序主入口。
     *
     * @param args 命令行参数：domain username password
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
            log.error("XMPP error");
            log.debug("Detail", e);
            throw new RuntimeException("XMPP connection failed", e);
        }
    }

    /**
     * 运行 XMPP 客户端。
     *
     * @param domain XMPP 服务域名
     * @param username 用户名
     * @param password 密码
     * @throws XmppException 如果 XMPP 连接失败
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

        connection.connect();

        log.info("Connected to XMPP server: {}", domain);

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            log.info("Application interrupted");
        }
    }
}
