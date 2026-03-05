package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * XEP-0133: Service Administration - 列出用户命令。
 *
 * <p>用于列出 XMPP 服务上的所有用户账户。</p>
 */
@Getter
@Setter
public class ListUsers implements ExtensionElement {

    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#list-users";
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    private List<String> domains;

    public ListUsers() {
    }

    public ListUsers(List<String> domains) {
        this.domains = domains;
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

        xml.optTextElement("instructions", "List all users");
        if (domains != null && !domains.isEmpty()) {
            for (String domain : domains) {
                xml.optTextElement("domain", domain);
            }
        }

        xml.closeElement("command");
        return xml.toString();
    }
}
