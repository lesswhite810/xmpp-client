package com.example.xmpp.net;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppNetworkException;
import io.netty.handler.ssl.SslHandler;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * SSL/TLS 工具类。
 *
 * <p>负责创建 {@link SslHandler}。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
@UtilityClass
public class SslUtils {

    private static final int DEFAULT_HANDSHAKE_TIMEOUT_SECONDS = XmppConstants.SSL_HANDSHAKE_TIMEOUT_SECONDS;

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

            SSLContext sslContext = SSLContext.getInstance("TLS");

            TrustManager[] trustManagers = config.getCustomTrustManager();
            KeyManager[] keyManagers = config.getKeyManagers();

            if (config.getCustomSslContext() != null) {
                log.debug("Using custom SSLContext");
                sslContext = config.getCustomSslContext();
            } else {
                sslContext.init(keyManagers, trustManagers, new SecureRandom());
            }

            SSLEngine sslEngine = sslContext.createSSLEngine();

            sslEngine.setUseClientMode(true);

            configureProtocols(sslEngine, config.getEnabledSSLProtocols());

            configureCipherSuites(sslEngine, config.getEnabledSSLCiphers());

            SslHandler sslHandler = new SslHandler(sslEngine);

            int handshakeTimeout = config.getHandshakeTimeoutMs() > 0
                    ? config.getHandshakeTimeoutMs()
                    : Math.toIntExact(TimeUnit.SECONDS.toMillis(DEFAULT_HANDSHAKE_TIMEOUT_SECONDS));
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
        if (config.getTlsAuthenticationMode() == XmppClientConfig.TlsAuthenticationMode.MUTUAL
                && config.getCustomSslContext() == null
                && (config.getKeyManagers() == null || config.getKeyManagers().length == 0)) {
            throw new XmppNetworkException("Mutual TLS requires at least one configured KeyManager");
        }
    }

    /**
     * 配置 SSL 协议。
     *
     * @param sslEngine SSL 引擎
     * @param enabledProtocols 启用的协议
     */
    private static void configureProtocols(SSLEngine sslEngine, String[] enabledProtocols) {
        if (enabledProtocols == null || enabledProtocols.length == 0) {
            return;
        }

        Set<String> supportedProtocols = Set.of(sslEngine.getSupportedProtocols());
        String[] protocolsToEnable = Arrays.stream(enabledProtocols)
                .filter(supportedProtocols::contains)
                .toArray(String[]::new);

        if (protocolsToEnable.length > 0) {
            sslEngine.setEnabledProtocols(protocolsToEnable);
            log.debug("Enabled SSL protocols: {}", Arrays.asList(protocolsToEnable));
        }
    }

    /**
     * 配置密码套件。
     *
     * @param sslEngine SSL 引擎
     * @param enabledCiphers 启用的密码套件
     */
    private static void configureCipherSuites(SSLEngine sslEngine, String[] enabledCiphers) {
        if (enabledCiphers == null || enabledCiphers.length == 0) {
            return;
        }

        Set<String> supportedCiphers = Set.of(sslEngine.getSupportedCipherSuites());
        String[] ciphersToEnable = Arrays.stream(enabledCiphers)
                .filter(supportedCiphers::contains)
                .toArray(String[]::new);

        if (ciphersToEnable.length > 0) {
            sslEngine.setEnabledCipherSuites(ciphersToEnable);
            log.debug("Enabled SSL ciphers: {}", Arrays.asList(ciphersToEnable));
        }
    }
}
