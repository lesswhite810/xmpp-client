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
 * <p>提供 XMPP 连接相关的辅助方法，主要用于封装 Netty Bootstrap 的同步建连流程。</p>
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
     * @param bootstrap Netty Bootstrap 实例，用于建立连接
     * @param address   目标地址，可以是已解析或未解析地址
     * @return 已连接的 Channel 实例
     * @throws XmppNetworkException 如果连接失败、中断或底层 ChannelFuture 返回异常
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
            log.warn("Connection interrupted for {}:{}", hostDesc, port);
            log.debug("Connection interrupt detail", e);
            throw new XmppNetworkException("Connection interrupted", e);
        } catch (XmppNetworkException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Connection failed");
            log.debug("Connection failure detail", e);
            log.debug("Connection failure detail", e);
            throw new XmppNetworkException("Failed to connect to " + hostDesc + ":" + port, e);
        }
    }
}
