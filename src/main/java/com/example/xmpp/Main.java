package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.protocol.model.Message;

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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Application interrupted");
        } catch (XmppException e) {
            log.error("XMPP error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * 运行 XMPP 客户端。
     *
     * @param domain   XMPP 服务域名
     * @param username 用户名
     * @param password 密码
     *
     * @throws InterruptedException 如果线程被中断
     * @throws XmppException        如果 XMPP 连接失败
     */
    private static void runClient(String domain, String username, String password)
            throws InterruptedException, XmppException {

        // 创建配置（使用 Builder 模式）
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain(domain)
                .username(username)
                .password(password.toCharArray())
                .build();

        // 创建连接（PingManager 和 ReconnectionManager 自动初始化）
        XmppTcpConnection connection = new XmppTcpConnection(config);

        // 注册消息监听器（示例：记录收到的消息）
        connection.addAsyncStanzaListener(
                stanza -> {
                    if (stanza instanceof Message msg) {
                        log.info("Received message from {}: {}", msg.getFrom(), msg.getBody());
                    }
                },
                stanza -> stanza instanceof Message
        );

        // 建立连接
        connection.connect();

        log.info("Connected to XMPP server: {}", domain);

        // 保持程序运行
        Thread.sleep(Long.MAX_VALUE);
    }
}
