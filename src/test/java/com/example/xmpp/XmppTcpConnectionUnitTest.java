package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppNetworkException;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmppTcpConnectionUnitTest {

    @Test
    void testResolveConnectionTargetsPrefersHostAddress() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("invalid-host")
                .hostAddress(InetAddress.getByName("127.0.0.1"))
                .port(5223)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build());

        List<?> targets = invoke(connection, "resolveConnectionTargets");

        assertEquals(1, targets.size());
        assertEquals("127.0.0.1:5223", targets.get(0).toString());
    }

    @Test
    void testResolveConnectionTargetsFallsBackToServiceDomain() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .host("")
                .port(5222)
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build());

        List<?> targets = invoke(connection, "resolveConnectionTargets");
        Object target = targets.get(0);
        InetSocketAddress socketAddress = invoke(target, "toSocketAddress");

        assertEquals(1, targets.size());
        assertEquals("example.com:5222", target.toString());
        assertTrue(socketAddress.isUnresolved());
        assertEquals("example.com", socketAddress.getHostString());
    }

    @Test
    void testFindReusableReadyFutureReturnsFutureForActiveChannel() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel();
        CompletableFuture<Void> future = new CompletableFuture<>();

        setField(connection, "channel", channel);
        setField(connection, "connectionReadyFuture", future);

        assertSame(future, invoke(connection, "findReusableReadyFuture"));

        channel.finishAndReleaseAll();
    }

    @Test
    void testFindReusableReadyFutureReturnsFutureForInProgressWorkerGroup() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        CompletableFuture<Void> future = new CompletableFuture<>();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);

        try {
            setField(connection, "connectionReadyFuture", future);
            setField(connection, "workerGroup", workerGroup);

            assertSame(future, invoke(connection, "findReusableReadyFuture"));
        } finally {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void testFindReusableReadyFutureReturnsNullForFailedFuture() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new XmppNetworkException("boom"));
        setField(connection, "connectionReadyFuture", future);

        assertNull(invoke(connection, "findReusableReadyFuture"));
    }

    @Test
    void testBindActiveChannelRejectsNullAndDisconnectRequested() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel();

        try {
            assertFalse(connection.bindActiveChannel(null));
            setField(connection, "disconnectRequested", true);
            assertFalse(connection.bindActiveChannel(channel));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testBindActiveChannelAcceptsFirstAndSameChannel() {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel channel = new EmbeddedChannel();

        try {
            assertTrue(connection.bindActiveChannel(channel));
            assertTrue(connection.bindActiveChannel(channel));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void testFailConnectionIgnoresStaleChannel() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        EmbeddedChannel currentChannel = new EmbeddedChannel();
        EmbeddedChannel staleChannel = new EmbeddedChannel();
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            setField(connection, "channel", currentChannel);
            setField(connection, "connectionReadyFuture", future);

            connection.failConnection(staleChannel, new XmppException("stale"));

            assertFalse(future.isDone());
            assertSame(currentChannel, getField(connection, "channel"));
        } finally {
            currentChannel.finishAndReleaseAll();
            staleChannel.finishAndReleaseAll();
        }
    }

    @Test
    void testUnwrapXmppExceptionReturnsWrappedXmppException() throws Exception {
        XmppTcpConnection connection = new XmppTcpConnection(newConfig());
        XmppNetworkException expected = new XmppNetworkException("boom");

        XmppException direct = invoke(connection, "unwrapXmppException", expected);
        XmppException wrapped = invoke(connection, "unwrapXmppException", new RuntimeException(expected));
        XmppException fallback = invoke(connection, "unwrapXmppException", new IllegalStateException("boom"));

        assertSame(expected, direct);
        assertSame(expected, wrapped);
        assertInstanceOf(com.example.xmpp.exception.XmppProtocolException.class, fallback);
    }

    @Test
    void testConnectionTargetFactorySkipsBlankHostWithoutAddress() throws Exception {
        Class<?> targetClass = Class.forName("com.example.xmpp.XmppTcpConnection$ConnectionTarget");
        Method ofMethod = targetClass.getDeclaredMethod("of", InetAddress.class, String.class, int.class);
        ofMethod.setAccessible(true);

        Optional<?> empty = (Optional<?>) ofMethod.invoke(null, null, " ", 5222);
        Optional<?> present = (Optional<?>) ofMethod.invoke(null, InetAddress.getByName("127.0.0.1"), "", 5222);

        assertTrue(empty.isEmpty());
        assertTrue(present.isPresent());
        assertEquals("127.0.0.1:5222", present.orElseThrow().toString());
    }

    private XmppClientConfig newConfig() {
        return XmppClientConfig.builder()
                .xmppServiceDomain("example.com")
                .username("user")
                .password("pass".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();
    }

    private <T> T invoke(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }

    private Method findMethod(Class<?> type, String methodName, Class<?>[] parameterTypes) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != parameterTypes.length) {
                continue;
            }
            return method;
        }
        throw new NoSuchMethodException(methodName);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = XmppTcpConnection.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object getField(Object target, String fieldName) throws Exception {
        var field = XmppTcpConnection.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
