package com.example.xmpp.example;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.logic.ConnectionRequestManager;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.util.XmppConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ACS 端 ConnectionRequest 异常处理示例。
 *
 * @since 2026-03-19
 */
@Slf4j
public class AcsConnectionRequestExample {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long REQUEST_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(XmppConstants.DEFAULT_IQ_TIMEOUT_SECONDS);

    private final XmppConnection connection;
    private final ConnectionRequestManager requestManager;
    private final AtomicInteger retryCount = new AtomicInteger(0);

    public AcsConnectionRequestExample(XmppConnection connection) {
        this.connection = connection;
        this.requestManager = new ConnectionRequestManager(connection, REQUEST_TIMEOUT_MS);
    }

    /**
     * ACS 端注册连接事件监听。
     */
    public void registerEventListeners() {
        XmppEventBus eventBus = XmppEventBus.getInstance();
        eventBus.subscribe(connection, ConnectionEventType.ERROR, this::onConnectionError);
        eventBus.subscribe(connection, ConnectionEventType.CLOSED, this::onConnectionClosed);
        eventBus.subscribe(connection, ConnectionEventType.AUTHENTICATED, this::onAuthenticated);
        log.info("ACS 事件监听器已注册");
    }

    /**
     * 发送 ConnectionRequest。
     */
    public CompletableFuture<XmppStanza> sendConnectionRequest(String cpeJid, String username, String password) {
        return requestManager.sendConnectionRequest(cpeJid, username, password);
    }

    /**
     * 发送 ConnectionRequest 并自动重试。
     */
    public CompletableFuture<XmppStanza> sendConnectionRequestWithRetry(
            String cpeJid, String username, String password, int maxRetries) {
        return requestManager.sendConnectionRequestWithRetry(cpeJid, username, password, maxRetries);
    }

    /**
     * 连接错误事件处理。
     */
    private void onConnectionError(ConnectionEvent event) {
        Exception error = event.error();
        log.error("连接异常关闭: {}", error != null ? error.getMessage() : "未知错误");

        if (error instanceof XmppAuthException) {
            log.error("认证失败 - ACS 配置的凭据无效，请检查用户名密码");
        } else if (error instanceof java.net.ConnectException) {
            log.warn("无法连接到 XMPP 服务器，请检查服务器地址和端口");
        } else {
            log.warn("连接因错误关闭，ReconnectionManager 将尝试重连");
        }
    }

    /**
     * 连接关闭事件处理。
     */
    private void onConnectionClosed(ConnectionEvent event) {
        log.info("连接已关闭");
    }

    /**
     * 认证成功事件处理。
     */
    private void onAuthenticated(ConnectionEvent event) {
        log.info("ACS 认证成功");
        retryCount.set(0);
    }

    /**
     * 主动关闭连接并清理资源。
     */
    public void shutdown() {
        log.info("关闭 ACS ConnectionRequest 示例");
        XmppEventBus.getInstance().unsubscribeAll(connection);
        connection.disconnect();
    }

    // ========== 使用示例 ==========

    public static void main(String[] args) {
        XmppClientConfig config = XmppClientConfig.builder()
                .host("acs.example.com")
                .port(XmppConstants.DEFAULT_XMPP_PORT)
                .username("acs-admin")
                .password("acs-password".toCharArray())
                .xmppServiceDomain("example.com")
                .build();

        XmppTcpConnection connection = new XmppTcpConnection(config);
        AcsConnectionRequestExample example = new AcsConnectionRequestExample(connection);
        example.registerEventListeners();

        try {
            connection.connect();

            String cpeJid = "cpe001@example.com";

            // 方式一：发送请求（需自行处理异常）
            example.sendConnectionRequest(cpeJid, "cpe-username", "cpe-password")
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        if (cause instanceof XmppAuthException) {
                            log.error("CPE 认证失败: {}", cause.getMessage());
                        } else if (cause instanceof XmppStanzaErrorException stanzaError) {
                            XmppError error = stanzaError.getXmppError();
                            if (error != null && error.getCondition() == XmppError.Condition.NOT_AUTHORIZED) {
                                log.error("CPE {} 认证失败，请检查凭据", cpeJid);
                            }
                        }
                        return null;
                    });

            // 方式二：发送请求并自动重试
            example.sendConnectionRequestWithRetry(cpeJid, "cpe-username", "cpe-password", MAX_RETRY_COUNT)
                    .get(XmppConstants.DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (XmppAuthException e) {
            log.error("ACS 认证失败，请检查凭据配置", e);
        } catch (Exception e) {
            log.error("发生错误", e);
        }
    }
}
