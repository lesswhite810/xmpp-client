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
import com.example.xmpp.protocol.model.stream.TlsElements.StartTls;
import com.example.xmpp.protocol.model.stream.TlsElements.TlsProceed;
import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.StanzaIdGenerator;
import com.example.xmpp.util.XmppConstants;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

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
    CONNECTING {
        @Override
        public void handleMessage(StateContext context, ChannelHandlerContext ctx, Object msg) {
            log.warn("Received {} in CONNECTING state (Direct TLS mode, waiting for handshake)",
                    msg.getClass().getSimpleName());
        }

        @Override
        public void handleUserEvent(StateContext context, ChannelHandlerContext ctx, Object evt) {
            if (!(evt instanceof SslHandshakeCompletionEvent sslEvent) || !sslEvent.isSuccess()) {
                return;
            }
            if (!context.getConfig().isUsingDirectTLS()) {
                log.debug("Ignoring SSL handshake completion in CONNECTING because Direct TLS is disabled");
                return;
            }
            log.info("Direct TLS handshake completed, opening initial XMPP stream");
            openStreamAfterTlsHandshake(context, ctx, "reopen stream after TLS handshake");
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
            log.info("Handling features - starttls: {}, mechanisms: {}, bind: {}",
                    features.isStarttlsAvailable(),
                    features.getMechanisms() != null ? features.getMechanisms().size() : 0,
                    features.isBindAvailable());
        }

        private void handleSecureConnectionFeatures(StateContext context, ChannelHandlerContext ctx, StreamFeatures features) {
            if (CollectionUtils.isNotEmpty(features.getMechanisms())) {
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
            } else if (CollectionUtils.isNotEmpty(features.getMechanisms())) {
                startSaslAuthentication(context, ctx, features.getMechanisms());
            } else if (features.isBindAvailable()) {
                handleBindRequest(context, ctx);
            } else {
                context.closeConnectionOnError(ctx, "Server features missing both SASL mechanisms and bind capability");
            }
        }

        private void initiateStartTls(StateContext context, ChannelHandlerContext ctx) {
            log.info("Initiating STARTTLS negotiation");
            context.transitionBeforeWrite(TLS_NEGOTIATING,
                    ctx,
                    "send STARTTLS request",
                    () -> context.sendStanza(ctx, StartTls.INSTANCE));
        }

        private void startSaslAuthentication(StateContext context, ChannelHandlerContext ctx, List<String> serverMechanisms) {
            if (CollectionUtils.isEmpty(serverMechanisms)) {
                log.error("No SASL mechanisms available from server");
                context.closeConnectionOnError(ctx, "No SASL mechanisms available");
                return;
            }

            Set<String> enabledMechanisms = context.getConfig().getEnabledSaslMechanisms();
            char[] password = context.getConfig().getPassword();
            Optional<SaslMechanism> best;
            try {
                best = SaslMechanismFactory.getInstance()
                        .createBestMechanism(serverMechanisms, enabledMechanisms,
                                context.getConfig().getUsername(), password);
            } finally {
                SecurityUtils.clear(password);
            }

            if (best.isPresent()) {
                startResolvedSaslAuthentication(context, ctx, best.get());
            } else {
                log.error("No supported SASL mechanism - Server offered: {}, Client enabled: {}",
                        serverMechanisms, enabledMechanisms);
                context.closeConnectionOnError(ctx, "No supported SASL mechanism");
            }
        }

        private void startResolvedSaslAuthentication(StateContext context, ChannelHandlerContext ctx, SaslMechanism mechanism) {
            String mechanismName = mechanism.getMechanismName();
            if (StringUtils.isBlank(mechanismName)) {
                context.closeConnectionOnError(ctx, "Invalid SASL mechanism name");
                return;
            }
            if (XmppConstants.SASL_MECH_PLAIN.equals(mechanismName) && !isSecureConnection(ctx, context.getConfig())) {
                log.error("PLAIN SASL mechanism requires TLS encryption. Current connection is not secure.");
                context.closeConnectionOnError(ctx, "PLAIN mechanism requires TLS encryption");
                return;
            }

            log.info("Starting SASL authentication using mechanism: {}", mechanismName);
            SaslNegotiator negotiator = new SaslNegotiator(mechanism, ctx);
            context.setSaslNegotiator(negotiator);
            try {
                context.transitionBeforeWrite(SASL_AUTH,
                        ctx,
                        "send SASL auth stanza",
                        negotiator::start);
            } catch (XmppAuthException e) {
                log.error("Authentication error - ErrorType: {}", e.getClass().getSimpleName());
                context.closeConnectionOnError(ctx, e);
            }
        }

        private void handleBindRequest(StateContext context, ChannelHandlerContext ctx) {
            log.info("Sending resource bind request");
            Bind bind = Bind.builder()
                    .resource(context.getConfig().getResource())
                    .build();

            Iq bindIq = new Iq.Builder(Iq.Type.SET)
                    .id(StanzaIdGenerator.newId(BIND_STANZA_ID_PREFIX))
                    .childElement(bind)
                    .build();
            context.transitionBeforeWrite(BINDING,
                    ctx,
                    "send resource bind request",
                    () -> context.sendStanza(ctx, bindIq));
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
                log.error("Network error while initializing SSL handler - ErrorType: {}",
                        e.getClass().getSimpleName());
                context.closeConnectionOnError(ctx, e);
            } catch (IllegalArgumentException e) {
                log.error("Invalid SSL configuration - ErrorType: {}", e.getClass().getSimpleName());
                context.closeConnectionOnError(ctx, new XmppNetworkException("Invalid SSL configuration", e));
            }
        }

        @Override
        public void handleUserEvent(StateContext context, ChannelHandlerContext ctx, Object evt) {
            if (!(evt instanceof SslHandshakeCompletionEvent sslEvent) || !sslEvent.isSuccess()) {
                return;
            }
            log.info("STARTTLS handshake completed, reopening XMPP stream");
            openStreamAfterTlsHandshake(context, ctx, "reopen stream after TLS handshake");
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
                log.error("SASL negotiator is null, cannot process authentication");
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
                log.error("SASL authentication error - ErrorType: {}", e.getClass().getSimpleName());
                context.setSaslNegotiator(null);
                context.closeConnectionOnError(ctx, e);
            } catch (IllegalArgumentException e) {
                log.error("Invalid SASL authentication data - ErrorType: {}", e.getClass().getSimpleName());
                context.setSaslNegotiator(null);
                context.closeConnectionOnError(ctx, new XmppAuthException("Invalid SASL authentication data"));
            }
        }

        private void handleSaslChallenge(StateContext context, SaslChallenge challenge) throws XmppAuthException {
            log.debug("SASL challenge received");
            context.getSaslNegotiator().handleChallenge(challenge.content());
        }

        private void handleSaslSuccess(StateContext context, ChannelHandlerContext ctx, SaslSuccess success)
                throws XmppAuthException {
            log.info("SASL authentication successful");
            if (!context.getSaslNegotiator().handleSuccess(success.content())) {
                return;
            }
            log.info("SASL negotiation completed, reopening stream");
            context.transitionBeforeWrite(AWAITING_FEATURES,
                    ctx,
                    "reopen XMPP stream after SASL success",
                    () -> context.openStream(ctx));
        }

        private void handleSaslFailure(StateContext context, ChannelHandlerContext ctx, SaslFailure failure) {
            log.error("SASL authentication failed - condition: {}",
                    failure.condition());
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
                context.activateSession(ctx);
                return;
            }
            log.info("Sending initial presence");
            context.transitionAfterSuccessfulWrite(ctx,
                    context.sendStanza(ctx, new Presence.Builder().build()),
                    "send initial presence",
                    () -> context.activateSession(ctx));
        }

        private void handleFailedBind(StateContext context, ChannelHandlerContext ctx, Iq iq) {
            String errorDetail = formatBindErrorDetail(iq.getError());
            log.error("Resource binding failed - {}", errorDetail);
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

    private static final String BIND_STANZA_ID_PREFIX = "bind";

    @Override
    public String getName() {
        return name();
    }

    @Override
    public boolean isSessionActive() {
        return false;
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
     * @return 是否允许切换
     */
    public abstract boolean canTransitionTo(XmppHandlerState target);

    private static void openStreamAfterTlsHandshake(StateContext context, ChannelHandlerContext ctx, String action) {
        context.transitionBeforeWrite(AWAITING_FEATURES, ctx, action, () -> context.openStream(ctx));
    }

}
