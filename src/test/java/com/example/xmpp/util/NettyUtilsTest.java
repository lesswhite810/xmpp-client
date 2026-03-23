package com.example.xmpp.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NettyUtils 测试。
 *
 * @since 2026-03-20
 */
class NettyUtilsTest {

    @Test
    @DisplayName("writeAndFlushStringAsync 应写出 UTF-8 数据")
    void testWriteAndFlushStringAsync() {
        AtomicReference<ChannelHandlerContext> ctxRef = new AtomicReference<>();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                ctxRef.set(ctx);
            }
        });

        ChannelFuture future = NettyUtils.writeAndFlushStringAsync(ctxRef.get(), "hello世界");

        ByteBuf written = channel.readOutbound();
        assertEquals("hello世界", written.toString(CharsetUtil.UTF_8));
        written.release();
        assertNotNull(future);

        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("参数为空时应抛出异常")
    void testWriteAndFlushStringAsyncRejectsNullArguments() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

        assertThrows(NullPointerException.class, () -> NettyUtils.writeAndFlushStringAsync(null, "x"));
        assertThrows(NullPointerException.class, () -> NettyUtils.writeAndFlushStringAsync(ctx, null));
    }

    @Test
    @DisplayName("写失败时应释放 ByteBuf")
    void testWriteAndFlushStringAsyncReleasesBufferOnFailure() throws Exception {
        Logger logger = (Logger) LogManager.getLogger(NettyUtils.class);
        TestLogAppender appender = attachAppender("writeFailure", logger);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        ByteBufAllocator allocator = mock(ByteBufAllocator.class);
        ByteBuf buf = mock(ByteBuf.class);
        ChannelFuture future = mock(ChannelFuture.class);

        try {
            when(ctx.alloc()).thenReturn(allocator);
            when(allocator.buffer(any(Integer.class))).thenReturn(buf);
            when(ctx.writeAndFlush(buf)).thenReturn(future);
            when(future.isSuccess()).thenReturn(false);
            when(future.cause()).thenReturn(new IllegalStateException("write failed"));
            when(buf.refCnt()).thenReturn(1);
            doAnswer(invocation -> {
                Object listener = invocation.getArgument(0);
                GenericFutureListener genericListener = (GenericFutureListener) listener;
                genericListener.operationComplete((io.netty.util.concurrent.Future) future);
                return future;
            }).when(future).addListener(any());

            assertSame(future, NettyUtils.writeAndFlushStringAsync(ctx, "data"));
            verify(buf).writeCharSequence("data", CharsetUtil.UTF_8);
            verify(buf).release();
            assertTrue(appender.containsAtLevel("Failed to write ByteBuf, releasing buffer. ErrorType:", Level.ERROR));
        } finally {
            detachAppender(appender, logger);
        }
    }

    @Test
    @DisplayName("UTF-8 预估容量应覆盖补充平面字符")
    void testUtf8EstimateCoversSupplementaryCharacters() {
        String content = "a😀中𐍈";

        int estimatedSize = (int) (content.length() * 3.0f);
        int actualSize = content.getBytes(StandardCharsets.UTF_8).length;

        assertEquals(6, content.length());
        assertEquals(12, actualSize);
        assertEquals(18, estimatedSize);
        assertTrue(estimatedSize >= actualSize);
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
