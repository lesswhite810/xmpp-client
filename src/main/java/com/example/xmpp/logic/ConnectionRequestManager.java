package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppProtocolException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.ConnectionRequest;
import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.StanzaIdGenerator;
import com.example.xmpp.util.XmppScheduler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.ConnectException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ACS 连接请求管理器。
 *
 * @since 2026-03-18
 */
@Slf4j
public class ConnectionRequestManager {

    private static final long DEFAULT_TIMEOUT_MS = 30000L;
    private static final long DEFAULT_RETRY_DELAY_MS = 5000L;
    private static final int RETRY_BACKOFF_BASE = 2;
    private static final String CONNECTION_REQUEST_ID_PREFIX = "connreq";

    private final XmppConnection connection;

    @Getter
    private final long timeoutMs;

    @Getter
    private final long retryDelayMs;

    /**
     * 创建连接请求管理器。
     *
     * @param connection XMPP 连接（ACS 端连接）
     */
    public ConnectionRequestManager(XmppConnection connection) {
        this(connection, DEFAULT_TIMEOUT_MS, DEFAULT_RETRY_DELAY_MS);
    }

    /**
     * 创建连接请求管理器。
     *
     * @param connection XMPP 连接
     * @param timeoutMs 命令超时时间（毫秒）
     */
    public ConnectionRequestManager(XmppConnection connection, long timeoutMs) {
        this(connection, timeoutMs, DEFAULT_RETRY_DELAY_MS);
    }

    /**
     * 创建连接请求管理器。
     *
     * @param connection XMPP 连接
     * @param timeoutMs 命令超时时间（毫秒）
     * @param retryDelayMs 连接断开后等待重连的时间（毫秒）
     */
    public ConnectionRequestManager(XmppConnection connection, long timeoutMs, long retryDelayMs) {
        this.connection = connection;
        this.timeoutMs = timeoutMs;
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * 发送连接请求给 CPE。
     *
     * @param cpeJid CPE 的完整 JID
     * @param username CPE 用户名
     * @param password CPE 密码
     * @return CompletableFuture，携带 CPE 的响应
     */
    public CompletableFuture<XmppStanza> sendConnectionRequest(String cpeJid, String username, String password) {
        validateRequestArguments(cpeJid, username, password);

        if (!connection.isConnected()) {
            log.warn("ConnectionRequest failed: ACS not connected to XMPP server");
            return CompletableFuture.failedFuture(
                    new ConnectException("ACS not connected to XMPP server"));
        }

        log.debug("Sending ConnectionRequest to CPE: {}", cpeJid);
        Iq iq = buildConnectionRequestIq(cpeJid, username, password);

        return connection.sendIqPacketAsync(iq, timeoutMs, TimeUnit.MILLISECONDS)
                .thenCompose(response -> handleResponse(response, cpeJid))
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        log.error("Failed to send ConnectionRequest to {}: {}", cpeJid, cause.getMessage(), cause);
                    }
                });
    }

    private void validateRequestArguments(String cpeJid, String username, String password) {
        if (StringUtils.isBlank(cpeJid)) {
            throw new IllegalArgumentException("CPE JID cannot be null or blank");
        }
        if (username == null || password == null) {
            throw new IllegalArgumentException("Username and password cannot be null");
        }
    }

    private Iq buildConnectionRequestIq(String cpeJid, String username, String password) {
        return new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId(CONNECTION_REQUEST_ID_PREFIX))
                .to(cpeJid)
                .childElement(new ConnectionRequest(username, password))
                .build();
    }

    private Throwable unwrap(Throwable throwable) {
        return throwable.getCause() != null ? throwable.getCause() : throwable;
    }

    /**
     * 发送连接请求并自动重试。
     *
     * @param cpeJid CPE 的完整 JID
     * @param username CPE 用户名
     * @param password CPE 密码
     * @param maxRetries 最大重试次数
     * @return CompletableFuture，携带最终的响应或异常
     */
    public CompletableFuture<XmppStanza> sendConnectionRequestWithRetry(
            String cpeJid, String username, String password, int maxRetries) {
        return doSendWithRetry(cpeJid, username, password, maxRetries, 1);
    }

    private CompletableFuture<XmppStanza> doSendWithRetry(
            String cpeJid, String username, String password, int maxRetries, int currentRetry) {
        return waitForConnection()
                .thenCompose(v -> sendConnectionRequest(cpeJid, username, password))
                .exceptionallyCompose(throwable -> {
                    Throwable cause = unwrap(throwable);

                    if (!isRetryableError(cause)) {
                        log.error("Non-retryable error, giving up: {}", cause.getMessage(), cause);
                        return CompletableFuture.failedFuture(throwable);
                    }

                    if (currentRetry >= maxRetries) {
                        log.error("Max retries ({}) exceeded for CPE {}", maxRetries, cpeJid);
                        return CompletableFuture.failedFuture(throwable);
                    }

                    long delaySeconds = (long) Math.pow(RETRY_BACKOFF_BASE, currentRetry - 1);
                    log.warn("Retrying ConnectionRequest to {} in {} seconds (attempt {}/{})",
                            cpeJid, delaySeconds, currentRetry + 1, maxRetries);

                    return CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(delaySeconds * 1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).thenCompose(v -> doSendWithRetry(cpeJid, username, password, maxRetries, currentRetry + 1));
                });
    }

    private CompletableFuture<Void> waitForConnection() {
        if (connection.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("ACS not connected, waiting for reconnection... (max {} ms)", retryDelayMs);
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Subscribe to connection event
        Runnable unsubscribe = XmppEventBus.getInstance().subscribe(
                connection,
                ConnectionEventType.CONNECTED,
                event -> {
                    log.info("ACS reconnected successfully");
                    future.complete(null);
                }
        );

        // Set up timeout
        ScheduledFuture<?> timeoutTask = XmppScheduler.getScheduler().schedule(
                () -> {
                    // Clean up subscription first
                    unsubscribe.run();
                    // Check if already completed
                    if (!future.isDone()) {
                        future.completeExceptionally(
                                new ConnectException(
                                        "Timeout waiting for reconnection after " + retryDelayMs + " ms"));
                    }
                },
                retryDelayMs,
                TimeUnit.MILLISECONDS
        );

        // Clean up timeout task and event subscription when future completes
        future.whenComplete((result, error) -> timeoutTask.cancel(false));
        future.whenComplete((result, error) -> unsubscribe.run());

        return future;
    }

    private CompletableFuture<XmppStanza> handleResponse(XmppStanza response, String cpeJid) {
        if (response instanceof Iq responseIq && responseIq.getType() == Iq.Type.ERROR) {
            log.warn("CPE {} returned error response: {}",
                    cpeJid, SecurityUtils.summarizeStanza(responseIq));
            XmppError error = responseIq.getError();
            if (error == null) {
                return CompletableFuture.failedFuture(
                        new XmppProtocolException(
                                "CPE " + cpeJid + " returned error response without error details"));
            }

            XmppError.Condition condition = error.getCondition();
            return CompletableFuture.failedFuture(createStanzaException(cpeJid, condition, responseIq));
        }
        log.info("ConnectionRequest sent to {}, response received", cpeJid);
        return CompletableFuture.completedFuture(response);
    }

    private XmppException createStanzaException(String cpeJid, XmppError.Condition condition, Iq errorIq) {
        return switch (condition) {
            case NOT_AUTHORIZED ->
                    new XmppAuthException("CPE " + cpeJid + " authentication failed (not-authorized). "
                            + "Please verify CPE credentials.");
            case RECIPIENT_UNAVAILABLE ->
                    new XmppStanzaErrorException("CPE " + cpeJid + " is currently unavailable (offline). "
                            + "Retry later.", errorIq);
            default ->
                    new XmppStanzaErrorException("CPE returned error: " + condition, errorIq);
        };
    }

    private boolean isRetryableError(Throwable error) {
        if (error instanceof TimeoutException) {
            return true;
        }
        if (error instanceof ConnectException) {
            return true;
        }
        if (error instanceof XmppStanzaErrorException stanzaError) {
            XmppError xmppError = stanzaError.getXmppError();
            if (xmppError != null) {
                XmppError.Condition condition = xmppError.getCondition();
                return condition == XmppError.Condition.RECIPIENT_UNAVAILABLE
                        || condition == XmppError.Condition.REMOTE_SERVER_TIMEOUT;
            }
        }
        return false;
    }
}
