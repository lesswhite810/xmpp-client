package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

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
public class AddUser implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#add-user";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";
    public static final String DATA_FORMS_NS = "jabber:x:data";

    // XEP-0050 命令动作类型
    public static final String ACTION_EXECUTE = "execute";
    public static final String ACTION_COMPLETE = "complete";  // 用于提交表单完成命令

    private String username;      // 完整 JID (user@domain)
    private String password;
    private String email;
    private String sessionId;     // 会话ID
    private String action;        // 命令动作

    public AddUser() {
        this.action = ACTION_COMPLETE;  // 默认为完成命令
    }

    public AddUser(String username, String password) {
        this.username = username;
        this.password = password;
        this.action = ACTION_COMPLETE;
    }

    public AddUser(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建执行命令（第一阶段）
     */
    public static AddUser createExecuteCommand() {
        AddUser cmd = new AddUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建提交表单命令（第二阶段）
     */
    public static AddUser createSubmitForm(String sessionId, String username, String password, String email) {
        AddUser cmd = new AddUser(username, password, email);
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

        // 如果是 execute 命令，不包含表单数据
        if (ACTION_EXECUTE.equals(action)) {
            xml.rightAngleBracket();
            xml.closeElement("command");
            return xml.toString();
        }

        // 如果是 complete 命令，包含会话ID和表单数据
        if (sessionId != null) {
            xml.attribute("sessionid", sessionId);
        }
        xml.rightAngleBracket();

        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", "submit");
        xml.rightAngleBracket();

        // 添加 FORM_TYPE 字段（XEP-0004 要求，type="hidden"）
        appendHiddenField(xml, "FORM_TYPE", "http://jabber.org/protocol/admin");

        // accountjid 需要完整的 JID 格式 (user@domain)
        if (username != null) {
            appendField(xml, "accountjid", username);
        }

        if (password != null) {
            appendField(xml, "password", password);
            appendField(xml, "password-verify", password);
        }
        if (email != null) {
            appendField(xml, "email", email);
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

    /**
     * 添加普通文本字段
     */
    private void appendField(XmlStringBuilder xml, String var, String value) {
        if (value != null) {
            xml.element("field");
            xml.attribute("var", var);
            xml.rightAngleBracket();
            xml.textElement("value", value);
            xml.closeElement("field");
        }
    }
}
