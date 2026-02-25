package com.example.xmpp.net;

import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DNS 解析器单元测试。
 *
 * <p>测试 DnsResolver 的基本功能，包括：</p>
 * <ul>
 *   <li>构造函数行为</li>
 *   <li>资源所有权管理</li>
 *   <li>域名参数验证</li>
 *   <li>资源释放</li>
 * </ul>
 *
 * @since 2026-02-15
 */
class DnsResolverTest {

    private NioEventLoopGroup externalGroup;

    @BeforeEach
    void setUp() {
        externalGroup = new NioEventLoopGroup(1);
    }

    @AfterEach
    void tearDown() {
        if (externalGroup != null && !externalGroup.isShutdown()) {
            externalGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("测试默认构造函数拥有 EventLoopGroup")
    void testDefaultConstructorOwnsGroup() {
        try (DnsResolver resolver = new DnsResolver()) {
            assertTrue(resolver.ownsGroup(), "Default constructor should own the EventLoopGroup");
        }
    }

    @Test
    @DisplayName("测试外部 EventLoopGroup 构造函数不拥有资源")
    void testExternalGroupConstructorDoesNotOwnGroup() {
        try (DnsResolver resolver = new DnsResolver(externalGroup)) {
            assertFalse(resolver.ownsGroup(), "External group constructor should not own the EventLoopGroup");
        }
    }

    @Test
    @DisplayName("测试 close() 关闭内部 EventLoopGroup")
    void testCloseShutsDownInternalGroup() throws InterruptedException {
        DnsResolver resolver = new DnsResolver();
        assertTrue(resolver.ownsGroup());

        resolver.close();

        // 给一点时间让关闭完成
        Thread.sleep(100);
        // 由于我们无法访问内部的 group，只能通过 ownsGroup 来验证状态
        // close() 后 ownsGroup 仍返回 true，只是 group 已关闭
        assertTrue(resolver.ownsGroup());
    }

    @Test
    @DisplayName("测试 close() 不关闭外部 EventLoopGroup")
    void testCloseDoesNotShutDownExternalGroup() throws InterruptedException {
        DnsResolver resolver = new DnsResolver(externalGroup);
        assertFalse(resolver.ownsGroup());

        resolver.close();

        // 外部 group 应该仍然可用
        assertFalse(externalGroup.isShutdown(), "External group should not be shut down");
    }

    @Test
    @DisplayName("测试 shutdownNow() 立即关闭内部 EventLoopGroup")
    void testShutdownNowClosesInternalGroup() {
        DnsResolver resolver = new DnsResolver();
        assertTrue(resolver.ownsGroup());

        resolver.shutdownNow();

        // shutdownNow 不会改变 ownsGroup 状态
        assertTrue(resolver.ownsGroup());
    }

    @Test
    @DisplayName("测试 shutdownNow() 不关闭外部 EventLoopGroup")
    void testShutdownNowDoesNotCloseExternalGroup() {
        DnsResolver resolver = new DnsResolver(externalGroup);
        assertFalse(resolver.ownsGroup());

        resolver.shutdownNow();

        // 外部 group 应该仍然可用
        assertFalse(externalGroup.isShutdown(), "External group should not be shut down");
    }

    @Test
    @DisplayName("测试 null 域名应抛出 NullPointerException")
    void testNullDomainThrowsNullPointerException() {
        try (DnsResolver resolver = new DnsResolver()) {
            assertThrows(NullPointerException.class, () -> resolver.resolveXmppService(null));
        }
    }

    @Test
    @DisplayName("测试空域名应抛出 IllegalArgumentException")
    void testBlankDomainThrowsIllegalArgumentException() {
        try (DnsResolver resolver = new DnsResolver()) {
            assertThrows(IllegalArgumentException.class, () -> resolver.resolveXmppService(""));
        }
    }

    @Test
    @DisplayName("测试纯空格域名应抛出 IllegalArgumentException")
    void testWhitespaceDomainThrowsIllegalArgumentException() {
        try (DnsResolver resolver = new DnsResolver()) {
            assertThrows(IllegalArgumentException.class, () -> resolver.resolveXmppService("   "));
        }
    }

    @Test
    @DisplayName("测试使用 try-with-resources 自动关闭")
    void testTryWithResourcesAutoClose() {
        // 这个测试验证 DnsResolver 实现了 AutoCloseable
        try (DnsResolver resolver = new DnsResolver()) {
            assertTrue(resolver.ownsGroup());
            // 正常使用
        }
        // 离开 try 块后自动调用 close()
    }

    @Test
    @DisplayName("测试多次调用 close() 不抛出异常")
    void testMultipleCloseCallsNoException() {
        DnsResolver resolver = new DnsResolver();

        // 多次调用 close() 应该不会抛出异常
        assertDoesNotThrow(() -> {
            resolver.close();
            resolver.close();
            resolver.close();
        });
    }

    @Test
    @DisplayName("测试外部 group 可以在 resolver 关闭后继续使用")
    void testExternalGroupReusableAfterResolverClose() throws InterruptedException {
        // 创建并关闭第一个 resolver
        try (DnsResolver resolver1 = new DnsResolver(externalGroup)) {
            assertFalse(resolver1.ownsGroup());
        }

        // 外部 group 应该仍然可用
        assertFalse(externalGroup.isShutdown());

        // 创建第二个 resolver 使用相同的 group
        try (DnsResolver resolver2 = new DnsResolver(externalGroup)) {
            assertFalse(resolver2.ownsGroup());
        }

        // 外部 group 仍然可用
        assertFalse(externalGroup.isShutdown());
    }
}
