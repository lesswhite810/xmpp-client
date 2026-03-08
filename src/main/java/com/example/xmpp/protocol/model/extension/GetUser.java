package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 获取用户信息命令。
 *
 * <p>用于获取 XMPP 服务上指定用户的账户信息。
 * 使用 Ad-Hoc Commands (XEP-0050) 和 Data Forms (XEP-0004) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class GetUser implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#get-user";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";
    public static final String DATA_FORMS_NS = "jabber:x:data";

    // XEP-0050 命令动作类型
    public static final String ACTION_EXECUTE = "execute";
    public static final String ACTION_COMPLETE = "complete";

    private String accountJid;
    private String sessionId;
    private String action;

    public GetUser() {
        this.action = ACTION_COMPLETE;
    }

    public GetUser(String accountJid) {
        this.accountJid = accountJid;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建执行命令（第一阶段）
     */
    public static GetUser createExecuteCommand() {
        GetUser cmd = new GetUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建提交表单命令（第二阶段）
     */
    public static GetUser createSubmitForm(String sessionId, String accountJid) {
        GetUser cmd = new GetUser(accountJid);
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

        // 如果是 complete 命令，包含会话ID和表单数据
        if (sessionId != null) {
            xml.attribute("sessionid", sessionId);
        }
        xml.rightAngleBracket();

        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", "submit");
        xml.rightAngleBracket();

        // 添加 FORM_TYPE 字段
        appendHiddenField(xml, "FORM_TYPE", "http://jabber.org/protocol/admin");

        // accountjid
        if (accountJid != null) {
            appendField(xml, "accountjid", accountJid);
        }

        xml.closeElement("x");
        xml.closeElement("command");
        return xml.toString();
    }

    private void appendHiddenField(XmlStringBuilder xml, String var, String value) {
        xml.element("field");
        xml.attribute("var", var);
        xml.attribute("type", "hidden");
        xml.rightAngleBracket();
        xml.textElement("value", value);
        xml.closeElement("field");
    }

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
