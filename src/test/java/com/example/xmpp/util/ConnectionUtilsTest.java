package com.example.xmpp.util;

import com.example.xmpp.exception.XmppNetworkException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.InetAddress;

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
        Bootstrap bootstrap = mock(Bootstrap.class);
        ChannelFuture future = mock(ChannelFuture.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", 5222);

        when(bootstrap.connect(address)).thenReturn(future);
        when(future.sync()).thenThrow(new InterruptedException("interrupted"));

        Throwable throwable = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class,
                () -> ConnectionUtils.connectSync(bootstrap, address));

        assertInstanceOf(XmppNetworkException.class, throwable);
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void testConnectSyncWrapsUnexpectedConnectException() {
        Bootstrap bootstrap = mock(Bootstrap.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", 5222);

        when(bootstrap.connect(address)).thenThrow(new IllegalStateException("boom"));

        XmppNetworkException exception = org.junit.jupiter.api.Assertions.assertThrows(XmppNetworkException.class,
                () -> ConnectionUtils.connectSync(bootstrap, address));

        assertEquals("Failed to connect to example.com:5222", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
    }

    @Test
    void testConnectSyncWrapsFailedFutureCause() throws Exception {
        Bootstrap bootstrap = mock(Bootstrap.class);
        ChannelFuture future = mock(ChannelFuture.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", 5222);
        IllegalStateException cause = new IllegalStateException("connect failed");

        when(bootstrap.connect(address)).thenReturn(future);
        when(future.sync()).thenReturn(future);
        when(future.isSuccess()).thenReturn(false);
        when(future.cause()).thenReturn(cause);

        XmppNetworkException exception = org.junit.jupiter.api.Assertions.assertThrows(XmppNetworkException.class,
                () -> ConnectionUtils.connectSync(bootstrap, address));

        assertEquals("Failed to connect to example.com:5222", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
    }

    @Test
    void testConnectSyncReturnsChannelWhenFutureSucceeds() throws Exception {
        Bootstrap bootstrap = mock(Bootstrap.class);
        ChannelFuture future = mock(ChannelFuture.class);
        Channel channel = mock(Channel.class);
        InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", 5222);

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
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 5222);

        when(bootstrap.connect(address)).thenReturn(future);
        when(future.sync()).thenReturn(future);
        when(future.isSuccess()).thenReturn(true);
        when(future.channel()).thenReturn(channel);

        assertSame(channel, ConnectionUtils.connectSync(bootstrap, address));
    }
}
