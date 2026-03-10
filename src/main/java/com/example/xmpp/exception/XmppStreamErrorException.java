package com.example.xmpp.exception;

import com.example.xmpp.protocol.model.stream.StreamError;
import lombok.Getter;

import java.io.Serial;

/**
 * XMPP stream error 异常。
 *
 * <p>封装服务端返回的 stream:error 元素，表示当前流已失效。</p>
 *
 * @since 2026-03-11
 */
public class XmppStreamErrorException extends XmppProtocolException {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final StreamError streamError;

    /**
     * 创建 stream error 异常。
     *
     * @param streamError 服务端返回的流错误
     */
    public XmppStreamErrorException(StreamError streamError) {
        super(buildMessage(streamError));
        this.streamError = streamError;
    }

    private static String buildMessage(StreamError streamError) {
        if (streamError == null) {
            return "Received stream error";
        }
        StringBuilder builder = new StringBuilder("Received stream error");
        if (streamError.getCondition() != null) {
            builder.append(": ").append(streamError.getCondition().getElementName());
        }
        if (streamError.getText() != null && !streamError.getText().isBlank()) {
            builder.append(" (").append(streamError.getText()).append(")");
        }
        return builder.toString();
    }
}