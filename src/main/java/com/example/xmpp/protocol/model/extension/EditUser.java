package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 编辑用户命令。
 *
 * <p>用于修改 XMPP 服务上的用户账户信息。
 * 使用 Ad-Hoc Commands (XEP-0050) 和 Data Forms (XEP-0004) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class EditUser implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#edit-user";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";
    public static final String DATA_FORMS_NS = "jabber:x:data";
    
    // 命令动作类型
    public static final String ACTION_EXECUTE = "execute";
    public static final String ACTION_SUBMIT = "submit";
    
    // 表单类型
    public static final String FORM_TYPE = "form";
    public static final String SUBMIT_TYPE = "submit";

    private String accountJid;
    private String password;
    private String email;
    private String sessionId;  // 会话ID
    private String action;     // 命令动作

    public EditUser() {
        this.action = ACTION_SUBMIT;  // 默认为提交表单
    }

    public EditUser(String accountJid) {
        this.accountJid = accountJid;
        this.action = ACTION_SUBMIT;
    }

    public EditUser(String accountJid, String password) {
        this.accountJid = accountJid;
        this.password = password;
        this.action = ACTION_SUBMIT;
    }

    public EditUser(String accountJid, String password, String email) {
        this.accountJid = accountJid;
        this.password = password;
        this.email = email;
        this.action = ACTION_SUBMIT;
    }

    // 工厂方法
    public static EditUser createExecuteCommand() {
        EditUser cmd = new EditUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    public static EditUser createSubmitForm(String sessionId, String accountJid, String password, String email) {
        EditUser cmd = new EditUser(accountJid, password, email);
        cmd.sessionId = sessionId;
        cmd.action = ACTION_SUBMIT;
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
        
        // 如果是submit命令，包含会话ID和表单数据
        xml.attribute("sessionid", sessionId);
        xml.rightAngleBracket();

        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", SUBMIT_TYPE);
        xml.rightAngleBracket();

        xml.textElement("instructions", "Edit user information");
        
        // 添加FORM_TYPE字段（XEP-0004要求）
        appendField(xml, "FORM_TYPE", "hidden", "http://jabber.org/protocol/admin");
        
        appendField(xml, "accountjid", "Jabber ID", accountJid);
        appendField(xml, "password", "Password", password);
        appendField(xml, "email", "Email", email);

        xml.closeElement("x");
        xml.closeElement("command");
        return xml.toString();
    }

    private void appendField(XmlStringBuilder xml, String var, String label, String value) {
        if (value != null) {
            xml.element("field");
            xml.attribute("var", var);
            xml.attribute("label", label);
            xml.attribute("type", "text-single");
            xml.rightAngleBracket();
            xml.textElement("value", value);
            xml.closeElement("field");
        }
    }
}
