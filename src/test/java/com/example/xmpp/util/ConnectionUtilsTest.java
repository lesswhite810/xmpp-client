package com.example.xmpp.util;

import com.example.xmpp.exception.XmppNetworkException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ConnectionUtils} 单元测试。
 */
class ConnectionUtilsTest {

    @Test
    void testConnectSyncInterruptedThrowsXmppNetworkException() throws Exception {
        Logger logger = (Logger) LogManager.getLogger(ConnectionUtils.class);
        TestLogAppender appender = attachAppender("connectInterrupted", logger);
        Bootstrap bootstrap = mock(Bootstrap.class);
        ChannelFuture future = mock(ChannelFuture.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", XmppConstants.DEFAULT_XMPP_PORT);

        try {
            when(bootstrap.connect(address)).thenReturn(future);
            when(future.sync()).thenThrow(new InterruptedException("interrupted"));

            Throwable throwable = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class,
                    () -> ConnectionUtils.connectSync(bootstrap, address));

            // 验证正确的异常类型被抛出
            assertInstanceOf(XmppNetworkException.class, throwable);

            // 验证异常消息正确
            assertTrue(throwable.getMessage().contains("Connection interrupted"),
                    "Exception message should indicate interruption");

            // 验证原始 InterruptedException 被保留为 cause
            assertInstanceOf(InterruptedException.class, throwable.getCause(),
                    "Original InterruptedException should be preserved as cause");

            // 验证错误日志被正确记录
            // 注意：这里使用 containsAtLevel 而不是精确匹配，允许日志框架添加额外信息
            assertTrue(appender.containsAtLevel("Connection interrupted for example.com:" + XmppConstants.DEFAULT_XMPP_PORT,
                    Level.ERROR), "Error log should be recorded for interrupted connection");
        } finally {
            detachAppender(appender, logger);
        }
    }

    @Test
    void testConnectSyncWrapsUnexpectedConnectException() {
        Logger logger = (Logger) LogManager.getLogger(ConnectionUtils.class);
        TestLogAppender appender = attachAppender("connectFailure", logger);
        Bootstrap bootstrap = mock(Bootstrap.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", XmppConstants.DEFAULT_XMPP_PORT);
        IllegalStateException cause = new IllegalStateException("boom");

        try {
            when(bootstrap.connect(address)).thenThrow(cause);

            XmppNetworkException exception = org.junit.jupiter.api.Assertions.assertThrows(XmppNetworkException.class,
                    () -> ConnectionUtils.connectSync(bootstrap, address));

            assertEquals("Failed to connect to example.com:" + XmppConstants.DEFAULT_XMPP_PORT, exception.getMessage());
            assertSame(cause, exception.getCause());
            assertTrue(appender.containsAtLevel("Connection failed - Target: example.com:" + XmppConstants.DEFAULT_XMPP_PORT,
                    Level.ERROR));
        } finally {
            detachAppender(appender, logger);
        }
    }

    @Test
    void testConnectSyncWrapsFailedFutureCause() throws Exception {
        Bootstrap bootstrap = mock(Bootstrap.class);
        ChannelFuture future = mock(ChannelFuture.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", XmppConstants.DEFAULT_XMPP_PORT);
        IllegalStateException cause = new IllegalStateException("connect failed");

        when(bootstrap.connect(address)).thenReturn(future);
        when(future.sync()).thenReturn(future);
        when(future.isSuccess()).thenReturn(false);
        when(future.cause()).thenReturn(cause);

        XmppNetworkException exception = org.junit.jupiter.api.Assertions.assertThrows(XmppNetworkException.class,
                () -> ConnectionUtils.connectSync(bootstrap, address));

        assertEquals("Failed to connect to example.com:" + XmppConstants.DEFAULT_XMPP_PORT, exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testConnectSyncReturnsChannelWhenFutureSucceeds() throws Exception {
        Bootstrap bootstrap = mock(Bootstrap.class);
        ChannelFuture future = mock(ChannelFuture.class);
        Channel channel = mock(Channel.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", XmppConstants.DEFAULT_XMPP_PORT);

        when(bootstrap.connect(address)).thenReturn(future);
        when(future.sync()).thenReturn(future);
        when(future.isSuccess()).thenReturn(true);
        when(future.channel()).thenReturn(channel);

        assertSame(channel, ConnectionUtils.connectSync(bootstrap, address));
    }

    @Test
    void testConnectSyncUsesResolvedAddressDescription() throws Exception {
        Bootstrap bootstrap = mock(Bootstrap.class);
        ChannelFuture future = mock(ChannelFuture.class);
        Channel channel = mock(Channel.class);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), XmppConstants.DEFAULT_XMPP_PORT);

        when(bootstrap.connect(address)).thenReturn(future);
        when(future.sync()).thenReturn(future);
        when(future.isSuccess()).thenReturn(true);
        when(future.channel()).thenReturn(channel);

        assertSame(channel, ConnectionUtils.connectSync(bootstrap, address));
    }

    private TestLogAppender attachAppender(String name, Logger... loggers) {
        TestLogAppender appender = new TestLogAppender(name);
        appender.start();
        for (Logger logger : loggers) {
            logger.addAppender(appender);
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
        private final Object lock = new Object();

        private TestLogAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false, null);
        }

        @Override
        public void append(LogEvent event) {
            synchronized (lock) {
                events.add(event.toImmutable());
            }
        }

        private boolean containsAtLevel(String text, Level level) {
            synchronized (lock) {
                for (LogEvent event : events) {
                    if (event.getLevel() == level
                            && event.getMessage() != null
                            && event.getMessage().getFormattedMessage().contains(text)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
