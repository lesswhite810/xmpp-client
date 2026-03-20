package com.example.xmpp;

/**
 * 真实服务器测试基类。
 *
 * <p>继承该基类的测试默认归入真实服务器测试分组，只在显式启用相关 profile 时执行，
 * 不参与默认覆盖率统计。</p>
 *
 * @since 2026-03-20
 */
@RealServerTest
public abstract class AbstractRealServerTest {
}
