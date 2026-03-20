package com.example.xmpp.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;

import java.nio.charset.StandardCharsets;

/**
 * Netty ByteBuf 工具类。
 *
 * @since 2026-02-09
 */
@Slf4j
@UtilityClass
public class NettyUtils {

    /**
     * UTF-8 单字符最大字节数估算值。
     */
    private static final float UTF8_MAX_BYTES_PER_CHAR = 3.0f;

    /**
     * 将字符串写入 Channel 并刷新。
     *
     * @param ctx 通道处理器上下文，不能为 null
     * @param content 要发送的字符串内容，不能为 null
     * @throws IllegalArgumentException 如果 ctx 或 content 为 null
     */
    public static void writeAndFlushString(ChannelHandlerContext ctx, String content) {
        writeAndFlushStringAsync(ctx, content);
    }

    /**
     * 将字符串写入 Channel 并返回对应的发送 Future。
     *
     * @param ctx 通道处理器上下文，不能为 null
     * @param content 要发送的字符串内容，不能为 null
     * @return Netty 发送 Future
     * @throws IllegalArgumentException 如果 ctx 或 content 为 null
     */
    public static ChannelFuture writeAndFlushStringAsync(ChannelHandlerContext ctx, String content) {
        Validate.notNull(ctx, "ChannelHandlerContext must not be null");
        Validate.notNull(content, "Content must not be null");

        int estimatedSize = (int) (content.length() * UTF8_MAX_BYTES_PER_CHAR);
        ByteBuf buf = ctx.alloc().buffer(estimatedSize);

        buf.writeCharSequence(content, StandardCharsets.UTF_8);
        ChannelFuture future = ctx.writeAndFlush(buf);

        future.addListener(f -> {
            if (!f.isSuccess() && buf.refCnt() > 0) {
                log.warn("Failed to write ByteBuf, releasing buffer. ErrorType: {}",
                        f.cause() != null ? f.cause().getClass().getSimpleName() : "unknown");
                buf.release();
            }
        });
        return future;
    }
}
