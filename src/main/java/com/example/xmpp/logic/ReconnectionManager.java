package com.example.xmpp.logic;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.XmppConnection;
import com.example.xmpp.exception.XmppException;
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

    /**
     * 基础重连延迟（秒）
     */
    private static final int BASE_DELAY_SECONDS = XmppConstants.RECONNECT_BASE_DELAY_SECONDS;

    /**
     * 最大重连延迟（秒）
     */
    private static final int MAX_DELAY_SECONDS = XmppConstants.RECONNECT_MAX_DELAY_SECONDS;

    /**
     * 最大重连尝试次数
     */
    private static final int MAX_RECONNECT_ATTEMPTS = XmppConstants.MAX_RECONNECT_ATTEMPTS;

    /**
     * 关联的 XMPP 连接
     */
    private final XmppConnection connection;

    /**
     * 当前重连任务
     */
    private volatile ScheduledFuture<?> currentTask;

    /**
     * 是否启用自动重连
     */
    private volatile boolean enabled = true;

    /**
     * 随机数生成器（用于添加抖动）
     */
    private final Random random = new Random();

    /**
     * 当前连续重连尝试次数（线程安全）
     */
    private final AtomicInteger attemptCount = new AtomicInteger(0);

    /**
     * 是否因错误触发过重连（避免被正常关闭事件覆盖）
     */
    private volatile boolean reconnectionScheduledDueToError = false;

    /** 事件订阅取消回调 */
    private Runnable unsubscribe;

    /**
     * 构造 ReconnectionManager。
     *
     * @param connection XMPP 连接实例
     */
    public ReconnectionManager(XmppConnection connection) {
        this.connection = connection;

        /** 通过 XmppEventBus 订阅连接事件（连接专属订阅，只响应本连接的事件） */
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
        stopReconnectTask();
    }

    /**
     * 关闭 ReconnectionManager。
     *
     * <p>停止重连任务并释放相关资源（取消事件订阅）。</p>
     */
    public void shutdown() {
        this.enabled = false;
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
        attemptCount.set(0);
        reconnectionScheduledDueToError = false;
        stopReconnectTask();
        log.debug("Connection established, reconnection task stopped");
    }

    private void onAuthenticated() {
        attemptCount.set(0);
        reconnectionScheduledDueToError = false;
        stopReconnectTask();
    }

    private void onConnectionClosed() {
        /** 如果之前因错误触发了重连，不取消（避免事件顺序问题） */
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
        reconnectionScheduledDueToError = true;
        log.info("Connection closed on error. Starting reconnection...", e);
        scheduleReconnect(0);
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

            /** 检查是否已有任务在运行 */
            if (currentTask != null && !currentTask.isDone()) {
                log.debug("Reconnection task already scheduled, skipping");
                return;
            }

            /** 注意：这里不检查 isConnected()，因为调用 scheduleReconnect 时 */
            /** 连接刚刚因错误关闭，channel 可能还处于过渡状态 */
            /** 重连逻辑会在实际执行时检查连接状态 */

            int currentAttempt = attemptCount.updateAndGet(curr -> curr >= MAX_RECONNECT_ATTEMPTS ? curr : curr + 1);
            if (currentAttempt > MAX_RECONNECT_ATTEMPTS) {
                log.error("Max reconnection attempts ({}) reached, stopping reconnection", MAX_RECONNECT_ATTEMPTS);
                attemptCount.set(0);
                return;
            }

            int delay = Math.min(BASE_DELAY_SECONDS * (1 << attempt), MAX_DELAY_SECONDS);
            delay += random.nextInt(Math.max(1, delay / 4));

            log.info("Reconnecting in {} seconds (Attempt {}/{})...", delay, currentAttempt, MAX_RECONNECT_ATTEMPTS);

            currentTask = XmppScheduler.getScheduler().schedule(() -> {
                try {
                    if (connection.isConnected()) {
                        attemptCount.set(0);
                        log.debug("Already connected, skipping reconnection");
                        return;
                    }
                    log.info("Retrying connection...");
                    connection.resetHandlerState();
                    connection.connect();
                    log.info("Reconnection successful");
                } catch (XmppException e) {
                    log.error("Reconnection failed: {}", e.getMessage());
                    scheduleReconnect(attempt + 1);
                } catch (RuntimeException e) {
                    log.error("Unexpected runtime error during reconnection: {}", e.getMessage(), e);
                    scheduleReconnect(attempt + 1);
                }
            }, delay, TimeUnit.SECONDS);
        }
    }
}
