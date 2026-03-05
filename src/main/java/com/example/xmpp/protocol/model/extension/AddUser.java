package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 添加用户命令。
 *
 * <p>用于在 XMPP 服务上创建新用户账户。</p>
 */
@Getter
@Setter
public class AddUser implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#add-user";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    private String username;
    private String password;
    private String email;

    public AddUser() {
    }

    public AddUser(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public AddUser(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
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

        xml.optTextElement("instructions", "Enter a username and password for the new user");
        xml.optTextElement("username", username);
        xml.optTextElement("password", password);
        xml.optTextElement("email", email);

        xml.closeElement("command");
        return xml.toString();
    }
}
