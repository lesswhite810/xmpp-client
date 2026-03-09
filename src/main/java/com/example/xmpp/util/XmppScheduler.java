package com.example.xmpp.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * XMPP 全局调度器。
 *
 * <p>提供全局共享的调度线程池。</p>
 *
 * @since 2026-02-26
 */
@Slf4j
public final class XmppScheduler {

    private static final int CORE_POOL_SIZE = 2;

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static final ScheduledThreadPoolExecutor SCHEDULER = createScheduler();

    private XmppScheduler() {
    }

    /**
     * 创建调度线程池。
     *
     * @return 调度线程池实例
     */
    private static ScheduledThreadPoolExecutor createScheduler() {
        ThreadFactory factory = Thread.ofPlatform()
                .name("xmpp-scheduler-", 0)
                .daemon(true)
                .factory();

        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                CORE_POOL_SIZE, factory, handler);

        executor.setRemoveOnCancelPolicy(true);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

        return executor;
    }

    /**
     * 获取全局调度器。
     *
     * @return 调度线程池
     */
    public static ScheduledExecutorService getScheduler() {
        return SCHEDULER;
    }

    /**
     * 优雅关闭调度器。
     */
    public static void shutdown() {
        log.debug("Shutting down XmppScheduler...");

        SCHEDULER.shutdown();

        try {
            if (!SCHEDULER.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Scheduler did not terminate in time, forcing shutdown...");
                SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted, forcing shutdown...");
            SCHEDULER.shutdownNow();
        }

        log.debug("XmppScheduler shutdown complete");
    }

    /**
     * 检查调度器是否已关闭。
     *
     * @return 已关闭返回 true
     */
    public static boolean isShutdown() {
        return SCHEDULER.isShutdown();
    }
}
