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
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自动重连管理器。
 *
 * @since 2026-02-09
 */
@Slf4j
public final class ReconnectionManager {

    private static final int MAX_EXPONENTIAL_SHIFT = 30;

    private final XmppConnection connection;

    private volatile ScheduledFuture<?> currentTask;

    private final ReentrantLock stateLock = new ReentrantLock();

    private Runnable unsubscribe;

    /**
     * 构造 ReconnectionManager。
     *
     * @param connection XMPP 连接实例
     */
    public ReconnectionManager(XmppConnection connection) {
        this.connection = Objects.requireNonNull(connection, "XmppConnection must not be null");

        XmppEventBus eventBus = XmppEventBus.getInstance();

        unsubscribe = eventBus.subscribeAll(connection, Map.of(
                ConnectionEventType.CONNECTED, event -> onConnected(),
                ConnectionEventType.CLOSED, this::onConnectionClosed
        ));
    }

    /**
     * 关闭 ReconnectionManager。
     */
    public void shutdown() {
        Runnable unsubscribeAction;
        stateLock.lock();
        try {
            stopReconnectTaskLocked();
            unsubscribeAction = unsubscribe;
            unsubscribe = null;
        } finally {
            stateLock.unlock();
        }
        if (unsubscribeAction != null) {
            unsubscribeAction.run();
        }
        log.debug("ReconnectionManager shutdown");
    }

    private void onConnectionClosed(ConnectionEvent event) {
        Exception disconnectError = event.error();
        if (disconnectError == null) {
            log.debug("Connection closed normally, shutting down reconnection manager");
            shutdown();
            return;
        }

        stateLock.lock();
        try {
            if (isShutdownLocked()) {
                log.debug("Reconnection manager already shutdown, ignoring close event");
                return;
            }
            stopReconnectTaskLocked();
            if (isNonRecoverableError(disconnectError)) {
                log.error("Connection closed after non-recoverable error. Skipping reconnection: {}",
                        disconnectError.getClass().getSimpleName());
                return;
            }
            log.error("Connection closed after error. Starting reconnection.");
            scheduleReconnectLocked(0);
        } finally {
            stateLock.unlock();
        }
    }

    private void onConnected() {
        stopReconnectTask();
        log.debug("XMPP session established, reconnection cycle reset");
    }

    private boolean isNonRecoverableError(Exception error) {
        return error instanceof XmppAuthException
                || error instanceof XmppProtocolException
                || error instanceof XmppStanzaErrorException;
    }

    private boolean isShutdownLocked() {
        return unsubscribe == null;
    }

    /**
     * 停止重连任务。
     */
    private void stopReconnectTask() {
        stateLock.lock();
        try {
            stopReconnectTaskLocked();
        } finally {
            stateLock.unlock();
        }
    }

    private void stopReconnectTaskLocked() {
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
        stateLock.lock();
        try {
            scheduleReconnectLocked(retryIndex);
        } finally {
            stateLock.unlock();
        }
    }

    private void scheduleReconnectLocked(int retryIndex) {
        if (isShutdownLocked()) {
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
        stateLock.lock();
        try {
            currentTask = null;
            if (isShutdownLocked()) {
                log.debug("Reconnection manager already shutdown before scheduled attempt executed");
                return;
            }
        } finally {
            stateLock.unlock();
        }
        if (connection.isConnected()) {
            log.debug("Already connected, skipping reconnection");
            return;
        }

        try {
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
        log.error("Reconnection failed - type: {}, retryIndex: {}, message: {}",
                error.getClass().getSimpleName(), retryIndex + 1, error.getMessage());
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
        stateLock.lock();
        try {
            if (!isShutdownLocked()) {
                scheduleReconnectLocked(nextRetryIndex);
                return;
            }
        } finally {
            stateLock.unlock();
        }
        log.debug("Reconnection manager already shutdown after failed attempt, not scheduling retry");
    }
}
