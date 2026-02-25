package com.example.xmpp.sasl;

import com.example.xmpp.XmppConstants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

/**
 * SASL 机制注册与工厂类。
 *
 * <p>管理支持的 SASL 机制，并根据服务器支持列表选择优先级最高的机制。</p>
 *
 * <p>功能特性：</p>
 * <ul>
 * <li>内置支持 SCRAM-SHA-512、SCRAM-SHA-256、SCRAM-SHA-1、PLAIN 机制</li>
 * <li>支持通过 Java SPI（ServiceLoader）自动发现扩展机制</li>
 * <li>按优先级排序，自动选择最安全的可用机制</li>
 * </ul>
 *
 * <p>线程安全：使用 ReentrantLock 保护注册操作，读操作利用原子引用替换实现无锁读取。</p>
 *
 * @since 2026-02-09
 */
public class SaslMechanismFactory {

    private static final SaslMechanismFactory INSTANCE = new SaslMechanismFactory();

    /** 已注册的机制列表，使用 volatile 保证可见性，通过原子替换实现无锁读取 */
    private volatile List<MechanismEntry> registeredMechanisms = java.util.Collections.emptyList();

    /** 注册锁，保护机制注册操作的原子性 */
    private final Lock registrationLock = new ReentrantLock();

    /** 缓存的 ServiceLoader 实例，避免重复加载 SPI 提供者 */
    private final ServiceLoader<SaslMechanismProvider> providerLoader;

    private SaslMechanismFactory() {
        // 缓存 ServiceLoader 实例
        this.providerLoader = ServiceLoader.load(SaslMechanismProvider.class);

        // 注册内置机制
        register(XmppConstants.SASL_MECH_SCRAM_SHA512, XmppConstants.PRIORITY_SCRAM_SHA512,
                ScramSha512SaslMechanism::new);
        register(XmppConstants.SASL_MECH_SCRAM_SHA256, XmppConstants.PRIORITY_SCRAM_SHA256,
                ScramSha256SaslMechanism::new);
        register(XmppConstants.SASL_MECH_SCRAM_SHA1, XmppConstants.PRIORITY_SCRAM_SHA1, ScramSha1SaslMechanism::new);
        register(XmppConstants.SASL_MECH_PLAIN, XmppConstants.PRIORITY_PLAIN, PlainSaslMechanism::new);

        // 注册 SPI 发现的扩展机制
        for (SaslMechanismProvider provider : this.providerLoader) {
            register(provider.getMechanismName(), provider.getPriority(), provider::create);
        }
    }

    /**
     * 获取 SASL 机制工厂单例实例。
     *
     * @return 工厂实例
     */
    public static SaslMechanismFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 注册 SASL 机制。
     *
     * <p>注册流程：</p>
     * <ol>
     * <li>获取注册锁（排他性）</li>
     * <li>创建当前机制列表的副本</li>
     * <li>添加新机制到副本</li>
     * <li>按优先级降序排序副本</li>
     * <li>原子替换原列表</li>
     * <li>释放锁</li>
     * </ol>
     *
     * @param name 机制名称（如 "SCRAM-SHA-256"）
     * @param priority 优先级（数值越大优先级越高）
     * @param factory 机制工厂函数，接收用户名和密码（char[]），返回 SaslMechanism 实例
     */
    public void register(String name, int priority, BiFunction<String, char[], SaslMechanism> factory) {
        registrationLock.lock();
        try {
            // 创建当前机制列表的副本进行修改
            List<MechanismEntry> updated = new ArrayList<>(registeredMechanisms);
            updated.add(new MechanismEntry(name, priority, factory));
            // 按优先级降序排序（高优先级在前）
            updated.sort((a, b) -> Integer.compare(b.priority, a.priority));

            // P1 修复：原子替换整个列表引用，消除清空和填充之间的并发空窗
            registeredMechanisms = List.copyOf(updated);
        } finally {
            registrationLock.unlock();
        }
    }

    /**
     * 根据服务器支持的机制列表创建最佳 SASL 机制实例。
     *
     * @param serverMechanisms 服务器支持的机制名称列表
     * @param username 用户名
     * @param password 密码（char[]）
     * @return 创建的 SaslMechanism 实例，如果没有匹配的机制则返回 Optional.empty()
     */
    public Optional<SaslMechanism> createBestMechanism(List<String> serverMechanisms, String username,
            char[] password) {
        return createBestMechanism(serverMechanisms, null, username, password);
    }

    /**
     * 根据服务器支持的机制列表和客户端启用的机制列表创建最佳 SASL 机制实例。
     *
     * @param serverMechanisms 服务器支持的机制名称列表
     * @param enabledMechanisms 客户端启用的机制列表（null 或空表示不限制）
     * @param username 用户名
     * @param password 密码（char[]）
     * @return 创建的 SaslMechanism 实例，如果没有匹配的机制则返回 Optional.empty()
     */
    public Optional<SaslMechanism> createBestMechanism(List<String> serverMechanisms, Set<String> enabledMechanisms,
            String username, char[] password) {
        for (MechanismEntry entry : registeredMechanisms) {
            // 检查服务器是否支持该机制
            if (!serverMechanisms.contains(entry.name)) {
                continue;
            }
            // 如果客户端指定了启用的机制，检查该机制是否在启用列表中
            if (enabledMechanisms != null && !enabledMechanisms.isEmpty()) {
                if (!enabledMechanisms.contains(entry.name)) {
                    continue;
                }
            }
            return Optional.of(entry.factory.apply(username, password));
        }
        return Optional.empty();
    }

    /**
     * SASL 机制条目，存储机制的名称、优先级和工厂函数。
     *
     * @since 2026-02-09
     */
    @Getter
    @RequiredArgsConstructor
    private static class MechanismEntry {

        /** 机制名称，如 "SCRAM-SHA-256" */
        private final String name;

        /** 优先级，数值越大优先级越高 */
        private final int priority;

        /** 机制工厂函数，接收用户名和密码，返回 SaslMechanism 实例 */
        private final BiFunction<String, char[], SaslMechanism> factory;
    }
}
