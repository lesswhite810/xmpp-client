package com.example.xmpp.util;

import com.example.xmpp.exception.XmppNetworkException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.InetSocketAddress;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * 连接辅助工具类。
 *
 * <p>提供 XMPP 连接相关的辅助方法，包括同步连接建立等操作。
 * 此类主要用于封装 Netty Bootstrap 的连接操作，提供更便捷的 API。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
@UtilityClass
public class ConnectionUtils {

    /**
     * 同步连接到指定地址。
     *
     * <p>此方法会阻塞当前线程，直到连接成功建立或发生异常。</p>
     *
     * @param bootstrap Netty Bootstrap 实例，用于建立连接，不能为 null
     * @param address   目标地址（可以是已解析或未解析的），不能为 null
     * @return 已连接的 Channel 实例
     * @throws io.netty.channel.ChannelException 如果连接失败（如拒绝连接、超时等）
     */
    public static Channel connectSync(Bootstrap bootstrap, InetSocketAddress address) throws XmppNetworkException {
        String hostDesc = address.isUnresolved()
                ? address.getHostString()
                : address.getAddress().getHostAddress();
        int port = address.getPort();

        log.info("Connecting to {}:{}...", hostDesc, port);
        try {
            ChannelFuture future = bootstrap.connect(address);
            future.sync();
            if (!future.isSuccess()) {
                throw new XmppNetworkException("Failed to connect to " + hostDesc + ":" + port, future.cause());
            }
            Channel channel = future.channel();
            log.info("Connected to {}", channel.remoteAddress());
            return channel;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Connection interrupted for {}:{}", hostDesc, port);
            throw new XmppNetworkException("Connection interrupted", e);
        }
    }
}
