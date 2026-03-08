package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 获取在线用户命令。
 *
 * <p>用于列出 XMPP 服务上当前在线的所有用户。
 * 使用 Ad-Hoc Commands (XEP-0050) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class GetOnlineUsers implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#get-online-users";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";
    public static final String DATA_FORMS_NS = "jabber:x:data";

    private String sessionId;
    private String action;

    public GetOnlineUsers() {
        this.action = "execute";
    }

    /**
     * 创建执行命令（第一阶段）
     */
    public static GetOnlineUsers createExecuteCommand() {
        GetOnlineUsers cmd = new GetOnlineUsers();
        cmd.action = "execute";
        return cmd;
    }

    /**
     * 创建提交表单命令（第二阶段）
     */
    public static GetOnlineUsers createSubmitForm(String sessionId) {
        GetOnlineUsers cmd = new GetOnlineUsers();
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

        // 如果是 complete 命令，包含会话ID
        if (sessionId != null) {
            xml.attribute("sessionid", sessionId);
        }
        xml.rightAngleBracket();

        xml.closeElement("command");
        return xml.toString();
    }
}
