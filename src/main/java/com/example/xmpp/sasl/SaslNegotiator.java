package com.example.xmpp.sasl;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.exception.XmppAuthException;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.sasl.Auth;
import com.example.xmpp.protocol.model.sasl.SaslResponse;
import com.example.xmpp.util.SecurityUtils;
import com.example.xmpp.util.XmlStringBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SASL 认证协商器。
 *
 * <p>处理 SASL 认证流程，将 SASL 逻辑从 XmppNettyHandler 状态机中解耦。
 * 负责发送 Auth 请求、处理 Challenge、验证 Success。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
@RequiredArgsConstructor
public class SaslNegotiator {

    /** UTF-8 编码的最大字节/字符比率（安全余量） */
    private static final int UTF8_MAX_BYTES_PER_CHAR = XmppConstants.UTF8_MAX_BYTES_PER_CHAR;

    /** Base64 编码器实例 */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

    /** Base64 解码器实例 */
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final SaslMechanism mechanism;
    private final ChannelHandlerContext ctx;

    /**
     * 启动 SASL 认证流程。
     *
     * <p>发送 Auth 元素和初始响应（如果机制支持）。</p>
     *
     * @throws XmppAuthException 如果认证启动失败
     */
    public void start() throws XmppAuthException {
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
            sendStanza(new Auth(mechanism.getMechanismName(), content));
        } catch (IllegalArgumentException e) {
            throw new XmppAuthException("Invalid Auth stanza", e);
        }
    }

    /**
     * 处理服务器发送的 Challenge。
     *
     * <p>解码 Challenge 数据，调用 SASL 机制处理，发送 Response。</p>
     *
     * @param contentB64 Base64 编码的 Challenge 内容
     * @throws XmppAuthException 如果处理 Challenge 失败
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
     * 处理服务器发送的 Success。
     *
     * <p>验证最终的服务器签名（如果机制需要）。</p>
     *
     * @param contentB64 Base64 编码的 Success 内容（可选）
     * @return 如果认证完成返回 true
     * @throws XmppAuthException 如果验证失败
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
     * 发送 SASL 协议 stanza。
     *
     * @param packet 要发送的协议元素
     * @throws XmppAuthException 如果发送失败
     * @throws IllegalArgumentException 如果 packet 不是 ExtensionElement 类型
     */
    private void sendStanza(Object packet) throws XmppAuthException {
        if (packet instanceof ExtensionElement element) {
            String xmlString = element.toXml();
            log.debug("Sending SASL stanza: {}", SecurityUtils.filterSensitiveXml(xmlString));

            int bufferSize = xmlString.length() * UTF8_MAX_BYTES_PER_CHAR;
            ByteBuf buf = ctx.alloc().buffer(bufferSize);
            try {
                buf.writeCharSequence(xmlString, StandardCharsets.UTF_8);
                final ByteBuf bufToWrite = buf;
                ctx.writeAndFlush(bufToWrite).addListener(future -> {
                    if (future.isSuccess()) {
                        log.info("SASL stanza sent successfully");
                    } else {
                        log.error("Failed to send SASL stanza", future.cause());
                        // 如果发送失败且 buf 还未被释放，确保释放
                        if (bufToWrite.refCnt() > 0) {
                            bufToWrite.release();
                        }
                    }
                });
            } catch (Exception e) {
                // 确保任何异常情况下都释放 ByteBuf
                if (buf.refCnt() > 0) {
                    buf.release();
                }
                throw new XmppAuthException("Failed to send SASL stanza", e);
            }
        } else {
            throw new IllegalArgumentException(
                    "Packet must implement ExtensionElement interface: " + packet.getClass().getName());
        }
    }
}
