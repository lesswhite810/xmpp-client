package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppNetworkException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.net.ConnectException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * Main 测试。
 *
 * @since 2026-03-20
 */
class MainTest {

    @Test
    @DisplayName("main 未传参时应使用默认配置")
    void testMainUsesDefaultArguments() {
        AtomicReference<XmppClientConfig> configRef = new AtomicReference<>();
        XmppNetworkException cause = new XmppNetworkException("connect failed");

        try (MockedConstruction<XmppTcpConnection> ignored = Mockito.mockConstruction(
                XmppTcpConnection.class,
                (mock, context) -> {
                    configRef.set((XmppClientConfig) context.arguments().getFirst());
                    doThrow(cause).when(mock).connect();
                })) {
            RuntimeException exception = assertThrows(RuntimeException.class, () -> Main.main(new String[0]));

            assertEquals("XMPP connection failed", exception.getMessage());
            assertNull(exception.getCause());
        }

        XmppClientConfig config = configRef.get();
        assertNotNull(config);
        assertEquals("example.com", config.getXmppServiceDomain());
        assertEquals("user", config.getUsername());
        assertArrayEquals("password".toCharArray(), config.getPassword());
        assertEquals(XmppClientConfig.SecurityMode.DISABLED, config.getSecurityMode());
        assertTrue(config.isReconnectionEnabled());
    }

    @Test
    @DisplayName("main 传入三个参数时应使用用户配置")
    void testMainUsesProvidedArguments() {
        AtomicReference<XmppClientConfig> configRef = new AtomicReference<>();
        XmppNetworkException cause = new XmppNetworkException("connect failed");

        try (MockedConstruction<XmppTcpConnection> ignored = Mockito.mockConstruction(
                XmppTcpConnection.class,
                (mock, context) -> {
                    configRef.set((XmppClientConfig) context.arguments().getFirst());
                    doThrow(cause).when(mock).connect();
                })) {
            assertThrows(RuntimeException.class, () -> Main.main(new String[] {"xmpp.test", "alice", "secret"}));
        }

        XmppClientConfig config = configRef.get();
        assertNotNull(config);
        assertEquals("xmpp.test", config.getXmppServiceDomain());
        assertEquals("alice", config.getUsername());
        assertArrayEquals("secret".toCharArray(), config.getPassword());
    }

    @Test
    @DisplayName("runClient 在 sleep 被中断时应正常返回")
    void testRunClientReturnsWhenInterrupted() throws Exception {
        try (MockedConstruction<XmppTcpConnection> ignored = Mockito.mockConstruction(
                XmppTcpConnection.class,
                (mock, context) -> doAnswer(invocation -> {
                    Thread.currentThread().interrupt();
                    return null;
                }).when(mock).connect())) {
            Method method = Main.class.getDeclaredMethod("runClient", String.class, String.class, String.class);
            method.setAccessible(true);

            assertDoesNotThrow(() -> method.invoke(null, "xmpp.test", "alice", "secret"));
        }
    }

    @Test
    @DisplayName("main 连接失败时应记录异常链")
    void testMainLogsErrorChainOnConnectionFailure() {
        Logger logger = (Logger) LogManager.getLogger(Main.class);
        TestLogAppender appender = attachAppender("mainFailure", logger);
        XmppNetworkException cause = new XmppNetworkException("connect failed", new ConnectException("refused"));

        try (MockedConstruction<XmppTcpConnection> ignored = Mockito.mockConstruction(
                XmppTcpConnection.class,
                (mock, context) -> doThrow(cause).when(mock).connect())) {
            RuntimeException exception = assertThrows(RuntimeException.class, () -> Main.main(new String[0]));

            assertEquals("XMPP connection failed", exception.getMessage());
            assertNull(exception.getCause());
            assertTrue(appender.containsAtLevel(
                    "XMPP connection failed - ErrorChain: XmppNetworkException <- ConnectException",
                    Level.ERROR));
        } finally {
            detachAppender(appender, logger);
        }
    }

    private TestLogAppender attachAppender(String name, Logger... loggers) {
        TestLogAppender appender = new TestLogAppender(name);
        appender.start();
        for (Logger logger : loggers) {
            logger.addAppender(appender);
            logger.setAdditive(false);
            logger.setLevel(Level.ALL);
        }
        return appender;
    }

    private void detachAppender(Appender appender, Logger... loggers) {
        for (Logger logger : loggers) {
            logger.removeAppender(appender);
        }
        appender.stop();
    }

    private static final class TestLogAppender extends AbstractAppender {
        private final List<LogEvent> events = new ArrayList<>();

        private TestLogAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        private boolean containsAtLevel(String text, Level level) {
            for (LogEvent event : events) {
                if (event.getLevel() == level
                        && event.getMessage() != null
                        && event.getMessage().getFormattedMessage().contains(text)) {
                    return true;
                }
            }
            return false;
        }
    }
}
