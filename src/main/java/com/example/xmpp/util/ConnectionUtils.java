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
 * @since 2026-02-09
 */
@Slf4j
@UtilityClass
public class ConnectionUtils {

    /**
     * 同步连接到指定地址。
     *
     * @param bootstrap Netty Bootstrap 实例
     * @param address 目标地址
     * @return 已连接的 Channel
     * @throws XmppNetworkException 如果连接失败或中断
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
                Throwable cause = future.cause();
                if (cause instanceof XmppNetworkException xmppNetworkException) {
                    throw xmppNetworkException;
                }
                throw new XmppNetworkException("Failed to connect to " + hostDesc + ":" + port, cause);
            }
            Channel channel = future.channel();
            log.info("Connected to {}", channel.remoteAddress());
            return channel;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Connection interrupted for {}:{}", hostDesc, port);
            throw new XmppNetworkException("Connection interrupted", e);
        } catch (XmppNetworkException e) {
            throw e;
        } catch (Exception e) {
            log.error("Connection failed - Target: {}:{}, ErrorType: {}",
                    hostDesc, port, e.getClass().getSimpleName());
            throw new XmppNetworkException("Failed to connect to " + hostDesc + ":" + port, e);
        }
    }
}
