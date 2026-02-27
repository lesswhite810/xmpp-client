package com.example.xmpp.util;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.InetSocketAddress;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * 连接辅助工具类。
 *
 * @since 2026-02-09
 */
@Slf4j
@UtilityClass
public class ConnectionUtils {

    /**
     * 同步连接到指定地址。
     *
     * @param bootstrap Netty Bootstrap 实例
     * @param address   目标地址（可以是已解析或未解析的）
     * @return 已连接的 Channel
     * @throws InterruptedException 线程被中断时抛出
     */
    public static Channel connectSync(Bootstrap bootstrap, InetSocketAddress address)
            throws InterruptedException {
        String hostDesc = address.isUnresolved()
                ? address.getHostString()
                : address.getAddress().getHostAddress();
        int port = address.getPort();

        log.info("Connecting to {}:{}...", hostDesc, port);
        try {
            ChannelFuture future = bootstrap.connect(address);
            future.sync();
            Channel channel = future.channel();
            log.info("Connected to {}", channel.remoteAddress());
            return channel;
        } catch (InterruptedException e) {
            log.error("Connection interrupted for {}:{}", hostDesc, port);
            throw e;
        }
    }
}
