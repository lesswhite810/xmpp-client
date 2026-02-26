package com.example.xmpp.logic;

import com.example.xmpp.ConnectionEvent;
import com.example.xmpp.XmppConnection;
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmppScheduler;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.StanzaListener;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.Ping;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * XMPP Ping 管理器（XEP-0199）。
 *
 * <p>实现 XMPP Ping 协议的双向 Ping 功能：</p>
 * <ul>
 * <li>响应服务端 Ping 请求（作为 StanzaListener）</li>
 * <li>定期发送 Keepalive Ping 保持连接活跃</li>
 * <li>支持自定义 Ping 间隔时间</li>
 * </ul>
 *
 * @since 2026-02-09
 */
@Slf4j
public class PingManager implements StanzaListener {

    /** 关联的 XMPP 连接 */
    private final XmppConnection connection;

    /** Ping 消息过滤器 */
    private final StanzaFilter pingFilter;

    /** 保活任务 */
    private ScheduledFuture<?> keepAliveTask;

    /** Ping 间隔时间（秒） */
    private volatile int pingIntervalSeconds = XmppConstants.DEFAULT_PING_INTERVAL_SECONDS;

    /**
     * 构造 PingManager。
     *
     * @param connection XMPP 连接实例
     */
    public PingManager(XmppConnection connection) {
        this.connection = connection;
        this.pingFilter = stanza -> {
            if (!(stanza instanceof Iq iq)) {
                return false;
            }
            if (iq.getType() != Iq.Type.GET) {
                return false;
            }
            return iq.getExtension(Ping.class).isPresent();
        };

        // 注册为 StanzaListener 响应服务端 Ping
        connection.addAsyncStanzaListener(this, pingFilter);

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
    public synchronized void startKeepAlive() {
        stopKeepAlive();

        keepAliveTask = XmppScheduler.getScheduler().scheduleWithFixedDelay(
                this::sendPing, pingIntervalSeconds, pingIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 停止保活任务。
     */
    public synchronized void stopKeepAlive() {
        if (keepAliveTask != null) {
            keepAliveTask.cancel(true);
            keepAliveTask = null;
        }
    }

    /**
     * 关闭 PingManager。
     */
    public synchronized void shutdown() {
        stopKeepAlive();
        log.debug("PingManager shutdown");
    }

    /**
     * 发送 Ping 请求。
     */
    private void sendPing() {
        if (!connection.isConnected() || !connection.isAuthenticated()) {
            return;
        }

        String id = "ping_" + System.currentTimeMillis();
        Iq pingIq = PingIq.createPingRequest(id, connection.getConfig().getXmppServiceDomain());

        log.debug("Sending Keepalive Ping...");
        connection.sendIqPacketAsync(pingIq)
                .orTimeout(XmppConstants.PING_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.warn("Keepalive Ping Failed: {}", ex.getMessage());
                    } else {
                        log.debug("Keepalive Pong received.");
                    }
                });
    }

    /**
     * 处理接收到的 Ping 请求并响应。
     *
     * @param stanza 接收到的节
     */
    @Override
    public void processStanza(XmppStanza stanza) {
        Iq iq = (Iq) stanza;
        log.debug("Received Ping from {}", iq.getFrom() != null ? iq.getFrom() : "Server");
        connection.sendStanza(Iq.createResultResponse(iq, null));
    }
}
