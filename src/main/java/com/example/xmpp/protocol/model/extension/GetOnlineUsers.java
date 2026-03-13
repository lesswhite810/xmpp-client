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

    /**
     * 获取在线用户命令节点。
     */
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#get-online-users";

    /**
     * Ad-Hoc Commands 命名空间。
     */
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    /**
     * Data Forms 命名空间。
     */
    public static final String DATA_FORMS_NS = "jabber:x:data";

    /**
     * Ad-Hoc Commands 会话标识。
     */
    private String sessionId;

    /**
     * 当前命令动作。
     */
    private String action;

    /**
     * 创建一个默认执行阶段的获取在线用户命令。
     */
    public GetOnlineUsers() {
        this.action = "execute";
    }

    /**
     * 创建执行阶段的获取在线用户命令。
     *
     * @return 仅用于请求表单的命令对象
     */
    public static GetOnlineUsers createExecuteCommand() {
        GetOnlineUsers cmd = new GetOnlineUsers();
        cmd.action = "execute";
        return cmd;
    }

    /**
     * 创建提交表单阶段的获取在线用户命令。
     *
     * @param sessionId 命令会话标识
     * @return 可直接提交的命令对象
     */
    public static GetOnlineUsers createSubmitForm(String sessionId) {
        GetOnlineUsers cmd = new GetOnlineUsers();
        cmd.sessionId = sessionId;
        cmd.action = "complete";
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
     * 将获取在线用户命令序列化为 XML。
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

        if ("execute".equals(action)) {
            xml.rightAngleBracket();
            xml.closeElement("command");
            return xml.toString();
        }

        if (sessionId != null) {
            xml.attribute("sessionid", sessionId);
        }
        xml.rightAngleBracket();

        xml.closeElement("command");
        return xml.toString();
    }
}
