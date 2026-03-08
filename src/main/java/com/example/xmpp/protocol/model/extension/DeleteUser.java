package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

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
public class DeleteUser implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#delete-user";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";
    public static final String DATA_FORMS_NS = "jabber:x:data";

    // XEP-0050 命令动作类型
    public static final String ACTION_EXECUTE = "execute";
    public static final String ACTION_COMPLETE = "complete";  // 用于提交表单完成命令

    private String accountJid;    // 完整 JID (user@domain)
    private String sessionId;     // 会话ID
    private String action;        // 命令动作

    public DeleteUser() {
        this.action = ACTION_COMPLETE;  // 默认为完成命令
    }

    public DeleteUser(String accountJid) {
        this.accountJid = accountJid;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建执行命令（第一阶段）
     */
    public static DeleteUser createExecuteCommand() {
        DeleteUser cmd = new DeleteUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建提交表单命令（第二阶段）
     */
    public static DeleteUser createSubmitForm(String sessionId, String accountJid) {
        DeleteUser cmd = new DeleteUser(accountJid);
        cmd.sessionId = sessionId;
        cmd.action = ACTION_COMPLETE;  // 使用 complete 提交表单
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

        // 如果是execute命令，不包含表单数据
        if (ACTION_EXECUTE.equals(action)) {
            xml.rightAngleBracket();
            xml.closeElement("command");
            return xml.toString();
        }

        // 如果是complete命令，包含会话ID和表单数据
        if (sessionId != null) {
            xml.attribute("sessionid", sessionId);
        }
        xml.rightAngleBracket();

        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", "submit");
        xml.rightAngleBracket();

        // 添加FORM_TYPE字段（XEP-0004要求，type="hidden"）
        appendHiddenField(xml, "FORM_TYPE", "http://jabber.org/protocol/admin");

        // XEP-0133: accountjids 是 jid-multi 类型，用于删除用户
        if (accountJid != null) {
            xml.element("field");
            xml.attribute("var", "accountjids");
            xml.rightAngleBracket();
            xml.textElement("value", accountJid);
            xml.closeElement("field");
        }

        xml.closeElement("x");
        xml.closeElement("command");
        return xml.toString();
    }

    /**
     * 添加隐藏字段（用于 FORM_TYPE）
     */
    private void appendHiddenField(XmlStringBuilder xml, String var, String value) {
        xml.element("field");
        xml.attribute("var", var);
        xml.attribute("type", "hidden");
        xml.rightAngleBracket();
        xml.textElement("value", value);
        xml.closeElement("field");
    }
}
