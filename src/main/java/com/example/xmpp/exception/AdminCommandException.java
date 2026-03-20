package com.example.xmpp.exception;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import lombok.Getter;

/**
 * 管理命令执行异常。
 *
 * @since 2026-03-09
 */
public class AdminCommandException extends XmppException {

    /**
     * 服务器返回的错误 IQ 响应，可能为 null（如缺少 session ID 场景）
     */
    @Getter
    private final Iq errorResponse;

    /**
     * 命令名称
     */
    @Getter
    private final String commandName;

    /**
     * 创建管理命令异常。
     *
     * @param commandName 命令名称
     * @param message 错误消息
     * @param errorResponse 服务器返回的错误 IQ 响应（可为 null）
     */
    public AdminCommandException(String commandName, String message, Iq errorResponse) {
        super(message);
        this.commandName = commandName;
        this.errorResponse = errorResponse;
    }

    /**
     * 创建管理命令异常（带原因）。
     *
     * @param commandName 命令名称
     * @param message 错误消息
     * @param cause 原因异常
     */
    public AdminCommandException(String commandName, String message, Throwable cause) {
        super(message, cause);
        this.commandName = commandName;
        this.errorResponse = null;
    }

    /**
     * 创建管理命令异常（简化版本）。
     *
     * @param commandName 命令名称
     * @param message 错误消息
     */
    public AdminCommandException(String commandName, String message) {
        this(commandName, message, (Iq) null);
    }

    /**
     * 检查是否有服务器错误响应。
     *
     * @return 如果有 IQ ERROR 响应返回 true
     */
    public boolean hasErrorResponse() {
        return errorResponse != null;
    }

    /**
     * 获取错误条件（如果可用）。
     *
     * @return 错误条件，不存在则返回 null
     */
    public XmppError.Condition getErrorCondition() {
        if (errorResponse == null) {
            return null;
        }
        XmppError error = errorResponse.getError();
        return error != null ? error.getCondition() : null;
    }
}
