package com.example.xmpp.net.handler.state;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.net.SslUtils;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.sasl.SaslChallenge;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.sasl.SaslSuccess;
import com.example.xmpp.protocol.model.stream.TlsElements.StartTls;
import com.example.xmpp.protocol.model.stream.TlsElements.TlsProceed;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.protocol.model.stream.StreamHeader;
import com.example.xmpp.sasl.SaslMechanism;
import com.example.xmpp.sasl.SaslMechanismFactory;
import com.example.xmpp.sasl.SaslNegotiator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * XMPP 处理器状态枚举。
 *
 * @since 2026-02-23
 */
@Slf4j
public enum XmppHandlerState implements HandlerState {

    // --- 初始状态 ---
    INITIAL {
        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == CONNECTING;
        }

        @Override
        public void validateTransition(XmppHandlerState target) {
            if (!canTransitionTo(target)) {
                throw new IllegalStateException("Cannot transition from INITIAL to " + target);
            }
        }
    },

    // --- 连接阶段 ---

    CONNECTING {
        @Override
        public void onEnter(StateContext context, ChannelHandlerContext ctx) {
            // Direct TLS 模式：等待 SSL 握手完成（不打开流）
            if (context.getConfig().isUsingDirectTLS()) {
                log.debug("Using Direct TLS, waiting for SSL handshake to complete");
                return;
            }
            // 非 Direct TLS 模式：打开初始流并转换状态
            log.debug("Opening initial XMPP stream");
            context.openStreamAndResetDecoder(ctx);
            context.transitionTo(AWAITING_FEATURES, ctx);
        }

        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            // Direct TLS 模式下，等待 SSL 握手完成
            log.warn("Received {} in CONNECTING state (Direct TLS mode, waiting for handshake)",
                    msg.getClass().getSimpleName());
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == AWAITING_FEATURES || target == CONNECTING;
        }
    },

    // --- 等待流特性 ---

    AWAITING_FEATURES {
        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof StreamFeatures features)) {
                log.debug("Received {} while awaiting features, ignoring", msg.getClass().getSimpleName());
                return;
            }

            handleFeatures(context, ctx, features);
        }

        private void handleFeatures(StateContext context, ChannelHandlerContext ctx, StreamFeatures features) {
            logFeaturesDebug(features);

            if (isSecureConnection(ctx, context.getConfig())) {
                handleSecureConnectionFeatures(context, ctx, features);
            } else {
                handleInsecureConnectionFeatures(context, ctx, features);
            }
        }

        private boolean isSecureConnection(ChannelHandlerContext ctx, XmppClientConfig config) {
            return config.isUsingDirectTLS() || ctx.pipeline().get(SslHandler.class) != null;
        }

        private void logFeaturesDebug(StreamFeatures features) {
            log.debug("Handling features - starttls: {}, mechanisms: {}, bind: {}",
                    features.isStarttlsAvailable(),
                    features.getMechanisms() != null ? features.getMechanisms().size() : 0,
                    features.isBindAvailable());
        }

        private void handleSecureConnectionFeatures(StateContext context, ChannelHandlerContext ctx, StreamFeatures features) {
            boolean hasSaslMechanisms = features.getMechanisms() != null && !features.getMechanisms().isEmpty();

            if (hasSaslMechanisms) {
                startSaslAuthentication(context, ctx, features.getMechanisms());
            } else if (features.isBindAvailable()) {
                handleBindRequest(context, ctx);
            } else {
                log.error("Server features missing both SASL mechanisms and bind capability");
                context.closeConnectionOnError(ctx, "Invalid server features after authentication");
            }
        }

        private void handleInsecureConnectionFeatures(StateContext context, ChannelHandlerContext ctx, StreamFeatures features) {
            XmppClientConfig.SecurityMode mode = context.getConfig().getSecurityMode();

            if (mode == XmppClientConfig.SecurityMode.REQUIRED && !features.isStarttlsAvailable()) {
                log.error("TLS required by configuration but not available on server");
                context.closeConnectionOnError(ctx, "TLS required but not supported by server");
                return;
            }

            if (features.isStarttlsAvailable() && mode != XmppClientConfig.SecurityMode.DISABLED) {
                initiateStartTls(context, ctx);
            } else if (features.isBindAvailable()) {
                // SASL 认证成功后，服务器不再返回 mechanisms，但会返回 bind
                // 此时应该直接进入绑定阶段
                handleBindRequest(context, ctx);
            } else {
                startSaslAuthentication(context, ctx, features.getMechanisms());
            }
        }

        private void initiateStartTls(StateContext context, ChannelHandlerContext ctx) {
            log.info("Initiating STARTTLS negotiation");
            context.sendStanza(ctx, StartTls.INSTANCE);
            context.transitionTo(TLS_NEGOTIATING, ctx);
        }

        private void startSaslAuthentication(StateContext context, ChannelHandlerContext ctx, List<String> serverMechanisms) {
            if (serverMechanisms == null || serverMechanisms.isEmpty()) {
                log.error("No SASL mechanisms available from server");
                context.closeConnectionOnError(ctx, "No SASL mechanisms available");
                return;
            }

            Set<String> enabledMechanisms = context.getConfig().getEnabledSaslMechanisms();
            Optional<SaslMechanism> best = SaslMechanismFactory.getInstance()
                    .createBestMechanism(serverMechanisms, enabledMechanisms,
                            context.getConfig().getUsername(), context.getConfig().getPassword());

            if (best.isPresent()) {
                SaslMechanism mech = best.get();

                // 安全检查：PLAIN 机制只能在 TLS 加密连接上使用
                if ("PLAIN".equals(mech.getMechanismName()) && !isSecureConnection(ctx, context.getConfig())) {
                    log.error("PLAIN SASL mechanism requires TLS encryption. Current connection is not secure.");
                    context.closeConnectionOnError(ctx, "PLAIN mechanism requires TLS encryption");
                    return;
                }

                log.info("Starting SASL authentication using mechanism: {}", mech.getMechanismName());
                SaslNegotiator negotiator = new SaslNegotiator(mech, ctx);
                context.setSaslNegotiator(negotiator);
                try {
                    negotiator.start();
                    context.transitionTo(SASL_AUTH, ctx);
                } catch (XmppAuthException e) {
                    log.error("Authentication error", e);
                    context.closeConnectionOnError(ctx, "Authentication error: " + e.getMessage());
                }
            } else {
                log.error("No supported SASL mechanism - Server offered: {}, Client enabled: {}",
                        serverMechanisms, enabledMechanisms);
                context.closeConnectionOnError(ctx, "No supported SASL mechanism");
            }
        }

        private void handleBindRequest(StateContext context, ChannelHandlerContext ctx) {
            log.debug("Sending resource bind request");
            String resource = context.getConfig().getResource();

            Bind bind = Bind.builder()
                    .resource(resource)
                    .build();

            Iq bindIq = new Iq.Builder(Iq.Type.SET)
                    .id(context.generateId("bind"))
                    .childElement(bind)
                    .build();

            context.sendStanza(ctx, bindIq);
            context.transitionTo(BINDING, ctx);
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == TLS_NEGOTIATING
                    || target == SASL_AUTH
                    || target == BINDING
                    || target == CONNECTING;
        }
    },

    // --- TLS 协商 ---

    TLS_NEGOTIATING {
        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof TlsProceed)) {
                log.debug("Received {} during TLS negotiation", msg.getClass().getSimpleName());
                return;
            }

            handleStartTlsProceed(context, ctx);
        }

        private void handleStartTlsProceed(StateContext context, ChannelHandlerContext ctx) {
            try {
                log.info("TLS proceed received, starting SSL handshake");

                String hostname = context.getConfig().getHost() != null
                        ? context.getConfig().getHost()
                        : context.getConfig().getXmppServiceDomain();
                int port = context.getConfig().getPort() > 0 ? context.getConfig().getPort() : 5222;

                var sslHandler = SslUtils.createSslHandler(hostname, port, context.getConfig());

                ctx.pipeline().addFirst(sslHandler);
                log.debug("SSL handler added to pipeline, handshake starting");

                // 保持 TLS_NEGOTIATING 状态，等待 SslHandshakeCompletionEvent

            } catch (XmppNetworkException e) {
                log.error("Network error while initializing SSL handler", e);
                context.closeConnectionOnError(ctx, e);
            } catch (IllegalArgumentException e) {
                log.error("Invalid SSL configuration", e);
                context.closeConnectionOnError(ctx, e);
            }
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == AWAITING_FEATURES || target == CONNECTING;
        }
    },

    // --- SASL 认证 ---

    SASL_AUTH {
        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            // 检查 SASL negotiator 是否存在
            if (context.getSaslNegotiator() == null) {
                log.error("SASL negotiator is null, cannot process authentication");
                context.closeConnectionOnError(ctx, "SASL negotiator not initialized");
                return;
            }

            try {
                switch (msg) {
                    case SaslChallenge challenge -> {
                        log.debug("SASL challenge received");
                        context.getSaslNegotiator().handleChallenge(challenge.getContent());
                    }
                    case SaslSuccess success -> {
                        log.debug("SASL authentication successful");
                        if (context.getSaslNegotiator().handleSuccess(success.getContent())) {
                            log.debug("SASL negotiation completed, reopening stream");
                            context.openStreamAndResetDecoder(ctx);
                            context.transitionTo(AWAITING_FEATURES, ctx);
                        }
                    }
                    case SaslFailure failure -> {
                        log.error("SASL authentication failed - condition: {}, text: {}",
                                failure.getCondition(), failure.getText());
                        // 清理 SASL negotiator 避免重连时残留状态
                        context.setSaslNegotiator(null);
                        context.closeConnectionOnError(ctx, "Authentication failed: " + failure.getCondition());
                    }
                    default -> log.debug("Received unexpected message during SASL auth: {}", msg.getClass().getSimpleName());
                }
            } catch (com.example.xmpp.exception.XmppAuthException e) {
                log.error("SASL authentication error", e);
                // 清理 SASL negotiator 避免重连时残留状态
                context.setSaslNegotiator(null);
                context.closeConnectionOnError(ctx, e);
            } catch (IllegalArgumentException e) {
                log.error("Invalid SASL authentication data", e);
                // 清理 SASL negotiator 避免重连时残留状态
                context.setSaslNegotiator(null);
                context.closeConnectionOnError(ctx, e);
            }
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == AWAITING_FEATURES || target == CONNECTING;
        }
    },

    // --- 资源绑定 ---

    BINDING {
        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof Iq iq)) {
                log.debug("Received {} while binding, ignoring", msg.getClass().getSimpleName());
                return;
            }

            handleBindResponse(context, ctx, iq);
        }

        private void handleBindResponse(StateContext context, ChannelHandlerContext ctx, Iq iq) {
            if (iq.getType() == Iq.Type.RESULT) {
                log.info("Resource binding successful");
                context.transitionTo(SESSION_ACTIVE, ctx);

                if (context.getConfig().isSendPresence()) {
                    log.debug("Sending initial presence");
                    context.sendStanza(ctx, new Presence());
                }

                context.getConnection().notifyAuthenticated(false);
            } else if (iq.getType() == Iq.Type.ERROR) {
                XmppError error = iq.getError();
                String errorDetail = error != null
                        ? String.format("condition=%s, type=%s, text=%s",
                        error.getCondition(), error.getType(), error.getText())
                        : "unknown";
                log.error("Resource binding failed - {}", errorDetail);
                context.closeConnectionOnError(ctx, "Resource binding failed: " + errorDetail);
            }
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == SESSION_ACTIVE || target == AWAITING_FEATURES || target == CONNECTING;
        }
    },

    // --- 会话激活 ---

    SESSION_ACTIVE {
        @Override
        public boolean isSessionActive() {
            return true;
        }

        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Iq iq) {
                log.debug("Received IQ stanza - type: {}, id: {}, from: {}", iq.getType(), iq.getId(), iq.getFrom());

                // 使用 IqRequestHandler 处理 IQ 请求
                if (context.getConnection().handleIqRequest(iq)) {
                    log.debug("IQ request handled by IqRequestHandler, id: {}", iq.getId());
                    return;
                }
            }

            // 其他 stanza 类型统一处理
            if (msg instanceof XmppStanza stanza) {
                log.debug("Received Stanza - type: {}", stanza.getClass().getSimpleName());
                context.getConnection().notifyStanzaReceived(stanza);
            } else {
                log.debug("Received unhandled message in SESSION_ACTIVE: {}", msg.getClass().getSimpleName());
            }
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == CONNECTING;
        }
    };

    @Override
    public String getName() {
        return name();
    }

    /**
     * 验证状态转换是否合法。
     *
     * @param target 目标状态
     * @throws IllegalStateException 如果状态转换不合法
     */
    public void validateTransition(XmppHandlerState target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("Invalid state transition: %s -> %s", this.name(), target.name()));
        }
    }

    /**
     * 检查是否可以转换到目标状态。
     *
     * @param target 目标状态
     * @return 如果允许转换返回 true
     */
    public abstract boolean canTransitionTo(XmppHandlerState target);
}
