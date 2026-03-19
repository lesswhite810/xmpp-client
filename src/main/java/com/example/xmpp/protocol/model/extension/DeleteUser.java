package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * XEP-0133: Service Administration - 删除用户命令。
 *
 * <p>用于从 XMPP 服务中删除用户账户。
 * 使用 Ad-Hoc Commands (XEP-0050) 和 Data Forms (XEP-0004) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class DeleteUser extends AbstractAdminCommand {

    /**
     * 删除用户命令节点。
     */
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#delete-user";

    /**
     * 要删除的用户账户 JID。
     */
    private String accountJid;

    /**
     * 创建一个默认提交的删除用户命令。
     */
    public DeleteUser() {
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个删除用户命令。
     *
     * @param accountJid 要删除的用户账户 JID
     */
    public DeleteUser(String accountJid) {
        if (StringUtils.isBlank(accountJid)) {
            throw new IllegalArgumentException("accountJid must not be null or blank");
        }
        this.accountJid = accountJid;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个执行阶段的删除用户命令。
     *
     * @return 请求命令表单的实例
     */
    public static DeleteUser createExecuteCommand() {
        DeleteUser cmd = new DeleteUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建一个提交表单阶段的删除用户命令。
     *
     * @param sessionId  会话标识
     * @param accountJid 要删除的用户账户 JID
     * @return 提交表单结果实例
     */
    public static DeleteUser createSubmitForm(String sessionId, String accountJid) {
        DeleteUser cmd = new DeleteUser(accountJid);
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
        xml.wrapElement("field", Map.of("var", "accountjids"),
                fieldXml -> fieldXml.wrapElement("value", accountJid));
    }
}
