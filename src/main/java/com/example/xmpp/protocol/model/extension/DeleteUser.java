package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 删除用户命令。
 *
 * <p>用于从 XMPP 服务中删除用户账户。
 * 使用 Ad-Hoc Commands (XEP-0050) 和 Data Forms (XEP-0004) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class DeleteUser implements ExtensionElement {

    /**
     * 删除用户命令节点。
     */
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#delete-user";

    /**
     * Ad-Hoc Commands 命名空间。
     */
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    /**
     * Data Forms 命名空间。
     */
    public static final String DATA_FORMS_NS = "jabber:x:data";

    /**
     * 执行命令动作。
     */
    public static final String ACTION_EXECUTE = "execute";

    /**
     * 提交表单并完成命令动作。
     */
    public static final String ACTION_COMPLETE = "complete";

    /**
     * 待删除用户的完整 JID。
     */
    private String accountJid;

    /**
     * Ad-Hoc Commands 会话标识。
     */
    private String sessionId;

    /**
     * 当前命令动作。
     */
    private String action;

    /**
     * 创建一个默认提交表单的删除用户命令。
     */
    public DeleteUser() {
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个删除用户命令。
     *
     * @param accountJid 待删除用户的完整 JID
     */
    public DeleteUser(String accountJid) {
        this.accountJid = accountJid;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建执行阶段的删除用户命令。
     *
     * @return 仅用于请求表单的命令对象
     */
    public static DeleteUser createExecuteCommand() {
        DeleteUser cmd = new DeleteUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建提交表单阶段的删除用户命令。
     *
     * @param sessionId 命令会话标识
     * @param accountJid 待删除用户的完整 JID
     * @return 可直接提交的命令对象
     */
    public static DeleteUser createSubmitForm(String sessionId, String accountJid) {
        DeleteUser cmd = new DeleteUser(accountJid);
        cmd.sessionId = sessionId;
        cmd.action = ACTION_COMPLETE;
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
     * 将删除用户命令序列化为 XML。
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

        if (ACTION_EXECUTE.equals(action)) {
            xml.rightAngleBracket();
            xml.closeElement("command");
            return xml.toString();
        }

        if (sessionId != null) {
            xml.attribute("sessionid", sessionId);
        }
        xml.rightAngleBracket();

        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", "submit");
        xml.rightAngleBracket();

        appendHiddenField(xml, "FORM_TYPE", "http://jabber.org/protocol/admin");

        if (accountJid != null) {
            xml.element("field");
            xml.attribute("var", "accountjids");
            xml.rightAngleBracket();
            xml.textElement("value", accountJid);
            xml.closeElement("field");
        }

        xml.closeElement("x");
        xml.closeElement("command");
        return xml.toString();
    }

    /**
     * 追加隐藏字段。
     *
     * @param xml XML 构建器
     * @param var 字段名称
     * @param value 字段值
     */
    private void appendHiddenField(XmlStringBuilder xml, String var, String value) {
        xml.element("field");
        xml.attribute("var", var);
        xml.attribute("type", "hidden");
        xml.rightAngleBracket();
        xml.textElement("value", value);
        xml.closeElement("field");
    }
}
