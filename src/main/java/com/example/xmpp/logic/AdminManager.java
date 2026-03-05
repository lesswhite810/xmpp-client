package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.extension.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * XEP-0133: Service Administration 管理员管理器。
 *
 * <p>提供 XMPP 服务管理功能，包括用户管理、在线用户管理等。
 * 使用此管理器需要管理员权限。</p>
 *
 * <p>管理员命令通过 Ad-Hoc Commands (XEP-0050) 发送到服务管理员账户。</p>
 */
@Slf4j
public class AdminManager {

    private final XmppConnection connection;
    private final String serviceDomain;

    public AdminManager(XmppConnection connection, XmppClientConfig config) {
        this.connection = connection;
        this.serviceDomain = config.getXmppServiceDomain();
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
                .id("admin-add-" + System.currentTimeMillis())
                .to("admin@" + serviceDomain)
                .childElement(request)
                .build();
        log.debug("Sending add-user IQ: {}", iq.toXml());
        return connection.sendIqPacketAsync(iq);
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
                .id("admin-delete-" + System.currentTimeMillis())
                .to("admin@" + serviceDomain)
                .childElement(request)
                .build();
        log.debug("Sending delete-user IQ: {}", iq.toXml());
        return connection.sendIqPacketAsync(iq);
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
                .id("admin-edit-" + System.currentTimeMillis())
                .to("admin@" + serviceDomain)
                .childElement(request)
                .build();
        log.debug("Sending edit-user IQ: {}", iq.toXml());
        return connection.sendIqPacketAsync(iq);
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
                .id("admin-get-" + System.currentTimeMillis())
                .to("admin@" + serviceDomain)
                .childElement(request)
                .build();
        log.debug("Sending get-user IQ: {}", iq.toXml());
        return connection.sendIqPacketAsync(iq);
    }

    /**
     * 列出所有用户。
     *
     * @return 包含用户列表的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> listUsers() {
        ListUsers request = new ListUsers();
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("admin-list-" + System.currentTimeMillis())
                .to("admin@" + serviceDomain)
                .childElement(request)
                .build();
        log.debug("Sending list-users IQ: {}", iq.toXml());
        return connection.sendIqPacketAsync(iq);
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
                .id("admin-list-" + System.currentTimeMillis())
                .to("admin@" + serviceDomain)
                .childElement(request)
                .build();
        log.debug("Sending list-users IQ: {}", iq.toXml());
        return connection.sendIqPacketAsync(iq);
    }

    /**
     * 获取在线用户列表。
     *
     * @return 包含在线用户列表的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> getOnlineUsers() {
        GetOnlineUsers request = new GetOnlineUsers();
        Iq iq = new Iq.Builder(Iq.Type.GET)
                .id("admin-online-" + System.currentTimeMillis())
                .to("admin@" + serviceDomain)
                .childElement(request)
                .build();
        log.debug("Sending get-online-users IQ: {}", iq.toXml());
        return connection.sendIqPacketAsync(iq);
    }

    /**
     * 踢出用户。
     *
     * @param jid 用户 JID
     * @return 执行结果的 CompletableFuture
     */
    public CompletableFuture<XmppStanza> kickUser(String jid) {
        Iq iq = new Iq.Builder(Iq.Type.SET)
                .id("admin-kick-" + System.currentTimeMillis())
                .to(jid)
                .build();
        log.debug("Sending kick-user IQ: {}", iq.toXml());
        return connection.sendIqPacketAsync(iq);
    }
}
