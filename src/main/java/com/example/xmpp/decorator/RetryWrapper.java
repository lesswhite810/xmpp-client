package com.example.xmpp.decorator;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 重试包装器。
 * 
 * <p>为操作添加重试功能，支持指数退避算法。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * XmppConnection connection = new RetryWrapper(
 *     new XmppTcpConnection(config),
 *     RetryPolicy.exponential()
 *         .baseDelay(1, TimeUnit.SECONDS)
 *         .maxDelay(30, TimeUnit.SECONDS)
 *         .maxAttempts(3));
 * }</pre>
 * 
 * @since 2026-03-02
 */
@Slf4j
public class RetryWrapper {

    private final XmppConnection delegate;
    private final RetryPolicy policy;

    public RetryWrapper(XmppConnection delegate, RetryPolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }

    /**
     * 获取被包装的连接。
     */
    public XmppConnection getDelegate() {
        return delegate;
    }

    /**
     * 发送 Stanza，带重试。
     */
    public void sendStanza(XmppStanza stanza) {
        execute(() -> {
            delegate.sendStanza(stanza);
            return null;
        });
    }

    /**
     * 执行操作，带重试。
     */
    public <T> T execute(Supplier<T> operation) {
        int attempt = 0;
        long delay = policy.baseDelayNanos;

        while (true) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempt++;
                
                if (attempt >= policy.maxAttempts) {
                    log.error("Retry failed after {} attempts", attempt);
                    throw new RuntimeException("Retry failed after " + attempt + " attempts", e);
                }
                
                if (!policy.retryable.test(e)) {
                    throw new RuntimeException("Non-retryable exception", e);
                }
                
                log.warn("Attempt {} failed: {}, retrying in {}ms", 
                    attempt, e.getMessage(), delay / 1_000_000);
                
                try {
                    Thread.sleep(delay, ThreadLocalRandom.current().nextInt(1_000_000));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                // 指数退避
                delay = Math.min(delay * 2, policy.maxDelayNanos);
            }
        }
    }

    /**
     * 重试策略配置。
     */
    public static class RetryPolicy {
        private long baseDelayNanos = TimeUnit.SECONDS.toNanos(1);
        private long maxDelayNanos = TimeUnit.SECONDS.toNanos(30);
        private int maxAttempts = 3;
        private java.util.function.Predicate<Exception> retryable = e -> true;

        private RetryPolicy() {}

        public static RetryPolicy exponential() {
            return new RetryPolicy();
        }

        public RetryPolicy baseDelay(long delay, TimeUnit unit) {
            this.baseDelayNanos = unit.toNanos(delay);
            return this;
        }

        public RetryPolicy maxDelay(long delay, TimeUnit unit) {
            this.maxDelayNanos = unit.toNanos(delay);
            return this;
        }

        public RetryPolicy maxAttempts(int max) {
            this.maxAttempts = max;
            return this;
        }

        public RetryPolicy retryable(java.util.function.Predicate<Exception> predicate) {
            this.retryable = predicate;
            return this;
        }
    }
}
