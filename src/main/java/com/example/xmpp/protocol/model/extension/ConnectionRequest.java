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
    public static final String ELEMENT = "connectionRequest";
    public static final String NAMESPACE = "urn:broadband-forum-org:cwmp:xmppConnReq-1-0";

    private final String username;
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
     * @return 元素名称 "connectionRequest"
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 序列化为 XML 字符串。
     *
     * @return XML 字符串表示
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .openElement("connectionRequest", NAMESPACE)
                .optTextElement("username", username)
                .optTextElement("password", password)
                .closeElement("connectionRequest")
                .toString();
    }
}
