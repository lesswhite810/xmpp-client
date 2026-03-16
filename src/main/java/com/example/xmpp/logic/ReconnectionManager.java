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
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自动重连管理器。
 *
 * <p>实现连接断开后的自动重连逻辑，采用指数退避算法避免频繁重连。</p>
 *
 * <p>功能特性：</p>
 * <ul>
 * <li>监听连接错误事件并自动触发重连</li>
 * <li>使用指数退避算法（基础延迟 2 秒，最大延迟 60 秒）</li>
 * <li>添加随机抖动避免雷鸣羊群效应</li>
 * <li>支持启用/禁用重连功能</li>
 * <li>正常关闭连接时不触发重连</li>
 * <li>重连成功后自动停止定时任务</li>
 * </ul>
 *
 * @since 2026-02-09
 */
@Slf4j
public class ReconnectionManager {

    private static final int BASE_DELAY_SECONDS = XmppConstants.RECONNECT_BASE_DELAY_SECONDS;

    private static final int MAX_DELAY_SECONDS = XmppConstants.RECONNECT_MAX_DELAY_SECONDS;

    private static final int MAX_RECONNECT_ATTEMPTS = XmppConstants.MAX_RECONNECT_ATTEMPTS;

    private final XmppConnection connection;

    private volatile ScheduledFuture<?> currentTask;

    private volatile boolean enabled = true;

    private final Random random = new Random();

    private final AtomicInteger attemptCount = new AtomicInteger(0);

    private volatile boolean reconnectionScheduledDueToError = false;

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
                ConnectionEventType.CONNECTED, event -> onConnected(),
                ConnectionEventType.AUTHENTICATED, event -> onAuthenticated(),
                ConnectionEventType.CLOSED, event -> onConnectionClosed(),
                ConnectionEventType.ERROR, event -> onConnectionClosedOnError(event.error())
        ));
    }

    /**
     * 启用自动重连。
     *
     * <p>允许在连接断开时自动尝试重新连接。</p>
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * 禁用自动重连。
     *
     * <p>取消所有待执行的重连任务。</p>
     */
    public void disable() {
        this.enabled = false;
        resetReconnectCycle();
        stopReconnectTask();
    }

    /**
     * 关闭 ReconnectionManager。
     *
     * <p>停止重连任务并释放相关资源（取消事件订阅）。</p>
     */
    public void shutdown() {
        this.enabled = false;
        resetReconnectCycle();
        stopReconnectTask();
        if (unsubscribe != null) {
            unsubscribe.run();
            unsubscribe = null;
        }
        log.debug("ReconnectionManager shutdown");
    }

    /**
     * 处理连接事件。
     *
     * <p>此方法用于测试目的，实际事件处理通过 XmppEventBus 自动触发。</p>
     *
     * @param event 连接事件
     */
    public void onEvent(ConnectionEvent event) {
        switch (event.eventType()) {
            case CONNECTED -> onConnected();
            case AUTHENTICATED -> onAuthenticated();
            case CLOSED -> onConnectionClosed();
            case ERROR -> onConnectionClosedOnError(event.error());
        }
    }

    private void onConnected() {
        reconnectionScheduledDueToError = false;
        stopReconnectTask();
        log.debug("TCP connection established, waiting for authenticated session before resetting reconnection cycle");
    }

    private void onAuthenticated() {
        resetReconnectCycle();
        stopReconnectTask();
    }

    private void onConnectionClosed() {
        if (reconnectionScheduledDueToError) {
            log.debug("Connection closed but reconnection already scheduled due to previous error");
            return;
        }
        stopReconnectTask();
        log.debug("Connection closed normally");
    }

    private void onConnectionClosedOnError(Exception e) {
        if (!enabled) {
            log.debug("Reconnection disabled, not attempting to reconnect");
            return;
        }
        if (isNonRecoverableError(e)) {
            resetReconnectCycle();
            stopReconnectTask();
            log.info("Connection closed on non-recoverable error. Skipping reconnection: {}",
                    e != null ? e.getClass().getSimpleName() : "unknown");
            logReconnectErrorDetail("Non-recoverable error detail", e);
            return;
        }
        reconnectionScheduledDueToError = true;
        log.info("Connection closed on error. Starting reconnection.");
        logReconnectErrorDetail("Connection closed on error detail", e);
        scheduleReconnect(0);
    }

    private boolean isNonRecoverableError(Exception error) {
        return error instanceof XmppAuthException
                || error instanceof XmppProtocolException
                || error instanceof XmppStanzaErrorException;
    }

    /**
     * 重置当前重连周期状态。
     */
    private void resetReconnectCycle() {
        this.reconnectionScheduledDueToError = false;
        this.attemptCount.set(0);
    }

    /**
     * 记录重连相关异常详情。
     *
     * @param message 日志前缀
     * @param error 异常对象
     */
    private void logReconnectErrorDetail(String message, Exception error) {
        if (error != null) {
            log.debug("{}: {}", message, error.getMessage());
        }
    }

    /**
     * 停止重连任务。
     *
     * <p>取消当前待执行的重连任务并重置重连计数。</p>
     */
    private synchronized void stopReconnectTask() {
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
    }

    /**
     * 调度重连任务。
     *
     * <p>使用指数退避算法计算延迟时间，并添加随机抖动避免雷鸣羊群效应。</p>
     *
     * @param attempt 当前重连尝试次数（从 0 开始）
     */
    private void scheduleReconnect(int attempt) {
        synchronized (this) {
            log.debug("scheduleReconnect called with attempt={}, currentTask={}", attempt, currentTask);

            if (currentTask != null && !currentTask.isDone()) {
                log.debug("Reconnection task already scheduled, skipping");
                return;
            }

            int currentAttempt = attemptCount.incrementAndGet();
            if (currentAttempt > MAX_RECONNECT_ATTEMPTS) {
                log.warn("Max reconnection attempts ({}) reached, stopping reconnection", MAX_RECONNECT_ATTEMPTS);
                resetReconnectCycle();
                return;
            }

            int delay = calculateReconnectDelay(attempt);
            log.info("Reconnecting in {} seconds (Attempt {}/{})...", delay, currentAttempt, MAX_RECONNECT_ATTEMPTS);
            currentTask = XmppScheduler.getScheduler().schedule(
                    () -> runReconnectAttempt(attempt),
                    delay,
                    TimeUnit.SECONDS
            );
        }
    }

    /**
     * 计算重连延迟。
     *
     * @param attempt 当前重连轮次
     * @return 延迟秒数
     */
    private int calculateReconnectDelay(int attempt) {
        int delay = Math.min(BASE_DELAY_SECONDS * (1 << attempt), MAX_DELAY_SECONDS);
        return delay + random.nextInt(Math.max(1, delay / 4));
    }

    /**
     * 执行一次重连尝试。
     *
     * @param attempt 当前重连轮次
     */
    private void runReconnectAttempt(int attempt) {
        synchronized (this) {
            currentTask = null;
        }
        try {
            if (!enabled) {
                log.debug("Reconnection disabled before scheduled attempt executed");
                return;
            }
            if (connection.isConnected()) {
                resetReconnectCycle();
                log.debug("Already connected, skipping reconnection");
                return;
            }

            log.info("Retrying connection...");
            connection.resetHandlerState();
            connection.connect();
            if (!enabled) {
                log.debug("Reconnection disabled while connect attempt was running, closing reconnected session");
                connection.disconnect();
                return;
            }
            log.info("Reconnection successful");
        } catch (XmppException e) {
            handleXmppReconnectFailure(attempt, e);
        } catch (RuntimeException e) {
            handleRuntimeReconnectFailure(attempt, e);
        }
    }

    /**
     * 处理 XMPP 层重连失败。
     *
     * @param attempt 当前重连轮次
     * @param error 失败异常
     */
    private void handleXmppReconnectFailure(int attempt, XmppException error) {
        log.warn("Reconnection failed");
        log.debug("Detail", error);
        if (isNonRecoverableError(error)) {
            resetReconnectCycle();
            stopReconnectTask();
            log.info("Stopping reconnection after non-recoverable reconnect failure: {}",
                    error.getClass().getSimpleName());
            logReconnectErrorDetail("Non-recoverable reconnect failure detail", error);
            return;
        }
        if (enabled) {
            scheduleReconnect(attempt + 1);
        } else {
            log.debug("Reconnection disabled after failed attempt, not scheduling retry");
        }
    }

    /**
     * 处理运行时重连失败。
     *
     * @param attempt 当前重连轮次
     * @param error 失败异常
     */
    private void handleRuntimeReconnectFailure(int attempt, RuntimeException error) {
        log.error("Unexpected runtime error during reconnection");
        log.debug("Detail", error);
        log.debug("Unexpected runtime reconnection failure detail", error);
        if (enabled) {
            scheduleReconnect(attempt + 1);
        } else {
            log.debug("Reconnection disabled after failed attempt, not scheduling retry");
        }
    }
}
