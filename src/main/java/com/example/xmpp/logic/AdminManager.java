package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.config.XmppClientConfig;
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
     * @param config 客户端配置，需要包含管理员账户的用户名
     */
    public AdminManager(XmppConnection connection, XmppClientConfig config) {
        this.connection = connection;
        this.serviceDomain = config.getXmppServiceDomain();
        this.adminUsername = config.getUsername();
    }

    /**
     * 创建 AdminManager（带自定义管理员用户名）。
     *
     * @param connection XMPP 连接
     * @param adminUsername 管理员用户名
     * @param serviceDomain XMPP 服务域名
     */
    public AdminManager(XmppConnection connection, String adminUsername, String serviceDomain) {
        this.connection = connection;
        this.serviceDomain = serviceDomain;
        this.adminUsername = adminUsername;
    }

    /**
     * 发送管理命令并等待响应（使用 from 地址匹配，支持 Openfire）。
     *
     * @param iq 要发送的 IQ
     * @return 响应 CompletableFuture
     */
    private CompletableFuture<XmppStanza> sendAdminCommand(Iq iq) {
        // 创建一个过滤器，匹配从 admin JID 返回的 IQ 响应
        // 这样可以兼容 Openfire 服务器（它会生成新的 IQ ID）
        StanzaFilter filter = stanza -> {
            if (!(stanza instanceof Iq responseIq)) {
                return false;
            }
            // 匹配类型为 RESULT 或 ERROR 的响应
            if (responseIq.getType() != Iq.Type.RESULT && responseIq.getType() != Iq.Type.ERROR) {
                return false;
            }
            // 检查 from 地址是否来自管理员用户
            String from = responseIq.getFrom();
            if (from != null && from.startsWith(adminUsername + "@")) {
                log.debug("Matched admin response from: {}", from);
                return true;
            }
            // 也接受来自服务器的响应
            return from != null && from.equals(serviceDomain);
        };

        // 使用连接的收集器注册机制，确保响应能被正确分发
        AsyncStanzaCollector collector = connection.createStanzaCollector(filter);
        connection.sendStanza(iq);
        log.debug("Sent admin command: id={}, to={}", iq.getId(), iq.getTo());

        return collector.getFuture()
                .orTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> {
                    connection.removeStanzaCollector(collector);
                    if (ex != null) {
                        log.warn("Admin command timed out or failed: {}", ex.getMessage());
                    }
                });
    }

    /**
     * 解析IQ响应中的会话ID。
     *
     * <p>从 GenericExtensionElement 或 command 扩展元素中提取 sessionid 属性。</p>
     *
     * @param response 响应节
     * @return 会话ID，如果不存在返回 null
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
        String xml = iq.toXml();
        int sessionidIndex = xml.indexOf("sessionid=");
        if (sessionidIndex == -1) {
            log.warn("No sessionid found in response XML");
            return null;
        }

        int start = sessionidIndex + 10; // "sessionid=".length() = 10
        // 跳过引号
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

    /**
     * 检查响应是否包含命令元素。
     */
    private boolean hasCommandElement(XmppStanza response) {
        if (!(response instanceof Iq iq)) {
            return false;
        }
        // 检查子元素是否为 command 元素
        ExtensionElement child = iq.getChildElement();
        return child != null && 
               "command".equals(child.getElementName()) &&
               "http://jabber.org/protocol/commands".equals(child.getNamespace());
    }

    /**
     * 添加用户（分阶段流程）。
     *
     * <p>按照XEP-0133标准，先发送execute命令获取表单，
     * 然后提交填好的表单数据。</p>
     *
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱（可选）
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> addUser(String username, String password, String email) {
        // 构建完整的 JID (user@domain)
        String accountJid = username.contains("@") ? username : username + "@" + serviceDomain;

        // 第一步：发送execute命令获取表单
        log.debug("Step 1: Sending execute command to get form");
        AddUser executeCmd = AddUser.createExecuteCommand();
        Iq executeIq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId("add-execute"))
                .to(serviceDomain)
                .childElement(executeCmd)
                .build();

        return sendAdminCommand(executeIq)
                .thenCompose(executeResponse -> {
                    // 解析响应获取sessionid
                    String sessionId = extractSessionId(executeResponse);

                    if (sessionId == null) {
                        log.error("No session ID in execute response");
                        // 返回错误响应
                        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                                .id("add-error-no-session")
                                .build();
                        return CompletableFuture.completedFuture(errorIq);
                    }
                    // 第二步：提交填好的表单（使用完整JID）
                    log.debug("Step 2: Submitting form with session ID: {}, accountJid: {}", sessionId, accountJid);
                    AddUser submitCmd = AddUser.createSubmitForm(sessionId, accountJid, password, email);
                    Iq submitIq = new Iq.Builder(Iq.Type.SET)
                            .id(StanzaIdGenerator.newId("add-submit"))
                            .to(serviceDomain)
                            .childElement(submitCmd)
                            .build();

                    return sendAdminCommand(submitIq);
                })
                .thenApply(submitResponse -> {
                    // 处理最终响应
                    if (submitResponse instanceof Iq submitIq) {
                        if (submitIq.getType() == Iq.Type.RESULT) {
                            log.info("✅ Successfully added user: {}", accountJid);
                        } else {
                            log.error("❌ Failed to add user: {}", accountJid);
                        }
                    }
                    return submitResponse;
                })
                .exceptionally(ex -> {
                    log.error("Add user failed: {}", ex.getMessage());
                    // 返回一个表示错误的 IQ
                    Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                            .id("add-error-exception")
                            .build();
                    return errorIq;
                });
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
        // 构建完整的 JID (user@domain)
        String accountJid = username.contains("@") ? username : username + "@" + serviceDomain;

        // 第一步：发送execute命令获取表单
        log.debug("Step 1: Sending delete execute command to get form");
        DeleteUser executeCmd = DeleteUser.createExecuteCommand();
        Iq executeIq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId("delete-execute"))
                .to(serviceDomain)
                .childElement(executeCmd)
                .build();

        return sendAdminCommand(executeIq)
                .thenCompose(executeResponse -> {
                    // 解析响应获取sessionid
                    String sessionId = extractSessionId(executeResponse);

                    if (sessionId == null) {
                        log.error("No session ID in delete execute response");
                        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                                .id("delete-error-no-session")
                                .build();
                        return CompletableFuture.completedFuture(errorIq);
                    }

                    // 第二步：提交填好的表单（使用完整JID）
                    log.debug("Step 2: Submitting delete form with session ID: {}, accountJid: {}", sessionId, accountJid);
                    DeleteUser submitCmd = DeleteUser.createSubmitForm(sessionId, accountJid);
                    Iq submitIq = new Iq.Builder(Iq.Type.SET)
                            .id(StanzaIdGenerator.newId("delete-submit"))
                            .to(serviceDomain)
                            .childElement(submitCmd)
                            .build();

                    return sendAdminCommand(submitIq);
                })
                .thenApply(submitResponse -> {
                    if (submitResponse instanceof Iq submitIq) {
                        if (submitIq.getType() == Iq.Type.RESULT) {
                            log.info("✅ Successfully deleted user: {}", accountJid);
                        } else {
                            log.error("❌ Failed to delete user: {}", accountJid);
                        }
                    }
                    return submitResponse;
                })
                .exceptionally(ex -> {
                    log.error("Delete user failed: {}", ex.getMessage());
                    Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                            .id("delete-error-exception")
                            .build();
                    return errorIq;
                });
    }

    /**
     * 编辑用户信息。
     *
     * @param username 用户名
     * @param newPassword 新密码
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> editUser(String username, String newPassword) {
        return editUser(username, newPassword, null);
    }

    /**
     * 编辑用户信息。
     *
     * @param username 用户名
     * @param newPassword 新密码
     * @param email 新邮箱
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> editUser(String username, String newPassword, String email) {
        // 第一步：发送execute命令获取表单
        log.debug("Step 1: Sending edit execute command to get form");
        EditUser executeCmd = EditUser.createExecuteCommand();
        Iq executeIq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId("edit-execute"))
                .to(serviceDomain)
                .childElement(executeCmd)
                .build();
        
        return sendAdminCommand(executeIq)
                .thenCompose(executeResponse -> {
                    // 解析响应获取sessionid
                    String sessionId = extractSessionId(executeResponse);
                    
                    if (sessionId == null) {
                        log.error("No session ID in edit execute response");
                        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                                .id("edit-error-no-session")
                                .build();
                        return CompletableFuture.completedFuture(errorIq);
                    }
                    
                    // 第二步：提交填好的表单
                    log.debug("Step 2: Submitting edit form with session ID: {}", sessionId);
                    EditUser submitCmd = EditUser.createSubmitForm(sessionId, username, newPassword, email);
                    Iq submitIq = new Iq.Builder(Iq.Type.SET)
                            .id(StanzaIdGenerator.newId("edit-submit"))
                            .to(serviceDomain)
                            .childElement(submitCmd)
                            .build();
                    
                    return sendAdminCommand(submitIq);
                })
                .thenApply(submitResponse -> {
                    if (submitResponse instanceof Iq submitIq) {
                        if (submitIq.getType() == Iq.Type.RESULT) {
                            log.info("✅ Successfully edited user: {}", username);
                        } else {
                            log.error("❌ Failed to edit user: {}", username);
                        }
                    }
                    return submitResponse;
                })
                .exceptionally(ex -> {
                    log.error("Edit user failed: {}", ex.getMessage());
                    Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                            .id("edit-error-exception")
                            .build();
                    return errorIq;
                });
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
        String accountJid = username.contains("@") ? username : username + "@" + serviceDomain;

        // 第一步：发送 execute 命令
        log.debug("Step 1: Sending get-user execute command");
        GetUser executeCmd = GetUser.createExecuteCommand();
        Iq executeIq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId("getuser-execute"))
                .to(serviceDomain)
                .childElement(executeCmd)
                .build();

        return sendAdminCommand(executeIq)
                .thenCompose(executeResponse -> {
                    String sessionId = extractSessionId(executeResponse);
                    if (sessionId == null) {
                        log.error("No session ID in get-user execute response");
                        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                                .id("getuser-error-no-session")
                                .build();
                        return CompletableFuture.completedFuture(errorIq);
                    }

                    // 第二步：提交表单
                    log.debug("Step 2: Submitting get-user form with session ID: {}, accountJid: {}", sessionId, accountJid);
                    GetUser submitCmd = GetUser.createSubmitForm(sessionId, accountJid);
                    Iq submitIq = new Iq.Builder(Iq.Type.SET)
                            .id(StanzaIdGenerator.newId("getuser-submit"))
                            .to(serviceDomain)
                            .childElement(submitCmd)
                            .build();

                    return sendAdminCommand(submitIq);
                })
                .exceptionally(ex -> {
                    log.error("Get user failed: {}", ex.getMessage());
                    Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                            .id("getuser-error-exception")
                            .build();
                    return errorIq;
                });
    }

    /**
     * 列出所有用户（两阶段流程）。
     *
     * @return 包含用户列表的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> listUsers() {
        // 第一步：发送 execute 命令
        log.debug("Step 1: Sending list-users execute command");
        ListUsers executeCmd = ListUsers.createExecuteCommand();
        Iq executeIq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId("list-execute"))
                .to(serviceDomain)
                .childElement(executeCmd)
                .build();

        return sendAdminCommand(executeIq)
                .thenCompose(executeResponse -> {
                    // 检查响应是否包含用户列表数据（可能直接返回结果）
                    if (executeResponse instanceof Iq iq && iq.getType() == Iq.Type.RESULT) {
                        ExtensionElement child = iq.getChildElement();
                        if (child instanceof GenericExtensionElement genericElement) {
                            // 检查是否包含 note 或其他完成标志
                            String status = genericElement.getAttributeValue("status");
                            if ("completed".equals(status)) {
                                log.debug("List users completed in first step");
                                return CompletableFuture.completedFuture(executeResponse);
                            }
                        }
                    }

                    String sessionId = extractSessionId(executeResponse);
                    if (sessionId == null) {
                        log.warn("No session ID in list-users execute response, returning raw response");
                        return CompletableFuture.completedFuture(executeResponse);
                    }

                    // 第二步：提交表单
                    log.debug("Step 2: Submitting list-users form with session ID: {}", sessionId);
                    ListUsers submitCmd = ListUsers.createSubmitForm(sessionId, null);
                    Iq submitIq = new Iq.Builder(Iq.Type.SET)
                            .id(StanzaIdGenerator.newId("list-submit"))
                            .to(serviceDomain)
                            .childElement(submitCmd)
                            .build();

                    return sendAdminCommand(submitIq);
                })
                .exceptionally(ex -> {
                    log.error("List users failed: {}", ex.getMessage());
                    Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                            .id("list-error-exception")
                            .build();
                    return errorIq;
                });
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
                .id(StanzaIdGenerator.newId("list"))
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
        // 第一步：发送 execute 命令
        log.debug("Step 1: Sending get-online-users execute command");
        GetOnlineUsers executeCmd = GetOnlineUsers.createExecuteCommand();
        Iq executeIq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId("online-execute"))
                .to(serviceDomain)
                .childElement(executeCmd)
                .build();

        return sendAdminCommand(executeIq)
                .thenCompose(executeResponse -> {
                    String sessionId = extractSessionId(executeResponse);
                    if (sessionId == null) {
                        log.error("No session ID in get-online-users execute response");
                        Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                                .id("online-error-no-session")
                                .build();
                        return CompletableFuture.completedFuture(errorIq);
                    }

                    // 第二步：提交表单（如果需要）
                    log.debug("Step 2: Submitting get-online-users form with session ID: {}", sessionId);
                    GetOnlineUsers submitCmd = GetOnlineUsers.createSubmitForm(sessionId);
                    Iq submitIq = new Iq.Builder(Iq.Type.SET)
                            .id(StanzaIdGenerator.newId("online-submit"))
                            .to(serviceDomain)
                            .childElement(submitCmd)
                            .build();

                    return sendAdminCommand(submitIq);
                })
                .exceptionally(ex -> {
                    log.error("Get online users failed: {}", ex.getMessage());
                    Iq errorIq = new Iq.Builder(Iq.Type.ERROR)
                            .id("online-error-exception")
                            .build();
                    return errorIq;
                });
    }

    /**
     * 踢出用户。
     *
     * @param jid 用户 JID
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> kickUser(String jid) {
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id(StanzaIdGenerator.newId("kick"))
                .to(jid)
                .build();
        return connection.sendIqPacketAsync(iq);
    }
}