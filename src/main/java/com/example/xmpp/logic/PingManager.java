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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XMPP Ping 管理器。
 *
 * <p>负责客户端保活 Ping。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public final class PingManager {

    /**
     * 关联的 XMPP 连接。
     */
    private final XmppConnection connection;

    /**
     * 当前保活任务。
     */
    private volatile ScheduledFuture<?> keepAliveTask;

    /**
     * 保活任务状态变更锁。
     */
    private final ReentrantLock taskLock = new ReentrantLock();

    /**
     * Ping 间隔时间，单位为秒。
     */
    private volatile int pingIntervalSeconds = XmppConstants.DEFAULT_PING_INTERVAL_SECONDS;

    /**
     * 事件订阅取消回调。
     */
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
                ConnectionEventType.CLOSED, event -> stopKeepAlive(),
                ConnectionEventType.ERROR, event -> stopKeepAlive()
        ));
    }

    /**
     * 处理连接事件。
     *
     * @param event 连接事件
     */
    public void onEvent(ConnectionEvent event) {
        switch (event.eventType()) {
            case AUTHENTICATED -> startKeepAlive();
            case CLOSED, ERROR -> stopKeepAlive();
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
            if (unsubscribe == null) {
                log.debug("PingManager already shutdown, skipping interval reschedule");
                return;
            }
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
     */
    public void startKeepAlive() {
        taskLock.lock();
        try {
            if (unsubscribe == null) {
                log.debug("PingManager already shutdown, skipping keepalive start");
                return;
            }
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
        keepAliveTask = null;
        if (task != null) {
            task.cancel(true);
        }
    }

    /**
     * 关闭 PingManager。
     */
    public void shutdown() {
        taskLock.lock();
        try {
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
     */
    private void sendPing() {
        if (!connection.isConnected() || !connection.isAuthenticated()) {
            return;
        }

        String id = XmppConstants.generateStanzaId();
        Iq pingIq = PingIq.createPingRequest(id, connection.getConfig().getXmppServiceDomain());

        log.debug("Sending Keepalive Ping...");
        connection.sendIqPacketAsync(pingIq)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex instanceof CompletionException completionException
                                && completionException.getCause() != null
                                ? completionException.getCause()
                                : ex;
                        log.warn("Keepalive Ping failed - ErrorType: {}", cause.getClass().getSimpleName());
                        return;
                    }
                    log.debug("Keepalive Pong received.");
                });
    }
}
