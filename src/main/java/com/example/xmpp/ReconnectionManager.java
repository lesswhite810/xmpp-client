package com.example.xmpp;

import com.example.xmpp.logic.PingManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 自动重连管理器。
 *
 * <p>实现连接断开后的自动重连逻辑，p>
 *
 * <p>功能特性：</p>
 * <ul>
 * <li>监听连接错误事件并自动触发重连</li>
 * <li>使用指数退避算法（基础延迟 2 秒，最大延迟 60 秒）</li>
 * <li>添加随机抖动避免雷鸣羊群效应</li>
 * <li>支持启用/禁用重连功能</li>
 * <li>正常关闭连接时不触发重连</li>
 * </ul>
 *
 * <p>使用单例模式，每个连接对应一个 ReconnectionManager 实例。</p>
 *
 * @since 2026-02-09
 */
public class ReconnectionManager implements ConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(ReconnectionManager.class);

    /** 基础重连延迟（秒） */
    private static final int BASE_DELAY_SECONDS = XmppConstants.RECONNECT_BASE_DELAY_SECONDS;

    /** 最大重连延迟（秒） */
    private static final int MAX_DELAY_SECONDS = XmppConstants.RECONNECT_MAX_DELAY_SECONDS;

    /** 最大重连尝试次数 */
    private static final int MAX_RECONNECT_ATTEMPTS = XmppConstants.MAX_RECONNECT_ATTEMPTS;

    /** 每个连接的 ReconnectionManager 实例缓存 */
    private static final Map<XmppConnection, ReconnectionManager> INSTANCES = new ConcurrentHashMap<>();

    /** 关联的 XMPP 连接 */
    private final XmppConnection connection;

    /** 当前重连任务 */
    private volatile ScheduledFuture<?> currentTask;

    /** 是否启用自动重连 */
    private volatile boolean enabled = true;

    /** 随机数生成器（用于添加抖动） */
    private final Random random = new Random();

    /** 当前连续重连尝试次数 */
    private volatile int attemptCount = 0;

    /**
     * 私有构造器。
     *
     * @param connection XMPP 连接实例
     */
    private ReconnectionManager(XmppConnection connection) {
        this.connection = connection;
    }

    /**
     * 获取指定连接的 ReconnectionManager 实例。
     *
     * <p>如果实例不存在则自动创建，使用单例模式确保每个连接只有一个 ReconnectionManager。</p>
     *
     * @param connection XMPP 连接实例
     * @return 对应的 ReconnectionManager 实例
     */
    public static ReconnectionManager getInstanceFor(XmppConnection connection) {
        return INSTANCES.computeIfAbsent(connection, conn -> {
            ReconnectionManager manager = new ReconnectionManager(conn);
            conn.addConnectionListener(manager);
            return manager;
        });
    }

    /**
     * 启用自动重连。
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
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }

    /**
     * 处理连接事件。
     *
     * @param event 连接事件
     */
    @Override
    public void onEvent(ConnectionEvent event) {
        switch (event) {
            case ConnectionEvent.ConnectedEvent e -> onConnected();
            case ConnectionEvent.AuthenticatedEvent e -> { /* Ready */ }
            case ConnectionEvent.ConnectionClosedEvent e -> onConnectionClosed();
            case ConnectionEvent.ConnectionClosedOnErrorEvent e -> onConnectionClosedOnError(e.error());
            }
    }

    private void onConnected() {
        attemptCount = 0;
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
    }

    private void onConnectionClosed() {
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        // 从实例缓存中移除
        INSTANCES.remove(connection);
    }

    private void onConnectionClosedOnError(Exception e) {
        if (!enabled) {
            // 从实例缓存中移除
            INSTANCES.remove(connection);
            return;
        }
        log.info("Connection closed on error. Starting reconnection...", e);
        scheduleReconnect(0);
    }

    /**
     * 调度重连任务。
     *
     * <p>使用指数退避算法计算延迟时间，并添加随机抖动避免雷鸣羊群效应。</p>
     *
     * @param attempt 当前重连尝试次数（从 0 开始）
     */
    private void scheduleReconnect(int attempt) {
        if (connection.isConnected()) {
            attemptCount = 0;
            return;
        }

        if (attempt >= MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnection attempts ({}) reached, stopping reconnection", MAX_RECONNECT_ATTEMPTS);
            attemptCount = 0;
            INSTANCES.remove(connection);
            return;
        }

        attemptCount = attempt + 1;
        int delay = Math.min(BASE_DELAY_SECONDS * (1 << attempt), MAX_DELAY_SECONDS);
        delay += random.nextInt(Math.max(1, delay / 4));

        log.info("Reconnecting in {} seconds (Attempt {}/{})...", delay, attempt + 1, MAX_RECONNECT_ATTEMPTS);

        currentTask = XmppScheduler.getScheduler().schedule(() -> {
            try {
                if (connection.isConnected()) {
                    attemptCount = 0;
                    return;
                }
                log.info("Retrying connection...");
                connection.resetHandlerState();
                connection.connect();
                attemptCount = 0;
                PingManager.getInstanceFor(connection).startKeepAlive();
            } catch (com.example.xmpp.exception.XmppException e) {
                log.error("Reconnection failed: {}", e.getMessage());
                scheduleReconnect(attempt + 1);
            } catch (RuntimeException e) {
                log.error("Unexpected runtime error during reconnection: {}", e.getMessage(), e);
                scheduleReconnect(attempt + 1);
            }
        }, delay, TimeUnit.SECONDS);
    }
}
