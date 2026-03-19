package com.example.xmpp.mechanism;

import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.sasl.Auth;
import com.example.xmpp.protocol.model.sasl.SaslResponse;
import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.XmppConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SASL 认证协商器。
 *
 * <p>负责驱动 SASL 认证过程，包括发送初始 {@code auth}、处理服务端
 * {@code challenge}、校验最终 {@code success}，并将认证阶段的报文发送逻辑
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
            byte[] init;
            try {
                init = mechanism.processChallenge(null);
            } catch (javax.security.sasl.SaslException e) {
                throw new XmppAuthException("Failed to generate initial response", e);
            }
            if (init != null) {
                content = BASE64_ENCODER.encodeToString(init);
            } else {
                content = "=";
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
        byte[] cContent = BASE64_DECODER.decode(contentB64 != null ? contentB64 : "");
        byte[] response;
        try {
            response = mechanism.processChallenge(cContent);
        } catch (javax.security.sasl.SaslException e) {
            throw new XmppAuthException("Failed to process challenge", e);
        }
        String responseB64 = BASE64_ENCODER.encodeToString(response);
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
     * @return 如果认证已经完成则返回 {@code true}
     * @throws XmppAuthException 如果成功结果校验失败
     */
    public boolean handleSuccess(String contentB64) throws XmppAuthException {
        if (contentB64 != null && !contentB64.isEmpty()) {
            try {
                mechanism.processChallenge(BASE64_DECODER.decode(contentB64));
            } catch (javax.security.sasl.SaslException e) {
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
            log.debug("Sending SASL stanza: {}", SecurityUtils.summarizeXml(xmlString));

            int bufferSize = xmlString.length() * XmppConstants.UTF8_MAX_BYTES_PER_CHAR;
            ByteBuf buf = ctx.alloc().buffer(bufferSize);
            buf.writeCharSequence(xmlString, StandardCharsets.UTF_8);
            final ByteBuf bufToWrite = buf;
            ChannelFuture future = ctx.writeAndFlush(bufToWrite);
            future.addListener(result -> {
                if (result.isSuccess()) {
                    log.debug("SASL stanza sent successfully");
                } else {
                    log.debug("Failed to send SASL stanza", result.cause());
                    if (bufToWrite.refCnt() > 0) {
                        bufToWrite.release();
                    }
                    ctx.executor().execute(() ->
                            ctx.pipeline().fireExceptionCaught(
                                    new XmppAuthException("Failed to send SASL stanza", result.cause())));
                }
            });
            return future;
        } else {
            throw new IllegalArgumentException(
                    "Packet must implement ExtensionElement interface: " + packet.getClass().getName());
        }
    }

    /**
     * 检查当前通道是否已经完成 TLS 加密握手。
     *
     * @return 如果当前通道已加密则返回 {@code true}
     */
    private boolean isTlsEncrypted() {
        return ctx.pipeline().get(SslHandler.class) != null
                && ctx.pipeline().get(SslHandler.class).handshakeFuture().isDone()
                && ctx.pipeline().get(SslHandler.class).handshakeFuture().isSuccess()
                && !ctx.pipeline().get(SslHandler.class).handshakeFuture().isCancelled();
    }
}
