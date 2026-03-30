package com.example.xmpp.mechanism;

import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.sasl.Auth;
import com.example.xmpp.protocol.model.sasl.SaslResponse;
import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.XmppConstants;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import javax.security.sasl.SaslException;
import java.util.Base64;

/**
 * SASL 认证协商器。
 *
 * @since 2026-02-09
 */
@Slf4j
@RequiredArgsConstructor
public class SaslNegotiator {

    /**
     * Base64 编码器。
     */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

    /**
     * Base64 解码器。
     */
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final String EMPTY_SASL_CONTENT = "=";

    private final SaslMechanism mechanism;
    private final ChannelHandlerContext ctx;

    /**
     * 启动 SASL 认证。
     *
     * @return 发送结果
     * @throws XmppAuthException 认证失败
     */
    public ChannelFuture start() throws XmppAuthException {
        String mechanismName = resolveMechanismName();
        if (XmppConstants.SASL_MECH_PLAIN.equals(mechanismName) && !isTlsEncrypted()) {
            throw new XmppAuthException("PLAIN authentication requires TLS encryption. Please enable TLS before authenticating.");
        }

        log.info("Authenticating with {}", mechanismName);
        String content;
        try {
            content = encodeSaslContent(mechanism.processChallenge(null));
        } catch (SaslException e) {
            throw new XmppAuthException("Failed to generate initial response");
        }
        try {
            return sendStanza(new Auth(mechanismName, content));
        } catch (IllegalArgumentException e) {
            throw new XmppAuthException("Invalid Auth stanza");
        }
    }

    /**
     * 处理服务端挑战。
     *
     * @param contentB64 Base64 编码的挑战内容
     * @throws XmppAuthException 认证失败
     */
    public void handleChallenge(String contentB64) throws XmppAuthException {
        byte[] cContent;
        try {
            cContent = BASE64_DECODER.decode(contentB64 != null ? contentB64 : "");
        } catch (IllegalArgumentException e) {
            throw new XmppAuthException("Invalid challenge content");
        }
        byte[] response;
        try {
            response = mechanism.processChallenge(cContent);
        } catch (SaslException e) {
            throw new XmppAuthException("Failed to process challenge");
        }
        String responseB64 = encodeSaslContent(response);
        try {
            sendStanza(new SaslResponse(responseB64));
        } catch (IllegalArgumentException e) {
            throw new XmppAuthException("Invalid Response stanza");
        }
    }

    /**
     * 处理服务端成功结果。
     *
     * @param contentB64 Base64 编码的附加数据
     * @return 是否完成
     * @throws XmppAuthException 认证失败
     */
    public boolean handleSuccess(String contentB64) throws XmppAuthException {
        if (StringUtils.isNotEmpty(contentB64)) {
            try {
                mechanism.processChallenge(BASE64_DECODER.decode(contentB64));
            } catch (IllegalArgumentException e) {
                throw new XmppAuthException("Invalid success content");
            } catch (SaslException e) {
                throw new XmppAuthException("Failed to verify server signature");
            }
        }
        boolean complete = mechanism.isComplete();
        if (complete) {
            log.info("SASL negotiation successful.");
        }
        return complete;
    }

    /**
     * 发送 SASL 扩展元素。
     *
     * @param packet 待发送的协议元素
     * @return 发送结果
     */
    private ChannelFuture sendStanza(Object packet) {
        if (packet == null) {
            throw new IllegalArgumentException("Packet must not be null");
        }
        if (!(packet instanceof ExtensionElement element)) {
            throw new IllegalArgumentException(
                    "Packet must implement ExtensionElement interface: " + packet.getClass().getName());
        }

        String xmlString = element.toXml();
        log.debug("Sending SASL stanza: {}", SecurityUtils.summarizeExtensionElement(element));

        ChannelFuture future = ctx.writeAndFlush(ByteBufUtil.writeUtf8(ctx.alloc(), xmlString));
        future.addListener(result -> {
            if (result.isSuccess()) {
                log.debug("SASL stanza sent successfully");
                return;
            }

            Throwable cause = result.cause();
            log.error("Failed to send SASL stanza - ErrorType: {}",
                    cause != null ? cause.getClass().getSimpleName() : "unknown");
            ctx.pipeline().fireExceptionCaught(new XmppAuthException("Failed to send SASL stanza", cause));
        });
        return future;
    }

    private String encodeSaslContent(byte[] content) {
        if (ArrayUtils.isEmpty(content)) {
            return EMPTY_SASL_CONTENT;
        }
        return BASE64_ENCODER.encodeToString(content);
    }

    private String resolveMechanismName() throws XmppAuthException {
        String mechanismName = mechanism.getMechanismName();
        if (StringUtils.isBlank(mechanismName)) {
            throw new XmppAuthException("SASL mechanism name must not be null or blank");
        }
        return mechanismName;
    }

    /**
     * 检查当前通道是否已加密。
     *
     * @return 是否已加密
     */
    private boolean isTlsEncrypted() {
        SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        return sslHandler != null
                && sslHandler.handshakeFuture().isDone()
                && sslHandler.handshakeFuture().isSuccess()
                && !sslHandler.handshakeFuture().isCancelled();
    }
}
