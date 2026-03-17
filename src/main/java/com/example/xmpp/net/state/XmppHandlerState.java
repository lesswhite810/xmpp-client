package com.example.xmpp.net.state;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.exception.XmppSaslFailureException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.mechanism.SaslMechanism;
import com.example.xmpp.mechanism.SaslMechanismFactory;
import com.example.xmpp.mechanism.SaslNegotiator;
import com.example.xmpp.net.SslUtils;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.sasl.SaslChallenge;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.sasl.SaslSuccess;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.protocol.model.stream.StreamHeader;
import com.example.xmpp.protocol.model.stream.TlsElements.StartTls;
import com.example.xmpp.protocol.model.stream.TlsElements.TlsProceed;
import com.example.xmpp.util.StanzaIdGenerator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * XMPP 处理器状态枚举。
 *
 * <p>定义连接建立、TLS 协商、SASL 认证、资源绑定以及会话激活等阶段的状态行为与转换规则。</p>
 *
 * @since 2026-02-23
 */
@Slf4j
public enum XmppHandlerState implements HandlerState {

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

    CONNECTING {
        @Override
        public void onEnter(StateContext context, ChannelHandlerContext ctx) {
            if (context.getConfig().isUsingDirectTLS()) {
                log.debug("Using Direct TLS, waiting for SSL handshake to complete");
                return;
            }
            log.debug("Opening initial XMPP stream");
            transitionAfterSuccessfulWrite(context, ctx,
                    context.openStream(ctx),
                    "open initial XMPP stream",
                    () -> context.transitionTo(AWAITING_FEATURES, ctx));
        }

        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            log.warn("Received {} in CONNECTING state (Direct TLS mode, waiting for handshake)",
                    msg.getClass().getSimpleName());
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == AWAITING_FEATURES || target == CONNECTING;
        }
    },

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
                return;
            }
            handleInsecureConnectionFeatures(context, ctx, features);
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
            if (features.getMechanisms() != null && !features.getMechanisms().isEmpty()) {
                startSaslAuthentication(context, ctx, features.getMechanisms());
            } else if (features.isBindAvailable()) {
                handleBindRequest(context, ctx);
            } else {
                log.warn("Server features missing both SASL mechanisms and bind capability");
                context.closeConnectionOnError(ctx, "Invalid server features after authentication");
            }
        }

        private void handleInsecureConnectionFeatures(StateContext context, ChannelHandlerContext ctx, StreamFeatures features) {
            XmppClientConfig.SecurityMode mode = context.getConfig().getSecurityMode();

            if (mode == XmppClientConfig.SecurityMode.REQUIRED && !features.isStarttlsAvailable()) {
                log.warn("TLS required by configuration but not available on server");
                context.closeConnectionOnError(ctx, "TLS required but not supported by server");
                return;
            }

            if (features.isStarttlsAvailable() && mode != XmppClientConfig.SecurityMode.DISABLED) {
                initiateStartTls(context, ctx);
            } else if (features.getMechanisms() != null && !features.getMechanisms().isEmpty()) {
                startSaslAuthentication(context, ctx, features.getMechanisms());
            } else if (features.isBindAvailable()) {
                handleBindRequest(context, ctx);
            } else {
                context.closeConnectionOnError(ctx, "Server features missing both SASL mechanisms and bind capability");
            }
        }

        private void initiateStartTls(StateContext context, ChannelHandlerContext ctx) {
            log.info("Initiating STARTTLS negotiation");
            transitionAfterSuccessfulWrite(context, ctx,
                    context.sendStanza(ctx, StartTls.INSTANCE),
                    "send STARTTLS request",
                    () -> context.transitionTo(TLS_NEGOTIATING, ctx));
        }

        private void startSaslAuthentication(StateContext context, ChannelHandlerContext ctx, List<String> serverMechanisms) {
            if (serverMechanisms == null || serverMechanisms.isEmpty()) {
                log.warn("No SASL mechanisms available from server");
                context.closeConnectionOnError(ctx, "No SASL mechanisms available");
                return;
            }

            Set<String> enabledMechanisms = context.getConfig().getEnabledSaslMechanisms();
            Optional<SaslMechanism> best = SaslMechanismFactory.getInstance()
                    .createBestMechanism(serverMechanisms, enabledMechanisms,
                            context.getConfig().getUsername(), context.getConfig().getPassword());

            if (best.isPresent()) {
                startResolvedSaslAuthentication(context, ctx, best.get());
            } else {
                log.warn("No supported SASL mechanism - Server offered: {}, Client enabled: {}",
                        serverMechanisms, enabledMechanisms);
                context.closeConnectionOnError(ctx, "No supported SASL mechanism");
            }
        }

        private void startResolvedSaslAuthentication(StateContext context, ChannelHandlerContext ctx, SaslMechanism mechanism) {
            if ("PLAIN".equals(mechanism.getMechanismName()) && !isSecureConnection(ctx, context.getConfig())) {
                log.warn("PLAIN SASL mechanism requires TLS encryption. Current connection is not secure.");
                context.closeConnectionOnError(ctx, "PLAIN mechanism requires TLS encryption");
                return;
            }

            log.info("Starting SASL authentication using mechanism: {}", mechanism.getMechanismName());
            SaslNegotiator negotiator = new SaslNegotiator(mechanism, ctx);
            context.setSaslNegotiator(negotiator);
            try {
                transitionAfterSuccessfulWrite(context, ctx,
                        negotiator.start(),
                        "send SASL auth stanza",
                        () -> context.transitionTo(SASL_AUTH, ctx));
            } catch (XmppAuthException e) {
                log.warn("Authentication error", e);
                context.closeConnectionOnError(ctx, e);
            }
        }

        private void handleBindRequest(StateContext context, ChannelHandlerContext ctx) {
            log.debug("Sending resource bind request");
            Bind bind = Bind.builder()
                    .resource(context.getConfig().getResource())
                    .build();

            Iq bindIq = new Iq.Builder(Iq.Type.SET)
                    .id(StanzaIdGenerator.newId("bind"))
                    .childElement(bind)
                    .build();
            transitionAfterSuccessfulWrite(context, ctx,
                    context.sendStanza(ctx, bindIq),
                    "send resource bind request",
                    () -> context.transitionTo(BINDING, ctx));
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == TLS_NEGOTIATING
                    || target == SASL_AUTH
                    || target == BINDING
                    || target == CONNECTING;
        }
    },

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

                var sslHandler = SslUtils.createSslHandler(context.getConfig());

                ctx.pipeline().addFirst(sslHandler);
                log.debug("SSL handler added to pipeline, handshake starting");

            } catch (XmppNetworkException e) {
                log.warn("Network error while initializing SSL handler", e);
                context.closeConnectionOnError(ctx, e);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid SSL configuration", e);
                context.closeConnectionOnError(ctx, new XmppNetworkException("Invalid SSL configuration", e));
            }
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == AWAITING_FEATURES || target == CONNECTING;
        }
    },

    SASL_AUTH {
        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            if (context.getSaslNegotiator() == null) {
                log.warn("SASL negotiator is null, cannot process authentication");
                context.closeConnectionOnError(ctx, "SASL negotiator not initialized");
                return;
            }

            try {
                switch (msg) {
                    case SaslChallenge challenge -> handleSaslChallenge(context, challenge)
                    ;
                    case SaslSuccess success -> handleSaslSuccess(context, ctx, success);
                    case SaslFailure failure -> handleSaslFailure(context, ctx, failure);
                    default -> log.debug("Received unexpected message during SASL auth: {}", msg.getClass().getSimpleName());
                }
            } catch (XmppAuthException e) {
                log.warn("SASL authentication error", e);
                context.setSaslNegotiator(null);
                context.closeConnectionOnError(ctx, e);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid SASL authentication data", e);
                context.setSaslNegotiator(null);
                context.closeConnectionOnError(ctx, new XmppAuthException("Invalid SASL authentication data", e));
            }
        }

        private void handleSaslChallenge(StateContext context, SaslChallenge challenge) throws XmppAuthException {
            log.debug("SASL challenge received");
            context.getSaslNegotiator().handleChallenge(challenge.getContent());
        }

        private void handleSaslSuccess(StateContext context, ChannelHandlerContext ctx, SaslSuccess success)
                throws XmppAuthException {
            log.debug("SASL authentication successful");
            if (!context.getSaslNegotiator().handleSuccess(success.getContent())) {
                return;
            }
            log.debug("SASL negotiation completed, reopening stream");
            transitionAfterSuccessfulWrite(context, ctx,
                    context.openStream(ctx),
                    "reopen XMPP stream after SASL success",
                    () -> context.transitionTo(AWAITING_FEATURES, ctx));
        }

        private void handleSaslFailure(StateContext context, ChannelHandlerContext ctx, SaslFailure failure) {
            log.warn("SASL authentication failed - condition: {}",
                    failure.getCondition());
            context.setSaslNegotiator(null);
            context.closeConnectionOnError(ctx, new XmppSaslFailureException(failure));
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == AWAITING_FEATURES || target == CONNECTING;
        }
    },

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
                handleSuccessfulBind(context, ctx);
                return;
            }

            if (iq.getType() == Iq.Type.ERROR) {
                handleFailedBind(context, ctx, iq);
            }
        }

        private void handleSuccessfulBind(StateContext context, ChannelHandlerContext ctx) {
            log.info("Resource binding successful");
            if (!context.getConfig().isSendPresence()) {
                activateSession(context, ctx);
                return;
            }
            log.debug("Sending initial presence");
            transitionAfterSuccessfulWrite(context, ctx,
                    context.sendStanza(ctx, new Presence()),
                    "send initial presence",
                    () -> activateSession(context, ctx));
        }

        private void handleFailedBind(StateContext context, ChannelHandlerContext ctx, Iq iq) {
            String errorDetail = formatBindErrorDetail(iq.getError());
            log.warn("Resource binding failed - {}", errorDetail);
            context.closeConnectionOnError(ctx,
                    new XmppStanzaErrorException("Resource binding failed: " + errorDetail, iq));
        }

        private String formatBindErrorDetail(XmppError error) {
            return error != null
                    ? String.format("condition=%s, type=%s",
                    error.getCondition(), error.getType())
                    : "unknown";
        }

        @Override
        public boolean canTransitionTo(XmppHandlerState target) {
            return target == SESSION_ACTIVE || target == AWAITING_FEATURES || target == CONNECTING;
        }
    },

    SESSION_ACTIVE {
        @Override
        public boolean isSessionActive() {
            return true;
        }

        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Iq iq) {
                log.debug("Received IQ stanza - type: {}, id: {}, from: {}", iq.getType(), iq.getId(), iq.getFrom());

                if (context.getConnection().handleIqRequest(iq)) {
                    log.debug("IQ request handled by IqRequestHandler, id: {}", iq.getId());
                    return;
                }
            }

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
     * 校验当前状态是否允许切换到目标状态。
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
     * 判断当前状态是否可以切换到目标状态。
     *
     * @param target 目标状态
     * @return 如果允许切换则返回 {@code true}
     */
    public abstract boolean canTransitionTo(XmppHandlerState target);

    private static void failOnWriteFailure(StateContext context,
                                           ChannelHandlerContext ctx,
                                           ChannelFuture future,
                                           String action) {
        if (future == null) {
            context.closeConnectionOnError(ctx, "Failed to " + action);
            return;
        }
        future.addListener(result -> {
            if (!result.isSuccess()) {
                context.closeConnectionOnError(ctx,
                        new XmppNetworkException("Failed to " + action, result.cause()));
            }
        });
    }

    private static void transitionAfterSuccessfulWrite(StateContext context,
                                                       ChannelHandlerContext ctx,
                                                       ChannelFuture future,
                                                       String action,
                                                       Runnable onSuccess) {
        if (future == null) {
            context.closeConnectionOnError(ctx, "Failed to " + action);
            return;
        }
        future.addListener(result -> {
            if (!result.isSuccess()) {
                context.closeConnectionOnError(ctx,
                        new XmppNetworkException("Failed to " + action, result.cause()));
                return;
            }
            if (context.isTerminated()) {
                log.debug("Skipping state follow-up for {} because state context is cleared", action);
                return;
            }
            if (!ctx.channel().isActive()) {
                log.debug("Skipping state follow-up for {} because channel is inactive", action);
                return;
            }
            onSuccess.run();
        });
    }

    private static void notifySessionReady(StateContext context) {
        context.getConnection().markConnectionReady();
        context.getConnection().notifyAuthenticated();
    }

    private static void activateSession(StateContext context, ChannelHandlerContext ctx) {
        context.transitionTo(SESSION_ACTIVE, ctx);
        notifySessionReady(context);
    }
}
