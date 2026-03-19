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

import javax.security.sasl.SaslException;
import java.util.Base64;

/**
 * SASL 认证协商器。
 *
 * <p>负责驱动 SASL 认证过程，包括发送初始 auth、处理服务端
 * challenge、校验最终 success，并将认证阶段的报文发送逻辑
 * 从连接状态机中解耦出来。</p>
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
     * 启动 SASL 认证流程。
     *
     * <p>如果当前机制支持初始响应，会在发送 {@link Auth} 前先生成并进行 Base64 编码。
     * 对于 PLAIN 机制，还会在启动前强制校验 TLS 是否已经启用。</p>
     *
     * @throws XmppAuthException 如果认证启动失败
     */
    public ChannelFuture start() throws XmppAuthException {
        if (XmppConstants.SASL_MECH_PLAIN.equals(mechanism.getMechanismName()) && !isTlsEncrypted()) {
            throw new XmppAuthException("PLAIN authentication requires TLS encryption. Please enable TLS before authenticating.");
        }

        log.info("Authenticating with {}", mechanism.getMechanismName());
        String content = "";
        if (mechanism.hasInitialResponse()) {
            try {
                content = encodeSaslContent(mechanism.processChallenge(null));
            } catch (SaslException e) {
                throw new XmppAuthException("Failed to generate initial response", e);
            }
        }
        try {
            return sendStanza(new Auth(mechanism.getMechanismName(), content));
        } catch (IllegalArgumentException e) {
            throw new XmppAuthException("Invalid Auth stanza", e);
        }
    }

    /**
     * 处理服务端返回的 SASL 挑战数据。
     *
     * @param contentB64 Base64 编码的挑战内容
     * @throws XmppAuthException 如果挑战处理失败
     */
    public void handleChallenge(String contentB64) throws XmppAuthException {
        byte[] cContent;
        try {
            cContent = BASE64_DECODER.decode(contentB64 != null ? contentB64 : "");
        } catch (IllegalArgumentException e) {
            throw new XmppAuthException("Invalid challenge content", e);
        }
        byte[] response;
        try {
            response = mechanism.processChallenge(cContent);
        } catch (SaslException e) {
            throw new XmppAuthException("Failed to process challenge", e);
        }
        String responseB64 = encodeSaslContent(response);
        try {
            sendStanza(new SaslResponse(responseB64));
        } catch (IllegalArgumentException e) {
            throw new XmppAuthException("Invalid Response stanza", e);
        }
    }

    /**
     * 处理服务端返回的 SASL 成功结果。
     *
     * @param contentB64 Base64 编码的成功附加数据，可能为空
     * @return 如果认证已经完成则返回 true
     * @throws XmppAuthException 如果成功结果校验失败
     */
    public boolean handleSuccess(String contentB64) throws XmppAuthException {
        if (contentB64 != null && !contentB64.isEmpty()) {
            try {
                mechanism.processChallenge(BASE64_DECODER.decode(contentB64));
            } catch (IllegalArgumentException e) {
                throw new XmppAuthException("Invalid success content", e);
            } catch (SaslException e) {
                throw new XmppAuthException("Failed to verify server signature", e);
            }
        }
        boolean complete = mechanism.isComplete();
        if (complete) {
            log.info("SASL negotiation successful.");
        }
        return complete;
    }

    /**
     * 发送 SASL 阶段使用的扩展元素。
     *
     * @param packet 待发送的协议元素
     * @throws XmppAuthException 如果发送失败
     */
    private ChannelFuture sendStanza(Object packet) throws XmppAuthException {
        if (packet instanceof ExtensionElement element) {
            String xmlString = element.toXml();
            log.debug("Sending SASL stanza: {}", SecurityUtils.summarizeExtensionElement(element));

            ChannelFuture future = ctx.writeAndFlush(ByteBufUtil.writeUtf8(ctx.alloc(), xmlString));
            future.addListener(result -> {
                if (result.isSuccess()) {
                    log.debug("SASL stanza sent successfully");
                } else {
                    log.debug("Failed to send SASL stanza", result.cause());
                    ctx.pipeline().fireExceptionCaught(
                            new XmppAuthException("Failed to send SASL stanza", result.cause()));
                }
            });
            return future;
        } else {
            throw new IllegalArgumentException(
                    "Packet must implement ExtensionElement interface: " + packet.getClass().getName());
        }
    }

    private String encodeSaslContent(byte[] content) {
        if (content == null || content.length == 0) {
            return EMPTY_SASL_CONTENT;
        }
        return BASE64_ENCODER.encodeToString(content);
    }

    /**
     * 检查当前通道是否已经完成 TLS 加密握手。
     *
     * @return 如果当前通道已加密则返回 true
     */
    private boolean isTlsEncrypted() {
        SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        return sslHandler != null
                && sslHandler.handshakeFuture().isDone()
                && sslHandler.handshakeFuture().isSuccess()
                && !sslHandler.handshakeFuture().isCancelled();
    }
}
