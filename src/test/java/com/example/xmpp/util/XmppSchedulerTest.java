package com.example.xmpp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XmppScheduler 测试。
 *
 * @since 2026-03-20
 */
class XmppSchedulerTest {

    @Test
    @DisplayName("createScheduler 应创建带预期策略的调度器")
    void testCreateSchedulerConfiguration() throws Exception {
        Method method = XmppScheduler.class.getDeclaredMethod("createScheduler");
        method.setAccessible(true);

        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) method.invoke(null);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> threadRef = new AtomicReference<>();
        executor.execute(() -> {
            threadRef.set(Thread.currentThread());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(executor.getRemoveOnCancelPolicy());
        assertFalse(executor.getContinueExistingPeriodicTasksAfterShutdownPolicy());
        assertFalse(executor.getExecuteExistingDelayedTasksAfterShutdownPolicy());
        assertNotNull(threadRef.get());
        assertTrue(threadRef.get().getName().startsWith("xmpp-scheduler-"));
        assertTrue(threadRef.get().isDaemon());

        executor.shutdownNow();
    }

    @Test
    @DisplayName("全局调度器应为单例且默认未关闭")
    void testGetSchedulerReturnsSingleton() {
        assertSame(XmppScheduler.getScheduler(), XmppScheduler.getScheduler());
        assertFalse(XmppScheduler.isShutdown());
    }
}
