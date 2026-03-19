package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * XEP-0133: Service Administration - 添加用户命令。
 *
 * <p>用于在 XMPP 服务上创建新用户账户。
 * 使用 Ad-Hoc Commands (XEP-0050) 和 Data Forms (XEP-0004) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class AddUser extends AbstractAdminCommand {

    /**
     * 添加用户命令节点。
     */
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#add-user";

    /**
     * 要添加的用户账户 JID。
     */
    private String username;

    /**
     * 要添加的用户账户密码。
     */
    private String password;

    /**
     * 要添加的用户邮箱地址。
     */
    private String email;

    /**
     * 创建一个默认提交的添加用户命令。
     */
    public AddUser() {
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个添加用户命令。
     *
     * @param username 要添加的用户账户 JID
     * @param password 要添加的用户账户密码
     */
    public AddUser(String username, String password) {
        this(username, password, null);
    }

    /**
     * 创建一个带邮箱信息的添加用户命令。
     *
     * @param username 要添加的用户账户 JID
     * @param password 要添加的用户账户密码
     * @param email    要添加的用户邮箱地址
     */
    public AddUser(String username, String password, String email) {
        if (!StringUtils.isNotBlank(username)) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        if (!StringUtils.isNotBlank(password)) {
            throw new IllegalArgumentException("password must not be null or blank");
        }
        this.username = username;
        this.password = password;
        this.email = email;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个执行阶段的添加用户命令。
     *
     * @return 请求命令表单的实例
     */
    public static AddUser createExecuteCommand() {
        AddUser cmd = new AddUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建一个提交表单阶段的添加用户命令。
     *
     * @param sessionId 会话标识
     * @param username  要添加的用户账户 JID
     * @param password  要添加的用户账户密码
     * @param email     要添加的用户邮箱地址
     * @return 提交表单结果实例
     */
    public static AddUser createSubmitForm(String sessionId, String username, String password, String email) {
        AddUser cmd = new AddUser(username, password, email);
        cmd.sessionId = sessionId;
        cmd.action = ACTION_COMPLETE;
        return cmd;
    }

    @Override
    protected String getCommandNode() {
        return COMMAND_NODE;
    }

    @Override
    protected void appendFields(XmlStringBuilder xml) {
        if (!StringUtils.isNotBlank(username)) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        if (!StringUtils.isNotBlank(password)) {
            throw new IllegalArgumentException("password must not be null or blank");
        }
        appendField(xml, "accountjid", username);
        appendField(xml, "password", password);
        appendField(xml, "password-verify", password);
        if (email != null) {
            appendField(xml, "email", email);
        }
    }
}
