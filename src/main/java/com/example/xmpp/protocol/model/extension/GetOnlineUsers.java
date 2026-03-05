package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 获取在线用户命令。
 *
 * <p>用于列出 XMPP 服务上当前在线的所有用户。</p>
 */
@Getter
@Setter
public class GetOnlineUsers implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#get-online-users";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    public GetOnlineUsers() {
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
        xml.openElement("command");
        xml.attribute("node", COMMAND_NODE);
        xml.rightAngleBracket();

        xml.optTextElement("instructions", "List all online users");

        xml.closeElement("command");
        return xml.toString();
    }
}
