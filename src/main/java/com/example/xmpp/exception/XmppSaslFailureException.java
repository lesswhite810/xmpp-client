package com.example.xmpp.exception;

import com.example.xmpp.protocol.model.sasl.SaslFailure;
import lombok.Getter;

import java.io.Serial;

/**
 * SASL 失败异常。
 *
 * <p>封装服务端返回的 SASL failure 元素，便于调用方按条件码进行处理。</p>
 *
 * @since 2026-03-11
 */
public class XmppSaslFailureException extends XmppAuthException {

    @Serial
    private static final long serialVersionUID = 1L;

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
            return "SASL authentication failed";
        }
        StringBuilder builder = new StringBuilder("SASL authentication failed");
        if (saslFailure.getCondition() != null && !saslFailure.getCondition().isBlank()) {
            builder.append(": ").append(saslFailure.getCondition());
        }
        if (saslFailure.getText() != null && !saslFailure.getText().isBlank()) {
            builder.append(" (").append(saslFailure.getText()).append(")");
        }
        return builder.toString();
    }
}