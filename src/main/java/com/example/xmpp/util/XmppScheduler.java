package com.example.xmpp.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.SynchronousQueue;

/**
 * XMPP 全局调度器。
 *
 * <p>提供全局共享的调度线程池和虚拟线程执行器。</p>
 *
 * @since 2026-02-26
 */
@Slf4j
public final class XmppScheduler {

    /** 调度线程池核心线程数 */
    private static final int CORE_POOL_SIZE = 2;

    /** 关闭超时时间（秒） */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    /** 全局调度线程池 */
    private static final ScheduledThreadPoolExecutor SCHEDULER = createScheduler();

    /** 虚拟线程执行器 */
    private static final ExecutorService VIRTUAL_EXECUTOR = createVirtualExecutor();

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
     * 创建虚拟线程执行器。
     *
     * @return 虚拟线程执行器实例
     */
    private static ExecutorService createVirtualExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("xmpp-virtual-", 0)
                .factory();

        return new ThreadPoolExecutor(
                0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
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
     * 获取虚拟线程执行器。
     *
     * @return 虚拟线程执行器
     */
    public static ExecutorService getVirtualExecutor() {
        return VIRTUAL_EXECUTOR;
    }

    /**
     * 在虚拟线程中执行任务。
     *
     * @param task 要执行的任务
     */
    public static void executeVirtual(Runnable task) {
        VIRTUAL_EXECUTOR.execute(task);
    }

    /**
     * 优雅关闭调度器。
     */
    public static void shutdown() {
        log.debug("Shutting down XmppScheduler...");

        VIRTUAL_EXECUTOR.shutdown();
        SCHEDULER.shutdown();

        try {
            if (!SCHEDULER.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Scheduler did not terminate in time, forcing shutdown...");
                SCHEDULER.shutdownNow();
            }
            if (!VIRTUAL_EXECUTOR.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Virtual executor did not terminate in time, forcing shutdown...");
                VIRTUAL_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted, forcing shutdown...");
            SCHEDULER.shutdownNow();
            VIRTUAL_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
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
