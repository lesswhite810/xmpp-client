package com.example.xmpp.util;

import com.example.xmpp.exception.XmppNetworkException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
