package com.example.xmpp.exception;

import com.example.xmpp.protocol.model.sasl.SaslFailure;
import lombok.Getter;

/**
 * SASL 失败异常。
 *
 * <p>封装服务端返回的 SASL failure 元素，便于调用方按条件码进行处理。</p>
 *
 * @since 2026-03-11
 */
public class XmppSaslFailureException extends XmppAuthException {

    @Getter
    private final SaslFailure saslFailure;

    /**
     * 创建 SASL 失败异常。
     *
     * @param saslFailure 服务端返回的 SASL failure
     */
    public XmppSaslFailureException(SaslFailure saslFailure) {
        super(buildMessage(saslFailure));
        this.saslFailure = saslFailure;
    }

    private static String buildMessage(SaslFailure saslFailure) {
        if (saslFailure == null) {
            return "SASL authentication failed: no failure element received";
        }
        StringBuilder builder = new StringBuilder("SASL authentication failed");
        if (saslFailure.condition() != null && !saslFailure.condition().isBlank()) {
            builder.append(": ").append(saslFailure.condition());
        }
        return builder.toString();
    }
}
