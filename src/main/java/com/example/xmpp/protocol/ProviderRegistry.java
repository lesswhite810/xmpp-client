package com.example.xmpp.protocol;

import com.example.xmpp.protocol.provider.BindProvider;
import com.example.xmpp.protocol.provider.ConnectionRequestProvider;
import com.example.xmpp.protocol.provider.PingProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 注册中心。
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
     * @return 单例实例
     */
    public static ProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册 Provider。
     *
     * @param provider Provider 实例
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
     * @return Provider
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
     * @return Provider
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
     * @return Provider
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
     * @return 被移除的 Provider
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
     * @return 注册键集合
     */
    public Set<String> getRegisteredKeys() {
        return Set.copyOf(providers.keySet());
    }

    /**
     * 检查是否存在 Provider。
     *
     * @param elementName 元素名称
     * @param namespace 命名空间
     * @return 是否存在
     */
    public boolean hasProvider(String elementName, String namespace) {
        return providers.containsKey(createKey(elementName, namespace));
    }

    /**
     * 获取 Provider 数量。
     *
     * @return 数量
     */
    public int size() {
        return providers.size();
    }

    private String createKey(String elementName, String namespace) {
        if (StringUtils.isBlank(elementName)) {
            throw new IllegalArgumentException("elementName must not be null or blank");
        }
        if (StringUtils.isEmpty(namespace)) {
            return elementName;
        }
        return elementName + ':' + namespace;
    }

    private void registerBuiltInProviders() {
        registerProvider(new BindProvider());
        registerProvider(new PingProvider());
        registerProvider(new ConnectionRequestProvider());
    }

    private void discoverSpiProviders() {
        ServiceLoader<ProtocolProvider> loader = ServiceLoader.load(ProtocolProvider.class);
        int spiCount = 0;
        for (ProtocolProvider protocolProvider : loader) {
            try {
                registerDiscoveredProvider(protocolProvider);
                spiCount++;
                log.debug("Discovered SPI provider: {}", protocolProvider.getClass().getName());
            } catch (Exception e) {
                log.error("Failed to load SPI provider: {}: {}", protocolProvider.getClass().getName(), e.getMessage());
            }
        }
        if (spiCount > 0) {
            log.info("Discovered {} SPI providers", spiCount);
        }
    }

    private void registerDiscoveredProvider(ProtocolProvider<?> protocolProvider) {
        Provider<?> provider = protocolProvider.createProvider();
        registerProvider(provider);
    }
}
