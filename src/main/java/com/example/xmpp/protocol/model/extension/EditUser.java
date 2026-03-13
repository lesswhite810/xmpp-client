package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 编辑用户命令。
 *
 * <p>用于修改 XMPP 服务上的用户账户信息。
 * 使用 Ad-Hoc Commands (XEP-0050) 和 Data Forms (XEP-0004) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class EditUser implements ExtensionElement {

    /**
     * 编辑用户命令节点。
     */
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#edit-user";

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
     * 提交表单动作。
     */
    public static final String ACTION_SUBMIT = "submit";

    /**
     * 表单类型常量。
     */
    public static final String FORM_TYPE = "form";

    /**
     * 提交类型常量。
     */
    public static final String SUBMIT_TYPE = "submit";

    /**
     * 待编辑用户的完整 JID。
     */
    private String accountJid;

    /**
     * 待更新的密码。
     */
    private String password;

    /**
     * 待更新的邮箱地址。
     */
    private String email;

    /**
     * Ad-Hoc Commands 会话标识。
     */
    private String sessionId;

    /**
     * 当前命令动作。
     */
    private String action;

    /**
     * 创建一个默认提交表单的编辑用户命令。
     */
    public EditUser() {
        this.action = ACTION_SUBMIT;
    }

    /**
     * 创建一个编辑用户命令。
     *
     * @param accountJid 待编辑用户的完整 JID
     */
    public EditUser(String accountJid) {
        this.accountJid = accountJid;
        this.action = ACTION_SUBMIT;
    }

    /**
     * 创建一个带密码更新信息的编辑用户命令。
     *
     * @param accountJid 待编辑用户的完整 JID
     * @param password 待更新的密码
     */
    public EditUser(String accountJid, String password) {
        this.accountJid = accountJid;
        this.password = password;
        this.action = ACTION_SUBMIT;
    }

    /**
     * 创建一个带密码和邮箱更新信息的编辑用户命令。
     *
     * @param accountJid 待编辑用户的完整 JID
     * @param password 待更新的密码
     * @param email 待更新的邮箱地址
     */
    public EditUser(String accountJid, String password, String email) {
        this.accountJid = accountJid;
        this.password = password;
        this.email = email;
        this.action = ACTION_SUBMIT;
    }

    /**
     * 创建执行阶段的编辑用户命令。
     *
     * @return 仅用于请求表单的命令对象
     */
    public static EditUser createExecuteCommand() {
        EditUser cmd = new EditUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建提交表单阶段的编辑用户命令。
     *
     * @param sessionId 命令会话标识
     * @param accountJid 待编辑用户的完整 JID
     * @param password 待更新的密码
     * @param email 待更新的邮箱地址
     * @return 可直接提交的命令对象
     */
    public static EditUser createSubmitForm(String sessionId, String accountJid, String password, String email) {
        EditUser cmd = new EditUser(accountJid, password, email);
        cmd.sessionId = sessionId;
        cmd.action = ACTION_SUBMIT;
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
     * 将编辑用户命令序列化为 XML。
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

        xml.attribute("sessionid", sessionId);
        xml.rightAngleBracket();

        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", SUBMIT_TYPE);
        xml.rightAngleBracket();

        xml.textElement("instructions", "Edit user information");

        appendField(xml, "FORM_TYPE", "hidden", "http://jabber.org/protocol/admin");

        appendField(xml, "accountjid", "Jabber ID", accountJid);
        appendField(xml, "password", "Password", password);
        appendField(xml, "email", "Email", email);

        xml.closeElement("x");
        xml.closeElement("command");
        return xml.toString();
    }

    /**
     * 追加表单字段。
     *
     * @param xml XML 构建器
     * @param var 字段名称
     * @param label 字段标签
     * @param value 字段值
     */
    private void appendField(XmlStringBuilder xml, String var, String label, String value) {
        if (value != null) {
            xml.element("field");
            xml.attribute("var", var);
            xml.attribute("label", label);
            xml.attribute("type", "text-single");
            xml.rightAngleBracket();
            xml.textElement("value", value);
            xml.closeElement("field");
        }
    }
}
