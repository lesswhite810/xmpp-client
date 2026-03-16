package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.*;
import com.example.xmpp.util.StanzaIdGenerator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 管理员管理器。
 *
 * <p>提供 XMPP 服务管理功能，包括用户管理。使用此管理器需要管理员权限。</p>
 *
 */
@Slf4j
@Getter
public class AdminManager {

    private static final long DEFAULT_TIMEOUT_MS = 15000;

    private static final String ATTR_SESSION_ID = "sessionid";

    private static final String ATTR_STATUS = "status";

    private static final String STATUS_COMPLETED = "completed";

    private static final String JID_SEPARATOR = "@";

    private final XmppConnection connection;

    private final String serviceDomain;

    private final String adminUsername;

    private final long timeoutMs;

    /**
     * 创建 AdminManager。
     *
     * @param connection XMPP 连接
     * @param config 客户端配置，需要包含管理员账户的用户名
     * 
     */
    public AdminManager(XmppConnection connection, XmppClientConfig config) {
        this(connection, config.getUsername(), config.getXmppServiceDomain(), DEFAULT_TIMEOUT_MS);
    }

    /**
     * 创建 AdminManager（带自定义管理员用户名）。
     *
     * @param connection XMPP 连接
     * @param adminUsername 管理员用户名
     * @param serviceDomain XMPP 服务域名
     * 
     */
    public AdminManager(XmppConnection connection, String adminUsername, String serviceDomain) {
        this(connection, adminUsername, serviceDomain, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 创建 AdminManager（完整构造器）。
     *
     * @param connection XMPP 连接
     * @param adminUsername 管理员用户名
     * @param serviceDomain XMPP 服务域名
     * @param timeoutMs 命令超时时间（毫秒）
     * 
     */
    public AdminManager(XmppConnection connection, String adminUsername, String serviceDomain, long timeoutMs) {
        this.connection = connection;
        this.serviceDomain = serviceDomain;
        this.adminUsername = adminUsername;
        this.timeoutMs = timeoutMs;
    }

    /**
     * 发送管理命令并等待响应。
     *
     * @param iq 要发送的 IQ
     * @return 响应 CompletableFuture
     * 
     */
    private CompletableFuture<XmppStanza> sendAdminCommand(Iq iq) {
        log.debug("Sent admin command: id={}, to={}", iq.getId(), iq.getTo());
        return connection.sendIqPacketAsync(iq, timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 构建管理命令 IQ 请求。
     *
     * @param childElement 命令载荷
     * @return 发送到服务域名的管理命令 IQ
     * 
     */
    private Iq buildAdminIq(ExtensionElement childElement) {
        return new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId())
                .to(serviceDomain)
                .childElement(childElement)
                .build();
    }

    /**
     * 解析 IQ 响应中的会话 ID。
     *
     * @param response 响应节
     * @return 会话 ID；如果不存在则返回 {@link Optional#empty()}
     * 
     */
    private Optional<String> extractSessionId(XmppStanza response) {
        if (!(response instanceof Iq iq)) {
            log.debug("Response is not an IQ stanza: {}", response.getClass().getSimpleName());
            return Optional.empty();
        }

        ExtensionElement childElement = iq.getChildElement();
        if (childElement == null) {
            log.debug("IQ has no child element");
            return Optional.empty();
        }

        if (childElement instanceof GenericExtensionElement genericElement) {
            String sessionId = genericElement.getAttributeValue(ATTR_SESSION_ID);
            if (sessionId != null) {
                log.debug("Extracted sessionId: {}", sessionId);
                return Optional.of(sessionId);
            }
        }

        return extractSessionIdFromXml(iq.toXml());
    }

    /**
     * 从 XML 字符串中解析 sessionid 属性。
     *
     * @param xml XML 字符串
     * @return 会话 ID；如果不存在则返回 {@link Optional#empty()}
     * 
     */
    private Optional<String> extractSessionIdFromXml(String xml) {
        int sessionidIndex = xml.indexOf(ATTR_SESSION_ID + "=");
        if (sessionidIndex == -1) {
            log.debug("No sessionid found in response XML");
            return Optional.empty();
        }

        int valueStart = sessionidIndex + ATTR_SESSION_ID.length() + 1;
        if (valueStart >= xml.length()) {
            log.warn("Malformed sessionid attribute in XML");
            return Optional.empty();
        }

        char quote = xml.charAt(valueStart);
        if (quote != '"' && quote != '\'') {
            log.warn("Malformed sessionid attribute: missing quote");
            return Optional.empty();
        }

        int valueEnd = xml.indexOf(quote, valueStart + 1);
        if (valueEnd == -1) {
            log.warn("Malformed sessionid attribute: unclosed quote");
            return Optional.empty();
        }

        return Optional.of(xml.substring(valueStart + 1, valueEnd));
    }

    /**
     * 检查响应是否为错误类型。
     *
     * @param response 响应节
     * @return 是否为错误响应
     * 
     */
    private boolean isErrorResponse(XmppStanza response) {
        return response instanceof Iq iq && iq.getType() == Iq.Type.ERROR;
    }

    /**
     * 检查响应是否已直接完成（status=completed）。
     *
     * @param response 响应节
     * @return 是否已完成
     * 
     */
    private boolean isCompletedResponse(XmppStanza response) {
        if (!(response instanceof Iq iq) || iq.getType() != Iq.Type.RESULT) {
            return false;
        }
        ExtensionElement child = iq.getChildElement();
        if (child instanceof GenericExtensionElement genericElement) {
            return STATUS_COMPLETED.equals(genericElement.getAttributeValue(ATTR_STATUS));
        }
        return false;
    }

    /**
     * 创建命令异常。
     *
     * @param commandName 命令名称
     * @param message 异常消息
     * @param response 响应节
     * @return 命令异常
     * 
     */
    private AdminCommandException createCommandException(String commandName, String message, XmppStanza response) {
        return new AdminCommandException(commandName, message, response instanceof Iq iq ? iq : null);
    }

    /**
     * 发送单阶段管理命令。
     *
     * @param commandName 命令名称
     * @param request 命令载荷
     * @return 服务端响应
     * 
     */
    private CompletableFuture<XmppStanza> executeSinglePhaseCommand(String commandName, ExtensionElement request) {
        log.debug("Sending single-phase {} command", commandName);
        return sendAdminCommand(buildAdminIq(request));
    }

    /**
     * 执行两阶段命令的通用模板。
     *
     * @param commandName 命令名称
     * @param executeCmdFactory execute 阶段命令工厂
     * @param submitCmdFactory submit 阶段命令工厂
     * @param <T> 命令载荷类型
     * @return 服务端响应
     * 
     */
    private <T extends ExtensionElement> CompletableFuture<XmppStanza> executeTwoPhaseCommand(
            String commandName,
            Supplier<T> executeCmdFactory,
            Function<String, T> submitCmdFactory) {

        log.debug("Step 1: Sending {} execute command", commandName);
        T executeCmd = executeCmdFactory.get();
        Iq executeIq = buildAdminIq(executeCmd);

        return sendAdminCommand(executeIq)
                .thenCompose(executeResponse -> handleExecuteResponse(commandName, executeResponse, submitCmdFactory))
                .thenApply(response -> {
                    log.info("{} command completed successfully", commandName);
                    return response;
                });
    }

    /**
     * 执行账户作用域的两阶段管理命令。
     *
     * @param commandName 命令名称
     * @param username 用户名或完整 JID
     * @param executeCmdFactory execute 阶段命令工厂
     * @param submitCmdFactory submit 阶段命令工厂
     * @param <T> 命令载荷类型
     * @return 服务端响应
     * 
     */
    private <T extends ExtensionElement> CompletableFuture<XmppStanza> executeAccountCommand(
            String commandName,
            String username,
            Supplier<T> executeCmdFactory,
            BiFunction<String, String, T> submitCmdFactory) {

        String accountJid = buildAccountJid(username);
        return executeTwoPhaseCommand(
                commandName,
                executeCmdFactory,
                sessionId -> submitCmdFactory.apply(sessionId, accountJid)
        );
    }

    /**
     * 处理 execute 阶段的响应。
     *
     * @param commandName 命令名称
     * @param executeResponse execute 阶段响应
     * @param submitCmdFactory submit 阶段命令工厂
     * @param <T> 命令载荷类型
     * @return 服务端响应
     * 
     */
    private <T extends ExtensionElement> CompletableFuture<XmppStanza> handleExecuteResponse(
            String commandName,
            XmppStanza executeResponse,
            Function<String, T> submitCmdFactory) {

        if (isErrorResponse(executeResponse)) {
            return CompletableFuture.failedFuture(
                    createCommandException(commandName, "Server returned error response", executeResponse));
        }

        if (isCompletedResponse(executeResponse)) {
            return CompletableFuture.completedFuture(executeResponse);
        }

        Optional<String> sessionId = extractSessionId(executeResponse);
        if (sessionId.isEmpty()) {
            return CompletableFuture.failedFuture(
                    createCommandException(commandName, "No session ID in execute response", null));
        }

        log.debug("Step 2: Submitting {} form with session ID: {}", commandName, sessionId.orElseThrow());
        T submitCmd = submitCmdFactory.apply(sessionId.orElseThrow());
        Iq submitIq = buildAdminIq(submitCmd);

        return sendAdminCommand(submitIq)
                .thenCompose(submitResponse -> {
                    if (isErrorResponse(submitResponse)) {
                        return CompletableFuture.failedFuture(
                                createCommandException(commandName, "Server returned error in submit phase", submitResponse));
                    }
                    return CompletableFuture.completedFuture(submitResponse);
                });
    }

    /**
     * 添加用户。
     *
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱（可选）
     * @return 服务端响应
     * 
     */
    public CompletableFuture<XmppStanza> addUser(String username, String password, String email) {
        return executeAccountCommand(
                "add-user",
                username,
                AddUser::createExecuteCommand,
                (sessionId, accountJid) -> AddUser.createSubmitForm(sessionId, accountJid, password, email)
        );
    }

    /**
     * 添加用户。
     *
     * @param username 用户名
     * @param password 密码
     * @return 服务端响应
     * 
     */
    public CompletableFuture<XmppStanza> addUser(String username, String password) {
        return addUser(username, password, null);
    }

    /**
     * 删除用户。
     *
     * @param username 用户名
     * @return 服务端响应
     * 
     */
    public CompletableFuture<XmppStanza> deleteUser(String username) {
        return executeAccountCommand(
                "delete-user",
                username,
                DeleteUser::createExecuteCommand,
                DeleteUser::createSubmitForm
        );
    }

    /**
     * 编辑用户信息。
     *
     * @param username 用户名
     * @param newPassword 新密码
     * @return 服务端响应
     * 
     */
    public CompletableFuture<XmppStanza> editUser(String username, String newPassword) {
        return editUser(username, newPassword, null);
    }

    /**
     * 编辑用户信息。
     *
     * @param username 用户名
     * @param newPassword 新密码
     * @param email 邮箱（可选）
     * @return 服务端响应
     * 
     */
    public CompletableFuture<XmppStanza> editUser(String username, String newPassword, String email) {
        return executeTwoPhaseCommand(
                "edit-user",
                EditUser::createExecuteCommand,
                sessionId -> EditUser.createSubmitForm(sessionId, username, newPassword, email)
        );
    }

    /**
     * 修改用户密码。
     *
     * @param username 用户名
     * @param newPassword 新密码
     * @return 服务端响应
     * 
     */
    public CompletableFuture<XmppStanza> changePassword(String username, String newPassword) {
        return executeAccountCommand(
                "change-password",
                username,
                ChangeUserPassword::createExecuteCommand,
                (sessionId, accountJid) -> ChangeUserPassword.createSubmitForm(sessionId, accountJid, newPassword)
        );
    }

    /**
     * 构建完整的账户 JID。
     *
     * @param username 用户名
     * @return 完整 JID
     * 
     */
    private String buildAccountJid(String username) {
        int atIndex = username.indexOf(JID_SEPARATOR.charAt(0));
        return atIndex >= 0 ? username : username + JID_SEPARATOR + serviceDomain;
    }

    /**
     * 列出指定域下的用户。
     *
     * @param domains 域名列表
     * @return 服务端响应
     * 
     */

    /**
     * 踢出用户。
     *
     * @param jid 用户 JID
     * @return 服务端响应
     * 
     */
    public CompletableFuture<XmppStanza> kickUser(String jid) {
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId())
                .to(jid)
                .build();
        return connection.sendIqPacketAsync(iq);
    }
}
