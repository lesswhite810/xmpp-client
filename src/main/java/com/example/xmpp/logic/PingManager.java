package com.example.xmpp.logic;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.XmppConnection;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmppScheduler;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XMPP Ping 管理器（XEP-0199）。
 *
 * <p>实现 XMPP Ping 协议的客户端 Ping 功能：</p>
 * <ul>
 * <li>定期发送 Keepalive Ping 保持连接活跃</li>
 * <li>支持自定义 Ping 间隔时间</li>
 * </ul>
 *
 * <p>服务端 Ping 请求由 {@link PingIqRequestHandler} 处理。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public class PingManager {

    /** 关联的 XMPP 连接 */
    private final XmppConnection connection;

    /** 保活任务 */
    private volatile ScheduledFuture<?> keepAliveTask;

    /** 任务操作锁 */
    private final ReentrantLock taskLock = new ReentrantLock();

    /** Ping 间隔时间（秒） */
    private volatile int pingIntervalSeconds = XmppConstants.DEFAULT_PING_INTERVAL_SECONDS;

    /**
     * 构造 PingManager。
     *
     * @param connection XMPP 连接实例
     */
    public PingManager(XmppConnection connection) {
        this.connection = connection;

        // 监听连接事件
        connection.addConnectionListener(event -> {
            switch (event) {
                case ConnectionEvent.AuthenticatedEvent e -> startKeepAlive();
                case ConnectionEvent.ConnectionClosedEvent e -> shutdown();
                case ConnectionEvent.ConnectionClosedOnErrorEvent e -> shutdown();
                default -> { }
            }
        });
    }

    /**
     * 设置 Ping 间隔时间。
     *
     * @param seconds 间隔时间（秒）
     */
    public void setPingInterval(int seconds) {
        this.pingIntervalSeconds = seconds;
        if (keepAliveTask != null && !keepAliveTask.isCancelled()) {
            startKeepAlive();
        }
    }

    /**
     * 启动保活任务。
     */
    public void startKeepAlive() {
        taskLock.lock();
        try {
            stopKeepAliveInternal();

            keepAliveTask = XmppScheduler.getScheduler().scheduleWithFixedDelay(
                    this::sendPing, pingIntervalSeconds, pingIntervalSeconds, TimeUnit.SECONDS);
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * 停止保活任务。
     */
    public void stopKeepAlive() {
        taskLock.lock();
        try {
            stopKeepAliveInternal();
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * 内部停止保活任务（调用者需持有锁）。
     */
    private void stopKeepAliveInternal() {
        ScheduledFuture<?> task = keepAliveTask;
        if (task != null) {
            task.cancel(true);
            keepAliveTask = null;
        }
    }

    /**
     * 关闭 PingManager。
     */
    public void shutdown() {
        taskLock.lock();
        try {
            stopKeepAliveInternal();
            log.debug("PingManager shutdown");
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * 发送 Ping 请求。
     */
    private void sendPing() {
        if (!connection.isConnected() || !connection.isAuthenticated()) {
            return;
        }

        String id = XmppConstants.generateStanzaId();
        Iq pingIq = PingIq.createPingRequest(id, connection.getConfig().getXmppServiceDomain());

        log.debug("Sending Keepalive Ping...");
        connection.sendIqPacketAsync(pingIq)
                .thenAccept(res -> log.debug("Keepalive Pong received."))
                .exceptionally(ex -> {
                    log.warn("Keepalive Ping failed: {}", ex.getMessage());
                    return null;
                });
    }
}
