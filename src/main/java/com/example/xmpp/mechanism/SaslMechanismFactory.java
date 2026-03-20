package com.example.xmpp.mechanism;

import com.example.xmpp.util.XmppConstants;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

/**
 * SASL 机制注册与工厂。
 *
 * @since 2026-02-09
 */
@Slf4j
public class SaslMechanismFactory {

    private static final SaslMechanismFactory INSTANCE = new SaslMechanismFactory();

    /**
     * 已注册的机制列表。
     */
    private volatile List<MechanismEntry> registeredMechanisms = Collections.emptyList();

    /**
     * 注册锁。
     */
    private final Lock registrationLock = new ReentrantLock();

    /**
     * ServiceLoader 实例。
     */
    private final ServiceLoader<SaslMechanismProvider> providerLoader;

    private SaslMechanismFactory() {
        this.providerLoader = ServiceLoader.load(SaslMechanismProvider.class);

        register(XmppConstants.SASL_MECH_SCRAM_SHA512, XmppConstants.PRIORITY_SCRAM_SHA512,
                ScramSha512SaslMechanism::new);
        register(XmppConstants.SASL_MECH_SCRAM_SHA256, XmppConstants.PRIORITY_SCRAM_SHA256,
                ScramSha256SaslMechanism::new);
        register(XmppConstants.SASL_MECH_SCRAM_SHA1, XmppConstants.PRIORITY_SCRAM_SHA1, ScramSha1SaslMechanism::new);
        register(XmppConstants.SASL_MECH_PLAIN, XmppConstants.PRIORITY_PLAIN, PlainSaslMechanism::new);

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
     * @param name 机制名称
     * @param priority 优先级
     * @param factory 机制工厂
     */
    public void register(String name, int priority, BiFunction<String, char[], SaslMechanism> factory) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        Objects.requireNonNull(factory, "factory must not be null");
        registrationLock.lock();
        try {
            List<MechanismEntry> updated = new ArrayList<>(registeredMechanisms);
            MechanismEntry existing = null;
            for (MechanismEntry entry : registeredMechanisms) {
                if (entry.name.equals(name)) {
                    existing = entry;
                    break;
                }
            }
            if (existing != null) {
                log.warn("SASL mechanism '{}' already registered with priority {}, replacing with priority {}",
                        name, existing.priority, priority);
            }
            updated.removeIf(entry -> entry.name.equals(name));
            updated.add(new MechanismEntry(name, priority, factory));
            updated.sort((a, b) -> Integer.compare(b.priority, a.priority));

            registeredMechanisms = List.copyOf(updated);
        } finally {
            registrationLock.unlock();
        }
    }

    /**
     * 创建最佳 SASL 机制。
     *
     * @param serverMechanisms 服务器支持的机制列表
     * @param username 用户名
     * @param password 密码
     * @return SASL 机制
     */
    public Optional<SaslMechanism> createBestMechanism(List<String> serverMechanisms, String username,
            char[] password) {
        return createBestMechanism(serverMechanisms, null, username, password);
    }

    /**
     * 创建最佳 SASL 机制。
     *
     * @param serverMechanisms 服务器支持的机制列表
     * @param enabledMechanisms 客户端启用的机制列表
     * @param username 用户名
     * @param password 密码
     * @return SASL 机制
     */
    public Optional<SaslMechanism> createBestMechanism(List<String> serverMechanisms, Set<String> enabledMechanisms,
            String username, char[] password) {
        if (CollectionUtils.isEmpty(serverMechanisms)) {
            log.warn("Server SASL mechanisms list is null or empty");
            return Optional.empty();
        }
        Set<String> serverMechanismSet = new HashSet<>(serverMechanisms);
        for (MechanismEntry entry : registeredMechanisms) {
            if (!serverMechanismSet.contains(entry.name)) {
                continue;
            }
            if (CollectionUtils.isNotEmpty(enabledMechanisms)) {
                if (!enabledMechanisms.contains(entry.name)) {
                    continue;
                }
            }
            SaslMechanism mechanism = entry.factory.apply(username, password);
            if (mechanism == null) {
                log.error("SASL mechanism factory returned null for '{}'", entry.name);
                throw new IllegalStateException("SASL mechanism factory returned null for " + entry.name);
            }
            String mechanismName = mechanism.getMechanismName();
            if (StringUtils.isBlank(mechanismName)) {
                log.error("SASL mechanism '{}' returned invalid (blank) mechanism name", entry.name);
                throw new IllegalStateException("SASL mechanism returned invalid name for " + entry.name);
            }
            return Optional.of(mechanism);
        }
        return Optional.empty();
    }

    /**
     * SASL 机制条目。
     */
    @Getter
    @RequiredArgsConstructor
    private static class MechanismEntry {

        /**
         * 机制名称。
         */
        private final String name;

        /**
         * 优先级。
         */
        private final int priority;

        /**
         * 机制工厂。
         */
        private final BiFunction<String, char[], SaslMechanism> factory;
    }
}
