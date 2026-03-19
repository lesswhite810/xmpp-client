package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

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
        Map<String, Object> commandAttributes = new LinkedHashMap<>();
        commandAttributes.put("node", getCommandNode());
        commandAttributes.put("action", action);

        if (ACTION_EXECUTE.equals(action)) {
            return new XmlStringBuilder().wrapElement("command", NAMESPACE, commandAttributes, "").toString();
        }

        if (sessionId != null) {
            commandAttributes.put("sessionid", sessionId);
        }
        return new XmlStringBuilder()
                .wrapElement("command", NAMESPACE, commandAttributes, xml -> xml.wrapElement("x",
                        DATA_FORMS_NS,
                        Map.of("type", "submit"),
                        formXml -> {
                            appendHiddenField(formXml, "FORM_TYPE", "http://jabber.org/protocol/admin");
                            appendFields(formXml);
                        }))
                .toString();
    }

    /**
     * 添加隐藏字段。
     *
     * @param xml   XML 构建器
     * @param var   字段名，不能为空或空白
     * @param value 字段值
     * @throws IllegalArgumentException 如果 {@code var} 为 null 或空白
     */
    protected void appendHiddenField(XmlStringBuilder xml, String var, String value) {
        if (var == null || var.isBlank()) {
            throw new IllegalArgumentException("var must not be null or blank");
        }
        xml.wrapElement("field", Map.of("var", var, "type", "hidden"),
                fieldXml -> fieldXml.wrapElement("value", value));
    }

    /**
     * 添加普通文本字段。
     *
     * @param xml   XML 构建器
     * @param var   字段名
     * @param value 字段值
     */
    protected void appendField(XmlStringBuilder xml, String var, String value) {
        if (value != null) {
            xml.wrapElement("field", Map.of("var", var),
                    fieldXml -> fieldXml.wrapElement("value", value));
        }
    }
}
