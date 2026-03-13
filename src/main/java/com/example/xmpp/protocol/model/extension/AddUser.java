package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 添加用户命令。
 *
 * <p>用于在 XMPP 服务上创建新用户账户。
 * 使用 Ad-Hoc Commands (XEP-0050) 和 Data Forms (XEP-0004) 格式。</p>
 *
 * @since 2026-02-09
 */
@Getter
@Setter
public class AddUser implements ExtensionElement {

    /**
     * 添加用户命令节点。
     */
    public static final String COMMAND_NODE = "http://jabber.org/protocol/admin#add-user";

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
     * 待创建用户的完整 JID。
     */
    private String username;

    /**
     * 待创建用户的密码。
     */
    private String password;

    /**
     * 待创建用户的邮箱地址。
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
     * 创建一个默认提交表单的添加用户命令。
     */
    public AddUser() {
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个添加用户命令。
     *
     * @param username 待创建用户的完整 JID
     * @param password 待创建用户的密码
     */
    public AddUser(String username, String password) {
        this.username = username;
        this.password = password;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建一个带邮箱信息的添加用户命令。
     *
     * @param username 待创建用户的完整 JID
     * @param password 待创建用户的密码
     * @param email 待创建用户的邮箱地址
     */
    public AddUser(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.action = ACTION_COMPLETE;
    }

    /**
     * 创建执行阶段的添加用户命令。
     *
     * @return 仅用于请求表单的命令对象
     */
    public static AddUser createExecuteCommand() {
        AddUser cmd = new AddUser();
        cmd.action = ACTION_EXECUTE;
        return cmd;
    }

    /**
     * 创建提交表单阶段的添加用户命令。
     *
     * @param sessionId 命令会话标识
     * @param username 待创建用户的完整 JID
     * @param password 待创建用户的密码
     * @param email 待创建用户的邮箱地址
     * @return 可直接提交的命令对象
     */
    public static AddUser createSubmitForm(String sessionId, String username, String password, String email) {
        AddUser cmd = new AddUser(username, password, email);
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
     * 将添加用户命令序列化为 XML。
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

        if (username != null) {
            appendField(xml, "accountjid", username);
        }

        if (password != null) {
            appendField(xml, "password", password);
            appendField(xml, "password-verify", password);
        }
        if (email != null) {
            appendField(xml, "email", email);
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

    /**
     * 追加普通文本字段。
     *
     * @param xml XML 构建器
     * @param var 字段名称
     * @param value 字段值
     */
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
