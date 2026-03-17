package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * TR-069 连接请求扩展元素，用于 CPE (Customer Premises Equipment) 管理。
 * <p>
 * 实现 Broadband Forum 规范的 CWMP (CPE WAN Management Protocol) XMPP 扩展，
 * 允许 ACS (Auto-Configuration Server) 通过 XMPP 通道向 CPE 发起连接请求。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class ConnectionRequest implements ExtensionElement {
    /**
     * 元素名称。
     */
    public static final String ELEMENT = "connectionRequest";

    /**
     * Broadband Forum 连接请求命名空间。
     */
    public static final String NAMESPACE = "urn:broadband-forum-org:cwmp:xmppConnReq-1-0";

    /**
     * CPE 用户名。
     */
    private final String username;

    /**
     * CPE 密码。
     */
    private final String password;

    /**
     * 构造 ConnectionRequest 实例。
     *
     * @param username CPE 用户名，用于身份验证
     * @param password CPE 密码，用于身份验证
     */
    public ConnectionRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * 获取元素名称。
     *
     * @return 固定返回 {@code connectionRequest}
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return Broadband Forum 连接请求命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 序列化为 XML 字符串。
     *
     * @return 扩展元素 XML 字符串
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .openElement(ELEMENT, NAMESPACE)
                .optTextElement("username", username)
                .optTextElement("password", password)
                .closeElement(ELEMENT)
                .toString();
    }
}
