package com.example.xmpp.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Netty ByteBuf 操作工具类。
 *
 * <p>提供常用的 Netty ByteBuf 操作封装，简化字符串写入等常见任务。</p>
 *
 * <h2>内存管理</h2>
 * <p>所有方法都遵循 Netty 的 ByteBuf 生命周期管理规则：</p>
 * <ul>
 *   <li>成功写入：ByteBuf 由 Netty 自动释放</li>
 *   <li>写入失败：通过 listener 确保释放</li>
 *   <li>异常情况：在 catch 块中显式释放</li>
 * </ul>
 *
 * @since 2026-02-09
 */
@UtilityClass
public class NettyUtils {

    private static final Logger log = LoggerFactory.getLogger(NettyUtils.class);

    /** UTF-8 字符最大字节数 */
    private static final float UTF8_MAX_BYTES_PER_CHAR = 3.0f;

    /**
     * 将字符串写入 Channel 并刷新。
     *
     * <p>自动分配 ByteBuf（预估容量），将字符串以 UTF-8 编码写入，然后立即刷新到通道。
     * 写入成功后 ByteBuf 由 Netty 自动释放，失败时通过 listener 确保释放。</p>
     *
     * @param ctx     通道处理器上下文，不能为 null
     * @param content 要发送的字符串内容，不能为 null
     * @throws IllegalArgumentException 如果 ctx 或 content 为 null
     */
    public static void writeAndFlushString(ChannelHandlerContext ctx, String content) {
        Validate.notNull(ctx, "ChannelHandlerContext must not be null");
        Validate.notNull(content, "Content must not be null");

        // 预估 UTF-8 编码后的最大字节数，避免多次扩容
        int estimatedSize = (int) (content.length() * UTF8_MAX_BYTES_PER_CHAR);
        ByteBuf buf = ctx.alloc().buffer(estimatedSize);

        try {
            buf.writeCharSequence(content, StandardCharsets.UTF_8);
            ChannelFuture future = ctx.writeAndFlush(buf);

            // 添加 listener 处理写入失败时的 ByteBuf 释放
            future.addListener(f -> {
                if (!f.isSuccess() && buf.refCnt() > 0) {
                    log.warn("Failed to write ByteBuf, releasing buffer. Error: {}",
                            f.cause() != null ? f.cause().getMessage() : "unknown");
                    buf.release();
                }
            });

        } catch (Exception e) {
            // 异常情况下确保释放 ByteBuf
            if (buf.refCnt() > 0) {
                buf.release();
            }
            log.error("Error writing string to channel", e);
            throw new RuntimeException("Failed to write string to channel", e);
        }
    }
}
