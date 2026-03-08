package com.example.xmpp.exception;

import com.example.xmpp.protocol.model.Iq;
import lombok.Getter;

import java.io.Serial;

/**
 * 管理命令执行异常。
 *
 * <p>当 XEP-0133 管理命令执行失败时抛出此异常。包含服务器返回的错误 IQ 响应，
 * 调用者可以通过 {@link #getErrorResponse()} 获取详细错误信息。</p>
 *
 * <h3>异常类型</h3>
 * <ul>
 *   <li>服务器返回 IQ ERROR 响应（如权限不足、命令不支持等）</li>
 *   <li>命令执行过程中缺少必要数据（如无 session ID）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * adminManager.addUser("user", "password")
 *     .exceptionally(ex -> {
 *         if (ex instanceof AdminCommandException ace) {
 *             if (ace.hasErrorResponse()) {
 *                 Iq error = ace.getErrorResponse();
 *                 // 处理服务器错误
 *             }
 *         }
 *         return null;
 *     });
 * }</pre>
 *
 * @since 2026-03-09
 */
public class AdminCommandException extends XmppException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务器返回的错误 IQ 响应，可能为 null（如缺少 session ID 场景） */
    @Getter
    private final Iq errorResponse;

    /** 命令名称 */
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
}
