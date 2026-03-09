package com.example.xmpp.logic;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.handler.PingIqRequestHandler;
import com.example.xmpp.XmppConnection;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmppScheduler;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
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

    /** 事件订阅取消回调 */
    private Runnable unsubscribe;

    /**
     * 构造 PingManager。
     *
     * @param connection XMPP 连接实例
     */
    public PingManager(XmppConnection connection) {
        this.connection = connection;

        XmppEventBus eventBus = XmppEventBus.getInstance();

        unsubscribe = eventBus.subscribeAll(connection, Map.of(
                ConnectionEventType.AUTHENTICATED, event -> startKeepAlive(),
                ConnectionEventType.CLOSED, event -> shutdown(),
                ConnectionEventType.ERROR, event -> shutdown()
        ));
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
            case AUTHENTICATED -> startKeepAlive();
            case CLOSED, ERROR -> shutdown();
        }
    }

    /**
     * 设置 Ping 间隔时间。
     *
     * <p>如果当前有任务在运行，会先停止旧任务再启动新任务。</p>
     *
     * @param seconds 间隔时间（秒），必须为正数
     * @throws IllegalArgumentException 如果 seconds 不为正数
     */
    public void setPingInterval(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Ping interval must be positive: " + seconds);
        }
        taskLock.lock();
        try {
            this.pingIntervalSeconds = seconds;
            /** 如果任务存在，先停止再启动 */
            if (keepAliveTask != null && !keepAliveTask.isCancelled()) {
                stopKeepAliveInternal();
                keepAliveTask = XmppScheduler.getScheduler().scheduleWithFixedDelay(
                        this::sendPing, pingIntervalSeconds, pingIntervalSeconds, TimeUnit.SECONDS);
            }
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * 启动保活任务。
     *
     * <p>发送定期 Ping 请求以保持 XMPP 连接活跃。</p>
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
     *
     * <p>取消定期 Ping 请求，但保持与 XMPP 服务器的连接。</p>
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
     *
     * <p>停止保活任务并释放相关资源。此方法不会关闭 XMPP 连接。</p>
     */
    public void shutdown() {
        taskLock.lock();
        try {
            /** 取消事件订阅 */
            if (unsubscribe != null) {
                unsubscribe.run();
                unsubscribe = null;
            }
            stopKeepAliveInternal();
            log.debug("PingManager shutdown");
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * 发送 Ping 请求。
     *
     * <p>向 XMPP 服务器发送 Keepalive Ping 以检测连接状态。</p>
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
