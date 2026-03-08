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

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#change-user-password";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";
    public static final String DATA_FORMS_NS = "jabber:x:data";

    // 命令动作类型
    public static final String ACTION_EXECUTE = "execute";
    public static final String ACTION_COMPLETE = "complete";

    private String accountJid;
    private String newPassword;
    private String sessionId;
    private String action;

    public ChangeUserPassword() {
        this.action = ACTION_COMPLETE;
    }

    public ChangeUserPassword(String accountJid, String newPassword) {
        this.accountJid = accountJid;
        this.newPassword = newPassword;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建 execute 命令（获取表单）。
     */
    public static ChangeUserPassword createExecuteCommand() {
        ChangeUserPassword cmd = new ChangeUserPassword();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建提交表单（修改密码）。
     *
     * @param sessionId 会话ID（从 execute 响应中获取）
     * @param accountJid 用户 JID
     * @param newPassword 新密码
     */
    public static ChangeUserPassword createSubmitForm(String sessionId, String accountJid, String newPassword) {
        ChangeUserPassword cmd = new ChangeUserPassword(accountJid, newPassword);
        cmd.sessionId = sessionId;
        cmd.action = ACTION_COMPLETE;
        return cmd;
    }

    @Override
    public String getElementName() {
        return "command";
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String toXml() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.element("command");
        xml.attribute("xmlns", NAMESPACE);
        xml.attribute("node", COMMAND_NODE);
        xml.attribute("action", action);

        // 如果是 execute 命令，不包含表单数据
        if (ACTION_EXECUTE.equals(action)) {
            xml.rightAngleBracket();
            xml.closeElement("command");
            return xml.toString();
        }

        // 提交表单
        xml.attribute("sessionid", sessionId);
        xml.rightAngleBracket();

        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", "submit");
        xml.rightAngleBracket();

        // FORM_TYPE 字段
        xml.element("field");
        xml.attribute("var", "FORM_TYPE");
        xml.attribute("type", "hidden");
        xml.rightAngleBracket();
        xml.textElement("value", "http://jabber.org/protocol/admin");
        xml.closeElement("field");

        // accountjid 字段
        if (accountJid != null) {
            xml.element("field");
            xml.attribute("var", "accountjid");
            xml.rightAngleBracket();
            xml.textElement("value", accountJid);
            xml.closeElement("field");
        }

        // password 字段
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
