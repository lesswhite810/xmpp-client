package com.example.xmpp.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * XMPP 全局调度器。
 *
 * <p>提供全局共享的 {@link ScheduledExecutorService}，用于所有定时任务：</p>
 * <ul>
 *   <li>PingManager 的保活任务</li>
 *   <li>ReconnectionManager 的重连任务</li>
 *   <li>其他需要定时执行的任务</li>
 * </ul>
 *
 * <p>使用 JDK 21 虚拟线程执行任务，减少线程资源占用。</p>
 *
 * @since 2026-02-26
 */
public final class XmppScheduler {

    /** 全局共享的调度器 */
    private static final ScheduledExecutorService SCHEDULER = createScheduler();

    private XmppScheduler() {
        // 工具类不允许实例化
    }

    /**
     * 创建调度器。
     *
     * <p>使用虚拟线程工厂，定时任务在虚拟线程中执行。</p>
     *
     * @return ScheduledExecutorService 实例
     */
    private static ScheduledExecutorService createScheduler() {
        ThreadFactory virtualThreadFactory = Thread.ofVirtual()
                .name("xmpp-scheduler-", 0)
                .factory();
        return Executors.newScheduledThreadPool(1, virtualThreadFactory);
    }

    /**
     * 获取全局调度器。
     *
     * @return 共享的 ScheduledExecutorService
     */
    public static ScheduledExecutorService getScheduler() {
        return SCHEDULER;
    }

    /**
     * 关闭调度器。
     *
     * <p>通常在应用程序退出时调用。</p>
     */
    public static void shutdown() {
        SCHEDULER.shutdown();
    }
}
