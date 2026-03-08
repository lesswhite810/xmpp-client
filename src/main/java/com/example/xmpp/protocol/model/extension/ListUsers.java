package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * XEP-0133: Service Administration - 列出用户命令。
 *
 * <p>用于列出 XMPP 服务上的所有用户账户。
 * 使用 Ad-Hoc Commands (XEP-0050) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class ListUsers implements ExtensionElement {

    // XEP-0133 标准命令节点：获取已注册用户列表
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#get-registered-users-list";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";
    public static final String DATA_FORMS_NS = "jabber:x:data";

    private List<String> searchDomains;
    private String sessionId;
    private String action;

    public ListUsers() {
        this.action = "execute";
    }

    public ListUsers(List<String> searchDomains) {
        this.searchDomains = searchDomains;
        this.action = "execute";
    }

    /**
     * 创建执行命令（第一阶段）
     */
    public static ListUsers createExecuteCommand() {
        ListUsers cmd = new ListUsers();
        cmd.action = "execute";
        return cmd;
    }

    /**
     * 创建提交表单命令（第二阶段）
     */
    public static ListUsers createSubmitForm(String sessionId, List<String> domains) {
        ListUsers cmd = new ListUsers(domains);
        cmd.sessionId = sessionId;
        cmd.action = "complete";
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
        if ("execute".equals(action)) {
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

        // 如果有搜索域限制
        if (searchDomains != null && !searchDomains.isEmpty()) {
            for (String domain : searchDomains) {
                appendField(xml, "domain", domain);
            }
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
