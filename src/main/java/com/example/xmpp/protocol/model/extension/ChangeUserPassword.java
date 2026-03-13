package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 修改用户密码命令。
 *
 * <p>用于修改 XMPP 服务上指定用户的密码。
 * 使用 Ad-Hoc Commands (XEP-0050) 和 Data Forms (XEP-0004) 格式。</p>
 *
 * <p>节点: {@code http://jabber.org/protocol/admin#change-user-password}</p>
 *
 * @since 2026-03-08
 */
@Getter
@Setter
public class ChangeUserPassword implements ExtensionElement {

    /**
     * 修改用户密码命令节点。
     */
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#change-user-password";

    /**
     * Ad-Hoc Commands 命名空间。
     */
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    /**
     * Data Forms 命名空间。
     */
    public static final String DATA_FORMS_NS = "jabber:x:data";

    /**
     * 执行命令动作。
     */
    public static final String ACTION_EXECUTE = "execute";

    /**
     * 提交表单并完成命令动作。
     */
    public static final String ACTION_COMPLETE = "complete";

    /**
     * 待修改密码用户的完整 JID。
     */
    private String accountJid;

    /**
     * 新密码。
     */
    private String newPassword;

    /**
     * Ad-Hoc Commands 会话标识。
     */
    private String sessionId;

    /**
     * 当前命令动作。
     */
    private String action;

    /**
     * 创建一个默认提交表单的修改密码命令。
     */
    public ChangeUserPassword() {
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个修改用户密码命令。
     *
     * @param accountJid 待修改密码用户的完整 JID
     * @param newPassword 新密码
     */
    public ChangeUserPassword(String accountJid, String newPassword) {
        this.accountJid = accountJid;
        this.newPassword = newPassword;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建执行阶段的修改密码命令。
     *
     * @return 仅用于请求表单的命令对象
     */
    public static ChangeUserPassword createExecuteCommand() {
        ChangeUserPassword cmd = new ChangeUserPassword();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建提交表单阶段的修改密码命令。
     *
     * @param sessionId 命令会话标识
     * @param accountJid 待修改密码用户的完整 JID
     * @param newPassword 新密码
     * @return 可直接提交的命令对象
     */
    public static ChangeUserPassword createSubmitForm(String sessionId, String accountJid, String newPassword) {
        ChangeUserPassword cmd = new ChangeUserPassword(accountJid, newPassword);
        cmd.sessionId = sessionId;
        cmd.action = ACTION_COMPLETE;
        return cmd;
    }

    /**
     * 获取扩展元素名称。
     *
     * @return 固定返回 {@code command}
     */
    @Override
    public String getElementName() {
        return "command";
    }

    /**
     * 获取扩展元素命名空间。
     *
     * @return Ad-Hoc Commands 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 将修改用户密码命令序列化为 XML。
     *
     * @return 命令 XML 字符串
     */
    @Override
    public String toXml() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.element("command");
        xml.attribute("xmlns", NAMESPACE);
        xml.attribute("node", COMMAND_NODE);
        xml.attribute("action", action);

        if (ACTION_EXECUTE.equals(action)) {
            xml.rightAngleBracket();
            xml.closeElement("command");
            return xml.toString();
        }

        xml.attribute("sessionid", sessionId);
        xml.rightAngleBracket();

        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", "submit");
        xml.rightAngleBracket();

        xml.element("field");
        xml.attribute("var", "FORM_TYPE");
        xml.attribute("type", "hidden");
        xml.rightAngleBracket();
        xml.textElement("value", "http://jabber.org/protocol/admin");
        xml.closeElement("field");

        if (accountJid != null) {
            xml.element("field");
            xml.attribute("var", "accountjid");
            xml.rightAngleBracket();
            xml.textElement("value", accountJid);
            xml.closeElement("field");
        }

        if (newPassword != null) {
            xml.element("field");
            xml.attribute("var", "password");
            xml.rightAngleBracket();
            xml.textElement("value", newPassword);
            xml.closeElement("field");
        }

        xml.closeElement("x");
        xml.closeElement("command");
        return xml.toString();
    }
}
