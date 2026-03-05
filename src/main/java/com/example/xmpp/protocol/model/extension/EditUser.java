package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 编辑用户命令。
 *
 * <p>用于修改 XMPP 服务上的用户账户信息。</p>
 */
@Getter
@Setter
public class EditUser implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#edit-user";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    private String username;
    private String newPassword;
    private String email;

    public EditUser() {
    }

    public EditUser(String username) {
        this.username = username;
    }

    public EditUser(String username, String newPassword) {
        this.username = username;
        this.newPassword = newPassword;
    }

    public EditUser(String username, String newPassword, String email) {
        this.username = username;
        this.newPassword = newPassword;
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

        xml.optTextElement("instructions", "Enter the new information for the user");
        xml.optTextElement("username", username);
        xml.optTextElement("password", newPassword);
        xml.optTextElement("email", email);

        xml.closeElement("command");
        return xml.toString();
    }
}
