package com.example.xmpp.event;

import com.example.xmpp.XmppConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * XmppEventBus 行为测试。
 */
class XmppEventBusTest {

    private final XmppEventBus eventBus = XmppEventBus.getInstance();
    private final XmppConnection connection1 = mock(XmppConnection.class);
    private final XmppConnection connection2 = mock(XmppConnection.class);

    @AfterEach
    void tearDown() {
        eventBus.unsubscribeAll(connection1);
        eventBus.unsubscribeAll(connection2);
    }

    @Test
    @DisplayName("getInstance 应返回单例")
    void testSingleton() {
        assertSame(eventBus, XmppEventBus.getInstance());
    }

    @Test
    @DisplayName("订阅后应收到对应事件，取消订阅后不再收到")
    void testSubscribeAndUnsubscribe() {
        AtomicBoolean handled = new AtomicBoolean(false);

        Runnable unsubscribe = eventBus.subscribe(connection1, ConnectionEventType.CONNECTED,
                event -> handled.set(true));

        eventBus.publish(connection1, ConnectionEventType.CONNECTED);
        assertTrue(handled.get());

        handled.set(false);
        unsubscribe.run();
        eventBus.publish(connection1, ConnectionEventType.CONNECTED);

        assertFalse(handled.get());
    }

    @Test
    @DisplayName("publish 应将事件分发给同一连接的所有处理器")
    void testPublishFanOut() {
        AtomicInteger counter = new AtomicInteger();

        eventBus.subscribe(connection1, ConnectionEventType.CONNECTED, event -> counter.incrementAndGet());
        eventBus.subscribe(connection1, ConnectionEventType.CONNECTED, event -> counter.incrementAndGet());

        eventBus.publish(connection1, ConnectionEventType.CONNECTED);

        assertEquals(2, counter.get());
    }

    @Test
    @DisplayName("subscribeAll 应批量订阅并支持批量取消")
    void testSubscribeAll() {
        AtomicInteger connectedCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        Runnable unsubscribe = eventBus.subscribeAll(connection1, Map.of(
                ConnectionEventType.CONNECTED, event -> connectedCount.incrementAndGet(),
                ConnectionEventType.ERROR, event -> errorCount.incrementAndGet()
        ));

        eventBus.publish(connection1, ConnectionEventType.CONNECTED);
        eventBus.publish(connection1, ConnectionEventType.ERROR, new RuntimeException("boom"));

        assertEquals(1, connectedCount.get());
        assertEquals(1, errorCount.get());

        unsubscribe.run();
        eventBus.publish(connection1, ConnectionEventType.CONNECTED);
        eventBus.publish(connection1, ConnectionEventType.ERROR, new RuntimeException("boom"));

        assertEquals(1, connectedCount.get());
        assertEquals(1, errorCount.get());
    }

    @Test
    @DisplayName("空 handlers 的批量订阅应返回无操作取消器")
    void testSubscribeAllWithEmptyHandlers() {
        Runnable unsubscribe = eventBus.subscribeAll(connection1, Map.of());

        assertDoesNotThrow(unsubscribe::run);
        eventBus.publish(connection1, ConnectionEventType.CONNECTED);
    }

    @Test
    @DisplayName("不同连接的事件应相互隔离")
    void testConnectionIsolation() {
        AtomicBoolean firstHandled = new AtomicBoolean(false);
        AtomicBoolean secondHandled = new AtomicBoolean(false);

        eventBus.subscribe(connection1, ConnectionEventType.CONNECTED, event -> firstHandled.set(true));
        eventBus.subscribe(connection2, ConnectionEventType.CONNECTED, event -> secondHandled.set(true));

        eventBus.publish(connection1, ConnectionEventType.CONNECTED);

        assertTrue(firstHandled.get());
        assertFalse(secondHandled.get());
    }

    @Test
    @DisplayName("单个处理器抛异常不应影响其他处理器")
    void testHandlerExceptionIsolation() {
        AtomicBoolean handled = new AtomicBoolean(false);

        eventBus.subscribe(connection1, ConnectionEventType.CONNECTED, event -> {
            throw new RuntimeException("boom");
        });
        eventBus.subscribe(connection1, ConnectionEventType.CONNECTED, event -> handled.set(true));

        assertDoesNotThrow(() -> eventBus.publish(connection1, ConnectionEventType.CONNECTED));
        assertTrue(handled.get());
    }

    @Test
    @DisplayName("带错误的事件应携带原始异常")
    void testEventWithError() {
        RuntimeException error = new RuntimeException("boom");
        AtomicBoolean handled = new AtomicBoolean(false);

        eventBus.subscribe(connection1, ConnectionEventType.ERROR, event -> {
            handled.set(true);
            assertSame(connection1, event.connection());
            assertEquals(ConnectionEventType.ERROR, event.eventType());
            assertSame(error, event.error());
        });

        eventBus.publish(connection1, ConnectionEventType.ERROR, error);

        assertTrue(handled.get());
    }

    @Test
    @DisplayName("null 连接的批量订阅应抛出异常")
    void testSubscribeAllNullConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> eventBus.subscribeAll(null, Map.of(ConnectionEventType.CONNECTED, event -> {})));
    }

    @Test
    @DisplayName("取消不存在连接的订阅应安全返回")
    void testUnsubscribeAllForUnknownConnection() {
        assertDoesNotThrow(() -> eventBus.unsubscribeAll(connection1));
    }
}
