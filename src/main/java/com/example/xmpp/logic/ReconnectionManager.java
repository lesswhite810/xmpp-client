package com.example.xmpp.logic;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.XmppConnection;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppProtocolException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmppScheduler;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 自动重连管理器。
 *
 * <p>负责异常断连后的重试调度。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public final class ReconnectionManager {

    private static final int MAX_EXPONENTIAL_SHIFT = 30;

    private final XmppConnection connection;

    private volatile ScheduledFuture<?> currentTask;

    private Runnable unsubscribe;

    /**
     * 构造 ReconnectionManager。
     *
     * @param connection XMPP 连接实例
     */
    public ReconnectionManager(XmppConnection connection) {
        this.connection = connection;

        XmppEventBus eventBus = XmppEventBus.getInstance();

        unsubscribe = eventBus.subscribeAll(connection, Map.of(
                ConnectionEventType.CONNECTED, event -> {
                    stopReconnectTask();
                    log.debug("TCP connection established, waiting for authenticated session before resetting reconnection cycle");
                },
                ConnectionEventType.AUTHENTICATED, event -> stopReconnectTask(),
                ConnectionEventType.CLOSED, this::onConnectionClosed
        ));
    }

    /**
     * 关闭 ReconnectionManager。
     */
    public void shutdown() {
        stopReconnectTask();
        if (unsubscribe != null) {
            unsubscribe.run();
            unsubscribe = null;
        }
        log.debug("ReconnectionManager shutdown");
    }

    private void onConnectionClosed(ConnectionEvent event) {
        if (isShutdown()) {
            log.debug("Reconnection manager already shutdown, ignoring close event");
            return;
        }
        stopReconnectTask();
        Exception disconnectError = event.error();
        if (disconnectError == null) {
            log.debug("Connection closed normally");
            return;
        }
        if (isNonRecoverableError(disconnectError)) {
            log.warn("Connection closed after non-recoverable error. Skipping reconnection: {}",
                    disconnectError.getClass().getSimpleName());
            return;
        }
        log.warn("Connection closed after error. Starting reconnection.");
        scheduleReconnect(0);
    }

    private boolean isNonRecoverableError(Exception error) {
        return error instanceof XmppAuthException
                || error instanceof XmppProtocolException
                || error instanceof XmppStanzaErrorException;
    }

    private boolean isShutdown() {
        return unsubscribe == null;
    }

    /**
     * 停止重连任务。
     */
    private synchronized void stopReconnectTask() {
        ScheduledFuture<?> task = currentTask;
        currentTask = null;
        if (task != null) {
            task.cancel(true);
        }
    }

    /**
     * 调度重连任务。
     *
     * @param retryIndex 当前退避轮次
     */
    private void scheduleReconnect(int retryIndex) {
        synchronized (this) {
            if (isShutdown()) {
                log.debug("Reconnection manager already shutdown, skipping schedule");
                return;
            }
            if (currentTask != null && !currentTask.isDone()) {
                log.debug("Reconnection task already scheduled, skipping");
                return;
            }

            int delay = calculateReconnectDelay(retryIndex);
            log.info("Reconnecting in {} seconds (Attempt {})...", delay, retryIndex + 1);
            currentTask = XmppScheduler.getScheduler().schedule(
                    () -> runReconnectAttempt(retryIndex),
                    delay,
                    TimeUnit.SECONDS
            );
        }
    }

    /**
     * 计算重连延迟。
     *
     * @param retryIndex 当前退避轮次
     * @return 延迟秒数
     */
    private int calculateReconnectDelay(int retryIndex) {
        long exponentialDelay = (long) XmppConstants.RECONNECT_BASE_DELAY_SECONDS
                * (1L << Math.min(retryIndex, MAX_EXPONENTIAL_SHIFT));
        return (int) Math.min(exponentialDelay, XmppConstants.RECONNECT_MAX_DELAY_SECONDS);
    }

    /**
     * 执行一次重连尝试。
     *
     * @param retryIndex 当前退避轮次
     */
    private void runReconnectAttempt(int retryIndex) {
        synchronized (this) {
            currentTask = null;
        }
        try {
            if (isShutdown()) {
                log.debug("Reconnection manager already shutdown before scheduled attempt executed");
                return;
            }
            if (connection.isConnected()) {
                log.debug("Already connected, skipping reconnection");
                return;
            }

            log.info("Retrying connection...");
            connection.connect();
            log.info("Reconnection successful");
        } catch (XmppException e) {
            handleXmppReconnectFailure(retryIndex, e);
        } catch (RuntimeException e) {
            handleUnexpectedReconnectFailure(retryIndex, e);
        }
    }

    /**
     * 处理预期内的重连失败。
     *
     * @param retryIndex 当前退避轮次
     * @param error 失败异常
     */
    private void handleXmppReconnectFailure(int retryIndex, XmppException error) {
        log.warn("Reconnection failed - type: {}, retryIndex: {}",
                error.getClass().getSimpleName(), retryIndex + 1, error);
        if (isNonRecoverableError(error)) {
            stopReconnectTask();
            log.info("Stopping reconnection after non-recoverable reconnect failure: {}",
                    error.getClass().getSimpleName());
            return;
        }
        scheduleNextReconnectIfActive(retryIndex + 1);
    }

    /**
     * 处理非预期运行时异常。
     *
     * @param retryIndex 当前退避轮次
     * @param error 失败异常
     */
    private void handleUnexpectedReconnectFailure(int retryIndex, RuntimeException error) {
        log.error("Unexpected runtime error during reconnection - type: {}, retryIndex: {}",
                error.getClass().getSimpleName(), retryIndex + 1);
        scheduleNextReconnectIfActive(retryIndex + 1);
    }

    private void scheduleNextReconnectIfActive(int nextRetryIndex) {
        if (!isShutdown()) {
            scheduleReconnect(nextRetryIndex);
            return;
        }
        log.debug("Reconnection manager already shutdown after failed attempt, not scheduling retry");
    }
}
