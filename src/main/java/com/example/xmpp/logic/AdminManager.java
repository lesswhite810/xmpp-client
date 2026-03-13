package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.*;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.AsyncStanzaCollector;
import com.example.xmpp.util.StanzaIdGenerator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * XEP-0133: Service Administration 管理员管理器。
 *
 * <p>提供 XMPP 服务管理功能，包括用户管理、在线用户管理等操作。使用此管理器需要管理员权限。</p>
 *
 * <p>管理员命令通过 Ad-Hoc Commands (XEP-0050) 发送到服务器。</p>
 *
 * <p>注意：此实现针对 Openfire 服务器进行了优化，使用 from 地址匹配响应。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * XmppClientConfig config = XmppClientConfig.builder()
 *     .xmppServiceDomain("example.com")
 *     .username("admin")  // 管理员账户
 *     .password("password".toCharArray())
 *     .build();
 *
 * XmppTcpConnection connection = new XmppTcpConnection(config);
 * connection.connect();
 *
 * AdminManager adminManager = new AdminManager(connection, config);
 * adminManager.addUser("newuser", "password");
 * }</pre>
 *
 * @since 2026-02-09
 */
@Slf4j
@Getter
public class AdminManager {

    /**
     * 默认命令超时时间，单位为毫秒。
     */
    private static final long DEFAULT_TIMEOUT_MS = 15000;

    /**
     * XEP-0050 命令属性名。
     */
    private static final String ATTR_SESSION_ID = "sessionid";

    /**
     * 命令状态属性名。
     */
    private static final String ATTR_STATUS = "status";

    /**
     * 命令已完成状态值。
     */
    private static final String STATUS_COMPLETED = "completed";

    /**
     * JID 分隔符。
     */
    private static final String JID_SEPARATOR = "@";

    /**
     * 关联的 XMPP 连接。
     */
    private final XmppConnection connection;

    /**
     * 管理命令发送的目标服务域名。
     */
    private final String serviceDomain;

    /**
     * 管理员用户名。
     */
    private final String adminUsername;

    /**
     * 预计算的管理员 JID 前缀。
     *
     * <p>用于快速匹配来自管理员账户的响应地址，避免重复拼接字符串。</p>
     */
    private final String adminJidPrefix;

    /**
     * 命令超时时间，单位为毫秒。
     */
    private final long timeoutMs;

    /**
     * 创建 AdminManager。
     *
     * @param connection XMPP 连接
     * @param config     客户端配置，需要包含管理员账户的用户名
     */
    public AdminManager(XmppConnection connection, XmppClientConfig config) {
        this(connection, config.getUsername(), config.getXmppServiceDomain(), DEFAULT_TIMEOUT_MS);
    }

    /**
     * 创建 AdminManager（带自定义管理员用户名）。
     *
     * @param connection    XMPP 连接
     * @param adminUsername 管理员用户名
     * @param serviceDomain XMPP 服务域名
     */
    public AdminManager(XmppConnection connection, String adminUsername, String serviceDomain) {
        this(connection, adminUsername, serviceDomain, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 创建 AdminManager（完整构造器）。
     *
     * @param connection    XMPP 连接
     * @param adminUsername 管理员用户名
     * @param serviceDomain XMPP 服务域名
     * @param timeoutMs     命令超时时间（毫秒）
     */
    public AdminManager(XmppConnection connection, String adminUsername, String serviceDomain, long timeoutMs) {
        this.connection = connection;
        this.serviceDomain = serviceDomain;
        this.adminUsername = adminUsername;
        this.adminJidPrefix = adminUsername + JID_SEPARATOR;
        this.timeoutMs = timeoutMs;
    }

    /**
     * 发送管理命令并等待响应（使用 ID + from 地址匹配，支持 Openfire）。
     *
     * @param iq 要发送的 IQ
     * @return 响应 CompletableFuture
     */
    private CompletableFuture<XmppStanza> sendAdminCommand(Iq iq) {
        final String requestId = iq.getId();
        StanzaFilter filter = createAdminResponseFilter(requestId);

        AsyncStanzaCollector collector = connection.createStanzaCollector(filter);
        connection.sendStanza(iq);
        log.debug("Sent admin command: id={}, to={}", requestId, iq.getTo());

        return collector.getFuture()
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> connection.removeStanzaCollector(collector));
    }

    /**
     * 创建管理命令响应过滤器。
     *
     * <p>匹配条件：IQ 类型为 RESULT/ERROR + ID 匹配 + from 地址来自管理员或服务器。</p>
     *
     * @param requestId 请求的 IQ ID
     * @return StanzaFilter 实例
     */
    private StanzaFilter createAdminResponseFilter(String requestId) {
        return stanza -> {
            if (!(stanza instanceof Iq responseIq)) {
                return false;
            }
            if (!requestId.equals(responseIq.getId())) {
                return false;
            }
            Iq.Type type = responseIq.getType();
            if (type != Iq.Type.RESULT && type != Iq.Type.ERROR) {
                return false;
            }

            String from = responseIq.getFrom();
            if (from == null) {
                return false;
            }
            return from.startsWith(adminJidPrefix) || from.equals(serviceDomain);
        };
    }

    /**
     * 构建管理命令 IQ 请求。
     *
     * @param childElement 命令载荷
     * @return 发送到服务域名的管理命令 IQ
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
     */
    private Optional<String> extractSessionIdFromXml(String xml) {
        int sessionidIndex = xml.indexOf(ATTR_SESSION_ID + "=");
        if (sessionidIndex == -1) {
            log.debug("No sessionid found in response XML");
            return Optional.empty();
        }

        int valueStart = sessionidIndex + ATTR_SESSION_ID.length() + 1; // "sessionid=".length()
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
     */
    private boolean isErrorResponse(XmppStanza response) {
        return response instanceof Iq iq && iq.getType() == Iq.Type.ERROR;
    }

    /**
     * 检查响应是否已直接完成（status=completed）。
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
     */
    private AdminCommandException createCommandException(String commandName, String message, XmppStanza response) {
        return new AdminCommandException(commandName, message, response instanceof Iq iq ? iq : null);
    }

    /**
     * 执行两阶段命令的通用模板。
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
     * 处理 execute 阶段的响应。
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
     */
    public CompletableFuture<XmppStanza> addUser(String username, String password, String email) {
        String accountJid = buildAccountJid(username);

        return executeTwoPhaseCommand(
                "add-user",
                AddUser::createExecuteCommand,
                sessionId -> AddUser.createSubmitForm(sessionId, accountJid, password, email)
        );
    }

    /**
     * 添加用户。
     */
    public CompletableFuture<XmppStanza> addUser(String username, String password) {
        return addUser(username, password, null);
    }

    /**
     * 删除用户。
     */
    public CompletableFuture<XmppStanza> deleteUser(String username) {
        String accountJid = buildAccountJid(username);

        return executeTwoPhaseCommand(
                "delete-user",
                DeleteUser::createExecuteCommand,
                sessionId -> DeleteUser.createSubmitForm(sessionId, accountJid)
        );
    }

    /**
     * 编辑用户信息。
     */
    public CompletableFuture<XmppStanza> editUser(String username, String newPassword) {
        return editUser(username, newPassword, null);
    }

    /**
     * 编辑用户信息。
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
     */
    public CompletableFuture<XmppStanza> changePassword(String username, String newPassword) {
        String accountJid = buildAccountJid(username);

        return executeTwoPhaseCommand(
                "change-password",
                ChangeUserPassword::createExecuteCommand,
                sessionId -> ChangeUserPassword.createSubmitForm(sessionId, accountJid, newPassword)
        );
    }

    /**
     * 获取用户信息。
     */
    public CompletableFuture<XmppStanza> getUser(String username) {
        String accountJid = buildAccountJid(username);

        return executeTwoPhaseCommand(
                "get-user",
                GetUser::createExecuteCommand,
                sessionId -> GetUser.createSubmitForm(sessionId, accountJid)
        );
    }

    /**
     * 构建完整的账户 JID。
     */
    private String buildAccountJid(String username) {
        int atIndex = username.indexOf(JID_SEPARATOR.charAt(0));
        return atIndex >= 0 ? username : username + JID_SEPARATOR + serviceDomain;
    }

    /**
     * 列出所有用户。
     */
    public CompletableFuture<XmppStanza> listUsers() {
        return executeTwoPhaseCommand(
                "list-users",
                ListUsers::createExecuteCommand,
                sessionId -> ListUsers.createSubmitForm(sessionId, null)
        );
    }

    /**
     * 列出指定域下的用户。
     */
    public CompletableFuture<XmppStanza> listUsers(List<String> domains) {
        ListUsers request = new ListUsers(domains);
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
    }

    /**
     * 获取在线用户列表。
     */
    public CompletableFuture<XmppStanza> getOnlineUsers() {
        return executeTwoPhaseCommand(
                "get-online-users",
                GetOnlineUsers::createExecuteCommand,
                GetOnlineUsers::createSubmitForm
        );
    }

    /**
     * 踢出用户。
     */
    public CompletableFuture<XmppStanza> kickUser(String jid) {
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId())
                .to(jid)
                .build();
        return connection.sendIqPacketAsync(iq);
    }
}
