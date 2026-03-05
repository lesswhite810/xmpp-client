package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Stanza;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.*;
import com.example.xmpp.protocol.StanzaFilter;
import com.example.xmpp.protocol.AsyncStanzaCollector;
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

        AsyncStanzaCollector collector = new AsyncStanzaCollector(filter);
        connection.sendStanza(iq);
        log.debug("Sent admin command: id={}, to={}", iq.getId(), iq.getTo());

        return collector.getFuture()
                .orTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Admin command timed out or failed: {}", ex.getMessage());
                    }
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
     * 添加用户（带邮箱）。
     *
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱（可选）
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> addUser(String username, String password, String email) {
        AddUser request = new AddUser(username, password, email);
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id("add-" + System.currentTimeMillis())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
    }

    /**
     * 删除用户。
     *
     * @param username 用户名
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> deleteUser(String username) {
        DeleteUser request = new DeleteUser(username);
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id("delete-" + System.currentTimeMillis())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
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
     * 编辑用户信息（带邮箱）。
     *
     * @param username 用户名
     * @param newPassword 新密码
     * @param email 新邮箱
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> editUser(String username, String newPassword, String email) {
        EditUser request = new EditUser(username, newPassword, email);
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id("edit-" + System.currentTimeMillis())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
    }

    /**
     * 获取用户信息。
     *
     * @param username 用户名
     * @return 包含用户信息的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> getUser(String username) {
        GetUser request = new GetUser(username);
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("get-" + System.currentTimeMillis())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
    }

    /**
     * 列出所有用户。
     *
     * @return 包含用户列表的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> listUsers() {
        ListUsers request = new ListUsers();
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("list-" + System.currentTimeMillis())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
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
                .id("list-" + System.currentTimeMillis())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
    }

    /**
     * 获取在线用户列表。
     *
     * @return 包含在线用户列表的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> getOnlineUsers() {
        GetOnlineUsers request = new GetOnlineUsers();
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("online-" + System.currentTimeMillis())
                .to(serviceDomain)
                .childElement(request)
                .build();
        return sendAdminCommand(iq);
    }

    /**
     * 踢出用户。
     *
     * @param jid 用户 JID
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> kickUser(String jid) {
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id("kick-" + System.currentTimeMillis())
                .to(jid)
                .build();
        return connection.sendIqPacketAsync(iq);
    }
}
