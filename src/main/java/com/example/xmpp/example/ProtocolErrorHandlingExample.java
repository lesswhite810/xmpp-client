package com.example.xmpp.example;

import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.config.SystemService;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.config.XmppConfigKeys;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppSaslFailureException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.exception.XmppStreamErrorException;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * 协议错误处理示例。
 *
 * <p>演示如何分别处理连接级异常和 IQ 请求级异常。</p>
 *
 * @since 2026-03-11
 */
@Slf4j
public final class ProtocolErrorHandlingExample {

    private ProtocolErrorHandlingExample() {
    }

    /**
     * 示例入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SystemService systemService = createSystemServiceBean();
        XmppClientConfig config = XmppClientConfig.fromSystemService(systemService, "192.168.10.11");

        handleSynchronousConnect(config);
        handleAsynchronousConnect(config);
        handleIqRequestError(config);
    }

    private static void handleSynchronousConnect(XmppClientConfig config) {
        XmppTcpConnection connection = new XmppTcpConnection(config);
        try {
            connection.connect();
            log.info("Synchronous connect succeeded");
        } catch (XmppSaslFailureException e) {
            log.warn("SASL failure: condition={}, text={}",
                    e.getSaslFailure().getCondition(), e.getSaslFailure().getText());
        } catch (XmppStreamErrorException e) {
            log.warn("Stream error: condition={}, text={}",
                    e.getStreamError().getCondition(), e.getStreamError().getText());
        } catch (XmppAuthException e) {
            log.warn("Authentication failed: {}", e.getMessage(), e);
        } catch (XmppException e) {
            log.error("Connect failed: {}", e.getMessage(), e);
        } finally {
            connection.disconnect();
        }
    }

    private static void handleAsynchronousConnect(XmppClientConfig config) {
        XmppTcpConnection connection = new XmppTcpConnection(config);
        try {
            connection.connectAsync()
                    .thenRun(() -> log.info("Async connect succeeded"))
                    .exceptionally(error -> {
                        Throwable cause = unwrap(error);
                        if (cause instanceof XmppSaslFailureException e) {
                            log.warn("Async SASL failure: condition={}, text={}",
                                    e.getSaslFailure().getCondition(), e.getSaslFailure().getText());
                        } else if (cause instanceof XmppStreamErrorException e) {
                            log.warn("Async stream error: condition={}, text={}",
                                    e.getStreamError().getCondition(), e.getStreamError().getText());
                        } else {
                            log.error("Async connect failed", cause);
                        }
                        return null;
                    })
                    .join();
        } catch (XmppException e) {
            log.error("Failed to start async connect", e);
        } finally {
            connection.disconnect();
        }
    }

    private static void handleIqRequestError(XmppClientConfig config) {
        XmppTcpConnection connection = new XmppTcpConnection(config);
        try {
            connection.connect();

            Iq request = new Iq.Builder(Iq.Type.GET)
                    .id("admin-query-1")
                    .to(config.getXmppServiceDomain())
                    .build();

            XmppStanza response = connection.sendIqPacketAsync(request).join();
            log.info("IQ request succeeded: {}", response.getClass().getSimpleName());
        } catch (XmppStanzaErrorException e) {
            log.warn("IQ error: id={}, condition={}, text={}",
                    e.getErrorIq().getId(),
                    e.getXmppError() != null ? e.getXmppError().getCondition() : null,
                    e.getXmppError() != null ? e.getXmppError().getText() : null);
        } catch (CompletionException e) {
            Throwable cause = unwrap(e);
            if (cause instanceof XmppStanzaErrorException stanzaError) {
                log.warn("IQ error: id={}, condition={}, text={}",
                        stanzaError.getErrorIq().getId(),
                        stanzaError.getXmppError() != null ? stanzaError.getXmppError().getCondition() : null,
                        stanzaError.getXmppError() != null ? stanzaError.getXmppError().getText() : null);
            } else {
                log.error("IQ request failed", cause);
            }
        } catch (XmppException e) {
            log.error("Connection setup failed before IQ request", e);
        } finally {
            connection.disconnect();
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException completionException && completionException.getCause() != null
                ? completionException.getCause()
                : error;
    }

    private static SystemService createSystemServiceBean() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(XmppConfigKeys.XMPP_SERVICE_DOMAIN, "example.com");
        values.put(XmppConfigKeys.USERNAME, "admin");
        values.put(XmppConfigKeys.PASSWORD, "admin-password");
        values.put(XmppConfigKeys.RESOURCE, "error-console");
        values.put(XmppConfigKeys.SECURITY_MODE, XmppClientConfig.SecurityMode.REQUIRED.name());
        values.put(XmppConfigKeys.PORT, "5222");
        values.put(XmppConfigKeys.CONNECT_TIMEOUT, "15000");
        values.put(XmppConfigKeys.READ_TIMEOUT, "60000");
        values.put(XmppConfigKeys.SEND_PRESENCE, "true");
        values.put(XmppConfigKeys.DIRECT_TLS, "false");
        values.put(XmppConfigKeys.ENABLED_SASL_MECHANISMS, "SCRAM-SHA-256,PLAIN");
        return values::get;
    }
}