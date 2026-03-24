package com.example.xmpp.net;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppNetworkException;
import io.netty.handler.ssl.SslHandler;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * SSL/TLS 工具类。
 *
 * @since 2026-02-09
 */
@Slf4j
@UtilityClass
public class SslUtils {

    private static final String TLS_PROTOCOL = "TLS";

    /**
     * 根据客户端配置创建 {@link SslHandler}。
     *
     * @param config XMPP 客户端配置
     * @return 配置好的 SslHandler
     * @throws XmppNetworkException 如果创建失败
     */
    public static SslHandler createSslHandler(XmppClientConfig config)
            throws XmppNetworkException {
        try {
            log.debug("Creating SslHandler");
            validateTlsAuthenticationConfig(config);

            SSLContext sslContext;

            TrustManager[] trustManagers = config.getCustomTrustManager();
            KeyManager[] keyManagers = config.getKeyManagers();

            if (config.getCustomSslContext() != null) {
                log.debug("Using custom SSLContext");
                sslContext = config.getCustomSslContext();
            } else {
                sslContext = SSLContext.getInstance(TLS_PROTOCOL);
                sslContext.init(keyManagers, trustManagers, new SecureRandom());
            }

            SSLEngine sslEngine = sslContext.createSSLEngine();

            sslEngine.setUseClientMode(true);

            configureProtocols(sslEngine, config.getEnabledSSLProtocols());

            configureCipherSuites(sslEngine, config.getEnabledSSLCiphers());

            SslHandler sslHandler = new SslHandler(sslEngine);

            int handshakeTimeout = config.getHandshakeTimeoutMs() > 0
                    ? config.getHandshakeTimeoutMs()
                    : Math.toIntExact(TimeUnit.SECONDS.toMillis(XmppConstants.SSL_HANDSHAKE_TIMEOUT_SECONDS));
            sslHandler.setHandshakeTimeoutMillis(handshakeTimeout);

            log.debug("SslHandler created successfully");

            return sslHandler;

        } catch (XmppNetworkException e) {
            throw e;
        } catch (Exception e) {
            throw new XmppNetworkException("Failed to create SslHandler", e);
        }
    }

    private static void validateTlsAuthenticationConfig(XmppClientConfig config) throws XmppNetworkException {
        if (config.getCustomSslContext() != null) {
            return; // 自定义 SSLContext，跳过检查
        }

        XmppClientConfig.TlsAuthenticationMode mode = config.getTlsAuthenticationMode();
        TrustManager[] trustManagers = config.getCustomTrustManager();
        KeyManager[] keyManagers = config.getKeyManagers();

        switch (mode) {
            case MUTUAL -> {
                if (ArrayUtils.isEmpty(keyManagers)) {
                    throw new XmppNetworkException("Mutual TLS requires at least one configured KeyManager");
                }
                if (ArrayUtils.isEmpty(trustManagers)) {
                    throw new XmppNetworkException("Mutual TLS requires at least one configured TrustManager");
                }
            }
            case ONE_WAY -> {
                if (ArrayUtils.isEmpty(trustManagers)) {
                    throw new XmppNetworkException("One-Way TLS requires at least one configured TrustManager");
                }
            }
        }
    }

    /**
     * 配置 SSL 协议。
     *
     * @param sslEngine SSL 引擎
     * @param enabledProtocols 启用的协议
     */
    private static void configureProtocols(SSLEngine sslEngine, String[] enabledProtocols) {
        applyEnabledValues(enabledProtocols, Set.of(sslEngine.getSupportedProtocols()),
                sslEngine::setEnabledProtocols, "SSL protocols");
    }

    /**
     * 配置密码套件。
     *
     * @param sslEngine SSL 引擎
     * @param enabledCiphers 启用的密码套件
     */
    private static void configureCipherSuites(SSLEngine sslEngine, String[] enabledCiphers) {
        applyEnabledValues(enabledCiphers, Set.of(sslEngine.getSupportedCipherSuites()),
                sslEngine::setEnabledCipherSuites, "SSL ciphers");
    }

    private static void applyEnabledValues(String[] configuredValues, Set<String> supportedValues,
                                           Consumer<String[]> enabler, String description) {
        if (ArrayUtils.isEmpty(configuredValues)) {
            return;
        }

        String[] valuesToEnable = Arrays.stream(configuredValues)
                .filter(supportedValues::contains)
                .toArray(String[]::new);
        if (ArrayUtils.isNotEmpty(valuesToEnable)) {
            enabler.accept(valuesToEnable);
            log.debug("Enabled {}: {}", description, Arrays.asList(valuesToEnable));
        }
    }
}
