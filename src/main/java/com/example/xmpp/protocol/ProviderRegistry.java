package com.example.xmpp.protocol;

import com.example.xmpp.protocol.provider.BindProvider;
import com.example.xmpp.protocol.provider.PingProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 注册中心。
 *
 * <p>单例模式，集中管理所有 XML 元素的 Provider。</p>
 *
 * <p>Provider 类型：</p>
 * <ul>
 *   <li>{@link ExtensionElementProvider}：解析 IQ/Message/Presence 的子元素</li>
 *   <li>{@link IqProvider}：解析完整的 IQ 节</li>
 * </ul>
 *
 * <p>键格式：有命名空间时为 elementName:namespace，无命名空间时为 elementName。</p>
 *
 * @since 2026-02-09
 * @see Provider
 * @see ExtensionElementProvider
 * @see IqProvider
 */
@Slf4j
public final class ProviderRegistry {

    private static final ProviderRegistry INSTANCE = new ProviderRegistry();

    private final Map<String, Provider<?>> providers = new ConcurrentHashMap<>();

    private ProviderRegistry() {
        registerBuiltInProviders();
        discoverSpiProviders();
        log.debug("ProviderRegistry initialized with {} providers", providers.size());
    }

    /**
     * 获取 ProviderRegistry 单例实例。
     *
     * @return ProviderRegistry 单例实例
     */
    public static ProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册 Provider。
     *
     * @param provider Provider 实例，不能为 null
     * @throws NullPointerException 如果 provider 为 null
     */
    public void registerProvider(Provider<?> provider) {
        Objects.requireNonNull(provider, "Provider cannot be null");
        String key = createKey(provider.getElementName(), provider.getNamespace());
        Provider<?> previous = providers.put(key, provider);
        if (previous != null) {
            log.debug("Replaced provider: <{} xmlns=\"{}\"> ({} -> {})",
                    provider.getElementName(), provider.getNamespace(),
                    previous.getClass().getSimpleName(), provider.getClass().getSimpleName());
        } else {
            log.debug("Registered provider: <{} xmlns=\"{}\"> ({})",
                    provider.getElementName(), provider.getNamespace(),
                    provider.getClass().getSimpleName());
        }
    }

    /**
     * 获取扩展元素 Provider。
     *
     * @param elementName 元素名称
     * @param namespace 命名空间
     * @return 扩展元素 Provider 的可选对象
     */
    public Optional<ExtensionElementProvider<?>> getExtensionProvider(String elementName, String namespace) {
        return getProvider(elementName, namespace)
                .filter(p -> p instanceof ExtensionElementProvider)
                .map(p -> (ExtensionElementProvider<?>) p);
    }

    /**
     * 获取 IQ Provider。
     *
     * @param elementName 元素名称
     * @param namespace 命名空间
     * @return IQ Provider 的可选对象
     */
    public Optional<IqProvider> getIqProvider(String elementName, String namespace) {
        return getProvider(elementName, namespace)
                .filter(p -> p instanceof IqProvider)
                .map(p -> (IqProvider) p);
    }

    /**
     * 获取任意 Provider。
     *
     * @param elementName 元素名称
     * @param namespace 命名空间
     * @return Provider 的可选对象
     */
    public Optional<Provider<?>> getProvider(String elementName, String namespace) {
        String key = createKey(elementName, namespace);
        Provider<?> provider = providers.get(key);
        if (provider != null) {
            log.trace("Found provider for <{} xmlns=\"{}\">: {}",
                    elementName, namespace, provider.getClass().getSimpleName());
            return Optional.of(provider);
        }
        log.trace("No provider found for <{} xmlns=\"{}\">", elementName, namespace);
        return Optional.empty();
    }

    /**
     * 移除 Provider。
     *
     * @param elementName 元素名称
     * @param namespace 命名空间
     * @return 被移除的 Provider 的可选对象
     */
    public Optional<Provider<?>> removeProvider(String elementName, String namespace) {
        String key = createKey(elementName, namespace);
        Provider<?> removed = providers.remove(key);
        if (removed != null) {
            log.debug("Removed provider: <{} xmlns=\"{}\">", elementName, namespace);
            return Optional.of(removed);
        }
        return Optional.empty();
    }

    /**
     * 清空所有 Provider。
     */
    public void clear() {
        int size = providers.size();
        providers.clear();
        log.debug("Cleared {} providers", size);
    }

    /**
     * 获取所有已注册的键。
     *
     * @return 注册键的集合
     */
    public Set<String> getRegisteredKeys() {
        return Set.copyOf(providers.keySet());
    }

    /**
     * 检查是否存在 Provider。
     *
     * @param elementName 元素名称
     * @param namespace 命名空间
     * @return 如果存在返回 true，否则返回 false
     */
    public boolean hasProvider(String elementName, String namespace) {
        return providers.containsKey(createKey(elementName, namespace));
    }

    /**
     * 获取 Provider 数量。
     *
     * @return Provider 数量
     */
    public int size() {
        return providers.size();
    }

    private String createKey(String elementName, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return elementName;
        }
        return elementName + ':' + namespace;
    }

    private void registerBuiltInProviders() {
        registerProvider(new BindProvider());
        registerProvider(new PingProvider());
    }

    private void discoverSpiProviders() {
        ServiceLoader<ProtocolProvider> loader = ServiceLoader.load(ProtocolProvider.class);
        int spiCount = 0;
        for (ProtocolProvider protocolProvider : loader) {
            try {
                Provider<?> provider = protocolProvider.createProvider();
                registerProvider(provider);
                spiCount++;
                log.debug("Discovered SPI provider: {}", protocolProvider.getClass().getName());
            } catch (Exception e) {
                log.warn("Failed to load SPI provider: {}", protocolProvider.getClass().getName(), e);
            }
        }
        if (spiCount > 0) {
            log.info("Discovered {} SPI providers", spiCount);
        }
    }
}
