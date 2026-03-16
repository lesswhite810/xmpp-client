package com.example.xmpp.event;

import com.example.xmpp.XmppConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * XmppEventBus 单元测试。
 */
class XmppEventBusTest {

    private XmppEventBus eventBus;
    private XmppConnection mockConnection1;
    private XmppConnection mockConnection2;

    @BeforeEach
    void setUp() {
        eventBus = XmppEventBus.getInstance();
        eventBus.clear();

        // 创建 mock 连接
        mockConnection1 = mock(XmppConnection.class);
        mockConnection2 = mock(XmppConnection.class);
    }

    @Test
    @DisplayName("单例实例应返回相同对象")
    void testSingleton() {
        XmppEventBus instance1 = XmppEventBus.getInstance();
        XmppEventBus instance2 = XmppEventBus.getInstance();

        assertSame(instance1, instance2);
    }

    // 订阅测试

    @Test
    @DisplayName("应正确订阅事件")
    void testSubscribe() {
        AtomicBoolean handled = new AtomicBoolean(false);

        Runnable unsubscribe = eventBus.subscribe(
                mockConnection1,
                ConnectionEventType.CONNECTED,
                event -> handled.set(true));

        assertTrue(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.CONNECTED));
        assertEquals(1, eventBus.getSubscriberCount(mockConnection1, ConnectionEventType.CONNECTED));

        // 触发事件
        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);

        assertTrue(handled.get());

        // 取消订阅
        unsubscribe.run();
        assertFalse(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.CONNECTED));
    }

    @Test
    @DisplayName("应正确发布事件")
    void testPublish() {
        AtomicInteger counter = new AtomicInteger(0);

        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> counter.incrementAndGet());
        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> counter.incrementAndGet());

        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);

        assertEquals(2, counter.get());
    }

    @Test
    @DisplayName("应正确取消订阅")
    void testUnsubscribe() {
        AtomicBoolean handled = new AtomicBoolean(false);
        Consumer<ConnectionEvent> handler = event -> handled.set(true);

        // 使用 subscribe 返回的 Runnable 取消订阅
        Runnable unsub = eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, handler);
        unsub.run();

        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);

        assertFalse(handled.get());
    }

    @Test
    @DisplayName("无订阅者时发布事件应正常返回")
    void testPublishNoSubscribers() {
        assertDoesNotThrow(() ->
            eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED));
    }

    @Test
    @DisplayName("清除所有订阅者应成功")
    void testClear() {
        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> {});
        eventBus.subscribe(mockConnection1, ConnectionEventType.AUTHENTICATED, event -> {});

        eventBus.clear();

        assertFalse(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.CONNECTED));
        assertFalse(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.AUTHENTICATED));
    }

    @Test
    @DisplayName("不同事件类型应相互独立")
    void testIndependentEventTypes() {
        AtomicInteger connectedCount = new AtomicInteger(0);
        AtomicInteger authCount = new AtomicInteger(0);

        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> connectedCount.incrementAndGet());
        eventBus.subscribe(mockConnection1, ConnectionEventType.AUTHENTICATED, event -> authCount.incrementAndGet());

        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);
        eventBus.publish(mockConnection1, ConnectionEventType.AUTHENTICATED);

        assertEquals(1, connectedCount.get());
        assertEquals(1, authCount.get());
    }

    @Test
    @DisplayName("多个订阅者时一个失败不应影响其他")
    void testHandlerExceptionIsolation() {
        AtomicBoolean firstHandlerCalled = new AtomicBoolean(false);
        AtomicBoolean thirdHandlerCalled = new AtomicBoolean(false);

        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> {
            throw new RuntimeException("Intentional error");
        });
        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> firstHandlerCalled.set(true));
        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> thirdHandlerCalled.set(true));

        assertDoesNotThrow(() -> eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED));

        assertTrue(firstHandlerCalled.get());
        assertTrue(thirdHandlerCalled.get());
    }

    @Test
    @DisplayName("获取不存在的订阅者数量应返回 0")
    void testGetSubscriberCountNonexistent() {
        assertEquals(0, eventBus.getSubscriberCount(mockConnection1, ConnectionEventType.CONNECTED));
    }

    // 连接隔离测试

    @Test
    @DisplayName("不同连接的事件应相互独立")
    void testIndependentConnections() {
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> count1.incrementAndGet());
        eventBus.subscribe(mockConnection2, ConnectionEventType.CONNECTED, event -> count2.incrementAndGet());

        // 向 connection1 发布
        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);
        // 向 connection2 发布
        eventBus.publish(mockConnection2, ConnectionEventType.CONNECTED);

        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    @DisplayName("只应接收指定连接的事件")
    void testConnectionSpecificEvents() {
        AtomicBoolean handled1 = new AtomicBoolean(false);
        AtomicBoolean handled2 = new AtomicBoolean(false);

        // 为 connection1 订阅
        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> handled1.set(true));
        // 为 connection2 订阅
        eventBus.subscribe(mockConnection2, ConnectionEventType.CONNECTED, event -> handled2.set(true));

        // 只向 connection1 发布
        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);

        assertTrue(handled1.get());
        assertFalse(handled2.get());
    }

    @Test
    @DisplayName("应正确取消特定连接的所有订阅")
    void testUnsubscribeAllForConnection() {
        AtomicBoolean handled1 = new AtomicBoolean(false);
        AtomicBoolean handled2 = new AtomicBoolean(false);

        eventBus.subscribe(mockConnection1, ConnectionEventType.CONNECTED, event -> handled1.set(true));
        eventBus.subscribe(mockConnection1, ConnectionEventType.AUTHENTICATED, event -> handled2.set(true));
        // connection2 的订阅
        eventBus.subscribe(mockConnection2, ConnectionEventType.CONNECTED, event -> {});

        // 只取消 connection1 的所有订阅
        eventBus.unsubscribeAll(mockConnection1);

        // connection1 的订阅应该被取消
        assertEquals(0, eventBus.getTotalSubscriberCount(mockConnection1));
        // connection2 的订阅应该还在
        assertEquals(1, eventBus.getTotalSubscriberCount(mockConnection2));

        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);
        eventBus.publish(mockConnection1, ConnectionEventType.AUTHENTICATED);

        assertFalse(handled1.get());
        assertFalse(handled2.get());
    }

    @Test
    @DisplayName("订阅返回的 Runnable 应可取消订阅")
    void testUnsubscribeRunnable() {
        AtomicBoolean handled = new AtomicBoolean(false);

        Runnable unsub = eventBus.subscribe(
                mockConnection1,
                ConnectionEventType.CONNECTED,
                event -> handled.set(true));

        // 第一次取消
        unsub.run();

        // 再次取消应该没问题
        unsub.run();

        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);

        assertFalse(handled.get());
    }

    @Test
    @DisplayName("带错误的事件应正确传递错误信息")
    void testEventWithError() {
        AtomicBoolean handled = new AtomicBoolean(false);
        RuntimeException testError = new RuntimeException("Test error");

        eventBus.subscribe(mockConnection1, ConnectionEventType.ERROR, event -> {
            handled.set(true);
            assertEquals(testError, event.error());
        });

        eventBus.publish(mockConnection1, ConnectionEventType.ERROR, testError);

        assertTrue(handled.get());
    }

    @Test
    @DisplayName("ConnectionEvent 对象应正确包含事件信息")
    void testConnectionEventObject() {
        AtomicBoolean handled = new AtomicBoolean(false);
        RuntimeException testError = new RuntimeException("Test error");

        eventBus.subscribe(mockConnection1, ConnectionEventType.ERROR, event -> {
            handled.set(true);
            assertEquals(mockConnection1, event.connection());
            assertEquals(ConnectionEventType.ERROR, event.eventType());
            assertEquals(testError, event.error());
        });

        eventBus.publish(mockConnection1, ConnectionEventType.ERROR, testError);

        assertTrue(handled.get());

    }

    // 批量订阅测试

    @Test
    @DisplayName("应正确批量订阅多个事件")
    void testSubscribeAll() {
        AtomicInteger connectedCount = new AtomicInteger(0);
        AtomicInteger authCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        Runnable unsubscribe = eventBus.subscribeAll(mockConnection1, Map.of(
                ConnectionEventType.CONNECTED, event -> connectedCount.incrementAndGet(),
                ConnectionEventType.AUTHENTICATED, event -> authCount.incrementAndGet(),
                ConnectionEventType.ERROR, event -> errorCount.incrementAndGet()
        ));

        // 验证订阅成功
        assertTrue(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.CONNECTED));
        assertTrue(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.AUTHENTICATED));
        assertTrue(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.ERROR));

        // 发布事件
        eventBus.publish(mockConnection1, ConnectionEventType.CONNECTED);
        eventBus.publish(mockConnection1, ConnectionEventType.AUTHENTICATED);
        eventBus.publish(mockConnection1, ConnectionEventType.ERROR, new RuntimeException("test"));

        assertEquals(1, connectedCount.get());
        assertEquals(1, authCount.get());
        assertEquals(1, errorCount.get());

        // 批量取消
        unsubscribe.run();

        assertFalse(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.CONNECTED));
        assertFalse(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.AUTHENTICATED));
        assertFalse(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.ERROR));
    }

    @Test
    @DisplayName("空 handlers 映射应返回空 Runnable")
    void testSubscribeAllEmptyHandlers() {
        Runnable unsubscribe = eventBus.subscribeAll(mockConnection1, Map.of());

        // 不应有订阅
        assertFalse(eventBus.hasSubscribers(mockConnection1, ConnectionEventType.CONNECTED));

        // 空 Runnable 不应抛异常
        assertDoesNotThrow(unsubscribe::run);
    }

    @Test
    @DisplayName("null 连接应抛出异常")
    void testSubscribeAllNullConnection() {
        assertThrows(IllegalArgumentException.class, () ->
                eventBus.subscribeAll(null, Map.of(ConnectionEventType.CONNECTED, event -> {})));
    }

    @Nested
    @DisplayName("ConnectionEvent 测试")
    class ConnectionEventTests {

        @Test
        @DisplayName("ConnectionEvent 构造器应设置所有属性")
        void testConnectionEventConstructor() {
            ConnectionEvent event = new ConnectionEvent(mockConnection1, ConnectionEventType.CONNECTED);

            assertEquals(mockConnection1, event.connection());
            assertEquals(ConnectionEventType.CONNECTED, event.eventType());
            assertNull(event.error());
        }

        @Test
        @DisplayName("ConnectionEvent 构造器应支持带错误")
        void testConnectionEventConstructorWithError() {
            Exception testError = new RuntimeException("Test error");
            ConnectionEvent event = new ConnectionEvent(mockConnection1, ConnectionEventType.ERROR, testError);

            assertEquals(mockConnection1, event.connection());
            assertEquals(ConnectionEventType.ERROR, event.eventType());
            assertEquals(testError, event.error());
        }

        @Test
        @DisplayName("ConnectionEvent.toString 无错误时应生成正确格式")
        void testConnectionEventToStringNoError() {
            ConnectionEvent event = new ConnectionEvent(mockConnection1, ConnectionEventType.CONNECTED);
            String str = event.toString();

            assertTrue(str.contains("ConnectionEvent"));
            assertTrue(str.contains("CONNECTED"));
            assertFalse(str.contains("error="));
        }

        @Test
        @DisplayName("ConnectionEvent.toString 有错误时应生成正确格式")
        void testConnectionEventToStringWithError() {
            Exception testError = new RuntimeException("Test error message");
            ConnectionEvent event = new ConnectionEvent(mockConnection1, ConnectionEventType.ERROR, testError);
            String str = event.toString();

            assertTrue(str.contains("ConnectionEvent"));
            assertTrue(str.contains("ERROR"));
            assertTrue(str.contains("error="));
            assertTrue(str.contains("Test error message"));
        }
    }
}
