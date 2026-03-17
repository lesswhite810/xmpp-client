package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

/**
 * XEP-0133: Service Administration - 管理命令抽象基类。
 *
 * <p>提供 Ad-Hoc Commands 和 Data Forms 的公共 XML 构建逻辑。</p>
 *
 * @since 2026-03-17
 */
@Getter
@Setter
public abstract class AbstractAdminCommand implements ExtensionElement {

    /**
     * Ad-Hoc Commands 命令命名空间。
     */
    public static final String NAMESPACE = "http://jabber.org/protocol/commands";

    /**
     * Data Forms 命名空间。
     */
    public static final String DATA_FORMS_NS = "jabber:x:data";

    /**
     * 执行动作类型 - 请求命令表单。
     */
    public static final String ACTION_EXECUTE = "execute";

    /**
     * 执行动作类型 - 提交表单结果。
     */
    public static final String ACTION_COMPLETE = "complete";

    /**
     * Ad-Hoc Commands 会话标识。
     */
    protected String sessionId;

    /**
     * 当前命令动作类型。
     */
    protected String action;

    /**
     * 获取元素名称。
     *
     * @return 固定返回 {@code command}
     */
    @Override
    public String getElementName() {
        return "command";
    }

    /**
     * 获取命名空间。
     *
     * @return Ad-Hoc Commands 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 获取命令节点名称（子类实现）。
     *
     * @return 命令节点 URL
     */
    protected abstract String getCommandNode();

    /**
     * 获取要添加的表单字段（子类实现）。
     *
     * @param xml XML 构建器
     */
    protected abstract void appendFields(XmlStringBuilder xml);

    /**
     * 构建命令 XML。
     *
     * @return XML 字符串
     */
    @Override
    public String toXml() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.element("command");
        xml.attribute("xmlns", NAMESPACE);
        xml.attribute("node", getCommandNode());
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

        // 添加 Data Form
        xml.element("x");
        xml.attribute("xmlns", DATA_FORMS_NS);
        xml.attribute("type", "submit");
        xml.rightAngleBracket();

        // 添加隐藏字段
        appendHiddenField(xml, "FORM_TYPE", "http://jabber.org/protocol/admin");

        // 添加业务字段
        appendFields(xml);

        xml.closeElement("x");
        xml.closeElement("command");
        return xml.toString();
    }

    /**
     * 添加隐藏字段。
     *
     * @param xml  XML 构建器
     * @param var  字段名
     * @param value 字段值
     */
    protected void appendHiddenField(XmlStringBuilder xml, String var, String value) {
        xml.element("field");
        xml.attribute("var", var);
        xml.attribute("type", "hidden");
        xml.rightAngleBracket();
        xml.textElement("value", value);
        xml.closeElement("field");
    }

    /**
     * 添加普通文本字段。
     *
     * @param xml    XML 构建器
     * @param var    字段名
     * @param value  字段值
     */
    protected void appendField(XmlStringBuilder xml, String var, String value) {
        if (value != null) {
            xml.element("field");
            xml.attribute("var", var);
            xml.rightAngleBracket();
            xml.textElement("value", value);
            xml.closeElement("field");
        }
    }
}
