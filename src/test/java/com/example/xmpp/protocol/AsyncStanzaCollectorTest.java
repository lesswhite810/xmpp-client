package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AsyncStanzaCollector 单元测试。
 *
 * @since 2026-02-27
 */
class AsyncStanzaCollectorTest {

    private StanzaFilter testFilter;
    private AsyncStanzaCollector collector;

    @BeforeEach
    void setUp() {
        // 创建一个只接受 IQ result 类型的过滤器
        testFilter = stanza -> {
            if (!(stanza instanceof Iq iq)) {
                return false;
            }
            return iq.getType() == Iq.Type.RESULT;
        };
        collector = new AsyncStanzaCollector(testFilter);
    }

    @Nested
    @DisplayName("构造器测试")
    class ConstructorTests {

        @Test
        @DisplayName("构造器应正确初始化 filter 和 future")
        void testConstructor() {
            assertNotNull(collector.getFuture());
            assertFalse(collector.getFuture().isDone());
        }
    }

    @Nested
    @DisplayName("processStanza 测试")
    class ProcessStanzaTests {

        @Test
        @DisplayName("匹配的节应被收集并完成 Future")
        void testMatchingStanzaCompletesFuture() {
            Iq matchingIq = new Iq.Builder(Iq.Type.RESULT)
                    .id("test-123")
                    .build();

            boolean result = collector.processStanza(matchingIq);

            assertTrue(result, "匹配的节应返回 true");
            assertTrue(collector.getFuture().isDone(), "Future 应该已完成");
            assertEquals(matchingIq, collector.getFuture().join());
        }

        @Test
        @DisplayName("不匹配的节应被忽略")
        void testNonMatchingStanzaIgnored() {
            Iq nonMatchingIq = new Iq.Builder(Iq.Type.GET)
                    .id("test-456")
                    .build();

            boolean result = collector.processStanza(nonMatchingIq);

            assertFalse(result, "不匹配的节应返回 false");
            assertFalse(collector.getFuture().isDone(), "Future 不应完成");
        }

        @Test
        @DisplayName("第一个匹配后后续节应被忽略")
        void testOnlyFirstMatchAccepted() {
            Iq firstMatch = new Iq.Builder(Iq.Type.RESULT).id("first").build();
            Iq secondMatch = new Iq.Builder(Iq.Type.RESULT).id("second").build();

            boolean result1 = collector.processStanza(firstMatch);
            boolean result2 = collector.processStanza(secondMatch);

            assertTrue(result1);
            assertFalse(result2, "后续匹配应被忽略");
            assertEquals(firstMatch, collector.getFuture().join());
        }
    }

    @Nested
    @DisplayName("Future 行为测试")
    class FutureBehaviorTests {

        @Test
        @DisplayName("Future 支持超时")
        void testFutureTimeout() {
            long startTime = System.currentTimeMillis();

            assertThrows(TimeoutException.class, () ->
                    collector.getFuture().get(100, TimeUnit.MILLISECONDS));

            long elapsed = System.currentTimeMillis() - startTime;
            assertTrue(elapsed >= 100 && elapsed < 500, "超时应在指定时间附近触发");
        }

        @Test
        @DisplayName("Future 支持取消")
        void testFutureCancel() {
            boolean cancelled = collector.getFuture().cancel(true);

            assertTrue(cancelled, "Future 应被成功取消");
            assertTrue(collector.getFuture().isCancelled(), "Future 状态应为已取消");
            assertTrue(collector.getFuture().isDone(), "已取消的 Future 也算完成");
        }

        @Test
        @DisplayName("已完成的 Future 不能再次完成")
        void testFutureCannotCompleteTwice() throws ExecutionException, InterruptedException {
            Iq firstIq = new Iq.Builder(Iq.Type.RESULT).id("first").build();
            Iq secondIq = new Iq.Builder(Iq.Type.RESULT).id("second").build();

            collector.processStanza(firstIq);

            // 尝试手动完成另一个值
            boolean manuallyCompleted = collector.getFuture().complete(secondIq);

            assertFalse(manuallyCompleted, "已完成的 Future 不能再次完成");
            assertEquals(firstIq, collector.getFuture().get());
        }
    }

    @Nested
    @DisplayName("过滤器测试")
    class FilterTests {

        @Test
        @DisplayName("空过滤器应接受所有节")
        void testAlwaysTrueFilter() {
            AsyncStanzaCollector alwaysAcceptCollector = new AsyncStanzaCollector(stanza -> true);

            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            assertTrue(alwaysAcceptCollector.processStanza(iq));
            assertTrue(alwaysAcceptCollector.getFuture().isDone());
        }

        @Test
        @DisplayName("空过滤器应拒绝所有节")
        void testAlwaysFalseFilter() {
            AsyncStanzaCollector alwaysRejectCollector = new AsyncStanzaCollector(stanza -> false);

            Iq iq = new Iq.Builder(Iq.Type.RESULT).build();

            assertFalse(alwaysRejectCollector.processStanza(iq));
            assertFalse(alwaysRejectCollector.getFuture().isDone());
        }
    }
}
