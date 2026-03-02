package com.example.xmpp.logic;

import com.example.xmpp.event.ConnectionEvent;
import com.example.xmpp.event.ConnectionListener;
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
public class PingManager implements ConnectionListener {

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

        // 注册连接监听器
        connection.addConnectionListener(this);
    }

    /**
     * 处理连接事件。
     *
     * @param event 连接事件
     */
    @Override
    public void onEvent(ConnectionEvent event) {
        switch (event) {
            case ConnectionEvent.AuthenticatedEvent e -> startKeepAlive();
            case ConnectionEvent.ConnectionClosedEvent e -> shutdown();
            case ConnectionEvent.ConnectionClosedOnErrorEvent e -> shutdown();
            default -> { }
        }
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
        Iq pingIq = PingIq.createPingRequest(id, connection.getConfig().getConnection().getXmppServiceDomain());

        log.debug("Sending Keepalive Ping...");
        connection.sendIqPacketAsync(pingIq)
                .thenAccept(res -> log.debug("Keepalive Pong received."))
                .exceptionally(ex -> {
                    log.warn("Keepalive Ping failed: {}", ex.getMessage());
                    return null;
                });
    }
}
