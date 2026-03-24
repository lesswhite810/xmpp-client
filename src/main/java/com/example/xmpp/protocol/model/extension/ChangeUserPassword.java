package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.util.XmlStringBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * XEP-0133 修改用户密码命令。
 *
 * @since 2026-03-08
 */
@Getter
@Setter
public class ChangeUserPassword extends AbstractAdminCommand {

    /**
     * 修改用户密码命令节点。
     */
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#change-user-password";

    /**
     * 要修改密码的用户账户 JID。
     */
    private String accountJid;

    /**
     * 新密码。
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String newPassword;

    /**
     * 创建一个默认提交的修改密码命令。
     */
    public ChangeUserPassword() {
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个修改用户密码命令。
     *
     * @param accountJid  要修改的用户账户 JID
     * @param newPassword 新密码
     */
    public ChangeUserPassword(String accountJid, String newPassword) {
        if (StringUtils.isBlank(accountJid)) {
            throw new IllegalArgumentException("accountJid must not be null or blank");
        }
        if (StringUtils.isBlank(newPassword)) {
            throw new IllegalArgumentException("newPassword must not be null or blank");
        }
        this.accountJid = accountJid;
        this.newPassword = newPassword;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个执行阶段的修改密码命令。
     *
     * @return 请求命令表单的实例
     */
    public static ChangeUserPassword createExecuteCommand() {
        ChangeUserPassword cmd = new ChangeUserPassword();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建一个提交表单阶段的修改密码命令。
     *
     * @param sessionId  会话标识
     * @param accountJid 要修改的用户账户 JID
     * @param newPassword 新密码
     * @return 提交表单结果实例
     */
    public static ChangeUserPassword createSubmitForm(String sessionId, String accountJid, String newPassword) {
        ChangeUserPassword cmd = new ChangeUserPassword(accountJid, newPassword);
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
        if (StringUtils.isBlank(accountJid)) {
            throw new IllegalArgumentException("accountJid must not be null or blank");
        }
        if (StringUtils.isBlank(newPassword)) {
            throw new IllegalArgumentException("newPassword must not be null or blank");
        }
        appendField(xml, "accountjid", accountJid);
        appendField(xml, "password", newPassword);
    }

    /**
     * 返回脱敏的字符串表示，隐藏密码。
     *
     * @return 脱敏的字符串
     */
    @Override
    public String toString() {
        return "ChangeUserPassword{accountJid=" + accountJid + ", newPassword=***}";
    }
}
