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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * XEP-0133: Service Administration 管理员管理器。
 *
 * <p>提供 XMPP 服务管理功能，包括用户管理、在线用户管理等。
 * 使用此管理器需要管理员权限。</p>
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
 */
@Slf4j
@Getter
public class AdminManager {

    private static final long DEFAULT_TIMEOUT_MS = 15000;

    private final XmppConnection connection;
    private final String serviceDomain;
    private final String adminUsername;

    /**
     * 创建 AdminManager。
     *
     * @param connection XMPP 连接
     * @param config     客户端配置，需要包含管理员账户的用户名
     */
    public AdminManager(XmppConnection connection, XmppClientConfig config) {
        this.connection = connection;
        this.serviceDomain = config.getXmppServiceDomain();
        this.adminUsername = config.getUsername();
    }

    /**
     * 创建 AdminManager（带自定义管理员用户名）。
     *
     * @param connection    XMPP 连接
     * @param adminUsername 管理员用户名
     * @param serviceDomain XMPP 服务域名
     */
    public AdminManager(XmppConnection connection, String adminUsername, String serviceDomain) {
        this.connection = connection;
        this.serviceDomain = serviceDomain;
        this.adminUsername = adminUsername;
    }

    // ==================== 核心方法 ====================

    /**
     * 发送管理命令并等待响应（使用 ID + from 地址匹配，支持 Openfire）。
     *
     * <p>过滤器同时匹配：</p>
     * <ul>
     *   <li>IQ 类型为 RESULT 或 ERROR</li>
     *   <li>from 地址来自管理员或服务器（兼容 Openfire）</li>
     *   <li>IQ ID 与请求 ID 匹配（防止并发响应错乱）</li>
     * </ul>
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
                .orTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> connection.removeStanzaCollector(collector));
    }

    /**
     * 创建管理命令响应过滤器。
     *
     * <p>匹配条件：</p>
     * <ul>
     *   <li>IQ 类型为 RESULT 或 ERROR</li>
     *   <li>IQ ID 与请求 ID 匹配</li>
     *   <li>from 地址来自管理员 JID 或服务域名</li>
     * </ul>
     *
     * <p>注意：同时匹配 ID 和 from 地址，确保并发请求不会响应错乱。</p>
     *
     * @param requestId 请求的 IQ ID
     * @return StanzaFilter 实例
     */
    private StanzaFilter createAdminResponseFilter(String requestId) {
        return stanza -> {
            if (!(stanza instanceof Iq responseIq)) {
                return false;
            }
            // 必须匹配 IQ ID（防止并发响应错乱）
            if (!requestId.equals(responseIq.getId())) {
                return false;
            }
            // 匹配类型为 RESULT 或 ERROR 的响应
            if (responseIq.getType() != Iq.Type.RESULT && responseIq.getType() != Iq.Type.ERROR) {
                return false;
            }
            // 检查 from 地址是否来自管理员用户或服务器
            String from = responseIq.getFrom();
            if (from != null && from.startsWith(adminUsername + "@")) {
                log.debug("Matched admin response from: {} with id: {}", from, requestId);
                return true;
            }
            // 也接受来自服务器的响应
            if (from != null && from.equals(serviceDomain)) {
                log.debug("Matched server response from: {} with id: {}", from, requestId);
                return true;
            }
            return false;
        };
    }

    /**
     * 构建管理命令 IQ 请求。
     *
     * @param childElement 子元素
     * @return IQ 节
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
     * <p>从 GenericExtensionElement 或 command 扩展元素中提取 sessionid 属性。</p>
     *
     * @param response 响应节
     * @return 会话 ID，如果不存在返回 null
     */
    private String extractSessionId(XmppStanza response) {
        if (!(response instanceof Iq iq)) {
            log.warn("Response is not an IQ stanza: {}", response.getClass().getSimpleName());
            return null;
        }

        ExtensionElement childElement = iq.getChildElement();
        if (childElement == null) {
            log.warn("IQ has no child element");
            return null;
        }

        // 优先从 GenericExtensionElement 获取属性
        if (childElement instanceof GenericExtensionElement genericElement) {
            String sessionId = genericElement.getAttributeValue("sessionid");
            if (sessionId != null) {
                log.debug("Extracted sessionId from GenericExtensionElement: {}", sessionId);
                return sessionId;
            }
        }

        // 回退到字符串解析
        return extractSessionIdFromXml(iq.toXml());
    }

    /**
     * 从 XML 字符串中解析 sessionid 属性。
     */
    private String extractSessionIdFromXml(String xml) {
        int sessionidIndex = xml.indexOf("sessionid=");
        if (sessionidIndex == -1) {
            log.debug("No sessionid found in response XML");
            return null;
        }

        int start = sessionidIndex + 10; // "sessionid=".length() = 10
        if (start < xml.length() && (xml.charAt(start) == '"' || xml.charAt(start) == '\'')) {
            start++;
        }
        int end = xml.indexOf(xml.charAt(sessionidIndex + 10), start);
        if (end == -1) {
            log.warn("Could not find end quote for sessionid value");
            return null;
        }

        String sessionId = xml.substring(start, end);
        log.debug("Extracted sessionId from XML string: {}", sessionId);
        return sessionId;
    }

    // ==================== 响应处理辅助方法 ====================

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
            return "completed".equals(genericElement.getAttributeValue("status"));
        }
        return false;
    }

    // ==================== 通用两阶段命令模板 ====================

    /**
     * 执行两阶段命令的通用模板。
     *
     * <p>两阶段流程：</p>
     * <ol>
     *   <li>发送 execute 命令获取表单</li>
     *   <li>检查响应（错误、已完成、需要提交表单）</li>
     *   <li>如果需要，提交表单完成命令</li>
     * </ol>
     *
     * <p>异常处理（由调用者处理）：</p>
     * <ul>
     *   <li>{@link AdminCommandException} - 服务器返回错误响应</li>
     *   <li>{@link java.util.concurrent.TimeoutException} - 命令超时</li>
     *   <li>其他异常 - 网络/系统错误</li>
     * </ul>
     *
     * @param <T>               命令扩展元素类型
     * @param commandName       命令名称（用于日志和异常）
     * @param executeCmdFactory 创建 execute 命令的工厂
     * @param submitCmdFactory  创建 submit 命令的工厂（接收 sessionId）
     * @return 响应的 CompletableFuture，成功时返回 RESULT 类型的 IQ，失败时以异常完成
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
        // 不在这里处理异常，让调用者决定如何处理
    }

    /**
     * 处理 execute 阶段的响应。
     *
     * <p>错误响应会导致 Future 以 {@link AdminCommandException} 异常完成。</p>
     *
     * @return 响应的 CompletableFuture，失败时以 {@link AdminCommandException} 异常完成
     */
    private <T extends ExtensionElement> CompletableFuture<XmppStanza> handleExecuteResponse(
            String commandName,
            XmppStanza executeResponse,
            Function<String, T> submitCmdFactory) {

        // 检查错误响应 - 以异常完成 Future
        if (isErrorResponse(executeResponse)) {
            Iq errorIq = (Iq) executeResponse;
            return CompletableFuture.failedFuture(
                    new AdminCommandException(
                            commandName,
                            "Server returned error response",
                            errorIq
                    )
            );
        }

        // 检查是否直接完成
        if (isCompletedResponse(executeResponse)) {
            return CompletableFuture.completedFuture(executeResponse);
        }

        // 提取 sessionId
        String sessionId = extractSessionId(executeResponse);
        if (sessionId == null) {
            return CompletableFuture.failedFuture(
                    new AdminCommandException(
                            commandName,
                            "No session ID in execute response"
                    )
            );
        }

        // 第二步：提交表单
        log.debug("Step 2: Submitting {} form with session ID: {}", commandName, sessionId);
        T submitCmd = submitCmdFactory.apply(sessionId);
        Iq submitIq = buildAdminIq(submitCmd);

        return sendAdminCommand(submitIq)
                .thenCompose(submitResponse -> {
                    // 检查提交阶段的错误响应
                    if (isErrorResponse(submitResponse)) {
                        Iq errorIq = (Iq) submitResponse;
                        return CompletableFuture.failedFuture(
                                new AdminCommandException(
                                        commandName,
                                        "Server returned error response in submit phase",
                                        errorIq
                                )
                        );
                    }
                    return CompletableFuture.completedFuture(submitResponse);
                });
    }

    // ==================== 用户管理命令 ====================

    /**
     * 添加用户（分阶段流程）。
     *
     * <p>按照 XEP-0133 标准，先发送 execute 命令获取表单，
     * 然后提交填好的表单数据。</p>
     *
     * @param username 用户名
     * @param password 密码
     * @param email    邮箱（可选）
     * @return 执行结果的 CompletableFuture
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
     *
     * @param username 用户名
     * @param password 密码
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> addUser(String username, String password) {
        return addUser(username, password, null);
    }

    /**
     * 删除用户。
     *
     * @param username 用户名
     * @return 执行结果的 CompletableFuture
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
     *
     * @param username    用户名
     * @param newPassword 新密码
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> editUser(String username, String newPassword) {
        return editUser(username, newPassword, null);
    }

    /**
     * 编辑用户信息。
     *
     * @param username    用户名
     * @param newPassword 新密码
     * @param email       新邮箱
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> editUser(String username, String newPassword, String email) {
        return executeTwoPhaseCommand(
                "edit-user",
                EditUser::createExecuteCommand,
                sessionId -> EditUser.createSubmitForm(sessionId, username, newPassword, email)
        );
    }

    /**
     * 修改用户密码（使用 XEP-0133 Service Administration）。
     *
     * <p>按照 XEP-0133 标准，使用两阶段流程：
     * 1. 发送 execute 命令获取表单
     * 2. 提交带有新密码的表单</p>
     *
     * @param username    用户名
     * @param newPassword 新密码
     * @return 执行结果的 CompletableFuture
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
     * 获取用户信息（两阶段流程）。
     *
     * <p>按照 XEP-0133 标准，先发送 execute 命令获取表单，
     * 然后提交带有用户 JID 的表单。</p>
     *
     * @param username 用户名
     * @return 包含用户信息的 CompletableFuture
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
     *
     * @param username 用户名（可能已包含 @domain）
     * @return 完整的 JID (user@domain)
     */
    private String buildAccountJid(String username) {
        return username.contains("@") ? username : username + "@" + serviceDomain;
    }

    // ==================== 列表查询命令 ====================

    /**
     * 列出所有用户（两阶段流程）。
     *
     * @return 包含用户列表的 CompletableFuture
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
     *
     * @param domains 域名列表
     * @return 包含用户列表的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> listUsers(List<String> domains) {
        ListUsers request = new ListUsers(domains);
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id(StanzaIdGenerator.newId())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
    }

    /**
     * 获取在线用户列表（两阶段流程）。
     *
     * @return 包含在线用户列表的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> getOnlineUsers() {
        return executeTwoPhaseCommand(
                "get-online-users",
                GetOnlineUsers::createExecuteCommand,
                GetOnlineUsers::createSubmitForm
        );
    }

    // ==================== 其他管理命令 ====================

    /**
     * 踢出用户。
     *
     * @param jid 用户 JID
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> kickUser(String jid) {
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId())
                .to(jid)
                .build();
        return connection.sendIqPacketAsync(iq);
    }
}
