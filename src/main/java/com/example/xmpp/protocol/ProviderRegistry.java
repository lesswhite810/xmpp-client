package com.example.xmpp.protocol;

import com.example.xmpp.protocol.provider.BindProvider;
import com.example.xmpp.protocol.provider.PingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 注册中心。
 *
 * <p>单例模式，集中管理所有 XML 扩展元素的 Provider。
 * 负责注册、查找和移除 Provider，使用元素名称和命名空间作为复合键。
 * 所有公共方法都是线程安全的。</p>
 *
 * <p>Provider 发现机制：内置 Provider 在初始化时自动注册，通过 Java SPI 发现的 ProtocolProvider
 * 实现自动注册，可通过 registerProvider(Provider) 手动注册。</p>
 *
 * <p>键格式：有命名空间时为 elementName:namespace，无命名空间时为 elementName。</p>
 *
 * @since 2026-02-09
 * @see Provider
 * @see ProtocolProvider
 */
public final class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    /** 单例实例 */
    private static final ProviderRegistry INSTANCE = new ProviderRegistry();

    /** Provider 映射表 */
    private final Map<String, ProviderEntry> providerMap;

    /** SPI 服务加载器 */
    private final ServiceLoader<ProtocolProvider> providerLoader;

    /**
     * 私有构造器。
     */
    private ProviderRegistry() {
        this.providerMap = new ConcurrentHashMap<>();
        this.providerLoader = ServiceLoader.load(ProtocolProvider.class);
        registerBuiltInProviders();
        discoverSpiProviders();
        log.debug("ProviderRegistry initialized with {} providers", providerMap.size());
    }

    /**
     * 获取 ProviderRegistry 单例实例。
     *
     * @return ProviderRegistry 实例
     */
    public static ProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册一个 Provider。
     *
     * <p>如果已存在相同 elementName 和 namespace 的 Provider，则根据优先级决定是否覆盖。</p>
     *
     * @param provider 要注册的 Provider，不能为 null
     *
     * @throws NullPointerException 如果 provider 为 null
     */
    public void registerProvider(Provider<?> provider) {
        registerProvider(provider, 100);
    }

    /**
     * 注册一个 Provider（带优先级）。
     *
     * <p>如果已存在相同 elementName 和 namespace 的 Provider：
     * 新优先级大于等于已有优先级时覆盖，新优先级小于已有优先级时忽略。</p>
     *
     * @param provider 要注册的 Provider，不能为 null
     * @param priority 优先级（数值越大优先级越高）
     *
     * @throws NullPointerException 如果 provider 为 null
     */
    public void registerProvider(Provider<?> provider, int priority) {
        Objects.requireNonNull(provider, "Provider cannot be null");

        String key = createKey(provider.getElementName(), provider.getNamespace());
        ProviderEntry newEntry = new ProviderEntry(provider, priority);

        providerMap.compute(key, (k, existing) -> {
            if (existing == null) {
                log.debug("Registered provider: <{} xmlns=\"{}\"> (priority={})",
                        provider.getElementName(), provider.getNamespace(), priority);
                return newEntry;
            } else if (priority >= existing.priority) {
                log.debug("Replaced provider: <{} xmlns=\"{}\"> ({} -> {}, previous: {})",
                        provider.getElementName(), provider.getNamespace(),
                        existing.priority, priority, existing.provider.getClass().getSimpleName());
                return newEntry;
            } else {
                log.trace("Skipped provider (lower priority): <{} xmlns=\"{}\"> ({} < {})",
                        provider.getElementName(), provider.getNamespace(),
                        priority, existing.priority);
                return existing;
            }
        });
    }

    /**
     * 获取指定元素和命名空间的 Provider。
     *
     * @param elementName XML 元素本地名称
     * @param namespace   XML 命名空间 URI，可为 null
     *
     * @return 匹配的 Provider 的 Optional，如果不存在则返回 Optional.empty()
     */
    public Optional<Provider<?>> getProvider(String elementName, String namespace) {
        String key = createKey(elementName, namespace);
        ProviderEntry entry = providerMap.get(key);

        if (entry != null) {
            if (log.isTraceEnabled()) {
                log.trace("Found provider for <{} xmlns=\"{}\">: {}",
                        elementName, namespace, entry.provider.getClass().getSimpleName());
            }
            return Optional.of(entry.provider);
        }

        if (log.isTraceEnabled()) {
            log.trace("No provider found for <{} xmlns=\"{}\">", elementName, namespace);
        }
        return Optional.empty();
    }

    /**
     * 移除指定元素和命名空间的 Provider。
     *
     * @param elementName XML 元素本地名称
     * @param namespace   XML 命名空间 URI，可为 null
     *
     * @return 被移除的 Provider 的 Optional，如果不存在则返回 Optional.empty()
     */
    public Optional<Provider<?>> removeProvider(String elementName, String namespace) {
        String key = createKey(elementName, namespace);
        ProviderEntry removed = providerMap.remove(key);

        if (removed != null) {
            log.debug("Removed provider: <{} xmlns=\"{}\">", elementName, namespace);
            return Optional.of(removed.provider);
        }

        return Optional.empty();
    }

    /**
     * 清空所有已注册的 Provider。
     *
     * <p>注意：此操作也会移除默认 Provider。</p>
     */
    public void clear() {
        int size = providerMap.size();
        providerMap.clear();
        log.debug("Cleared {} providers", size);
    }

    /**
     * 获取所有已注册 Provider 的键集合。
     *
     * <p>主要用于调试和监控。</p>
     *
     * @return 所有 Provider 键的不可修改集合
     */
    public Set<String> getRegisteredKeys() {
        return Set.copyOf(providerMap.keySet());
    }

    /**
     * 检查是否存在指定元素和命名空间的 Provider。
     *
     * @param elementName XML 元素本地名称
     * @param namespace   XML 命名空间 URI，可为 null
     * @return 如果存在返回 true
     */
    public boolean hasProvider(String elementName, String namespace) {
        String key = createKey(elementName, namespace);
        return providerMap.containsKey(key);
    }

    /**
     * 获取已注册 Provider 的数量。
     *
     * @return Provider 数量
     */
    public int size() {
        return providerMap.size();
    }

    private String createKey(String elementName, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return elementName;
        }
        return elementName + ':' + namespace;
    }

    private void registerBuiltInProviders() {
        registerProvider(new BindProvider(), 50);
        registerProvider(new PingProvider(), 50);
    }

    private void discoverSpiProviders() {
        int spiCount = 0;
        for (ProtocolProvider<?> protocolProvider : providerLoader) {
            try {
                Provider<?> provider = protocolProvider.createProvider();
                registerProvider(provider, protocolProvider.getPriority());
                spiCount++;
                log.debug("Discovered SPI provider: {} (priority={})",
                        protocolProvider.getClass().getName(), protocolProvider.getPriority());
            } catch (Exception e) {
                log.warn("Failed to load SPI provider: {}",
                        protocolProvider.getClass().getName(), e);
            }
        }
        if (spiCount > 0) {
            log.info("Discovered {} SPI providers", spiCount);
        }
    }

    private static final class ProviderEntry {
        final Provider<?> provider;
        final int priority;

        ProviderEntry(Provider<?> provider, int priority) {
            this.provider = provider;
            this.priority = priority;
        }
    }
}
