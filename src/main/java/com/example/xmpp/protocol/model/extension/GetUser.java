package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 获取用户命令。
 *
 * <p>用于获取 XMPP 服务上指定用户的账户信息。</p>
 */
@Getter
@Setter
public class GetUser implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#get-user";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    private String username;

    public GetUser() {
    }

    public GetUser(String username) {
        this.username = username;
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

        xml.optTextElement("instructions", "Enter the username to get user information");
        xml.optTextElement("username", username);

        xml.closeElement("command");
        return xml.toString();
    }
}
