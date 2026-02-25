package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;

/**
 * 连接请求扩展元素。
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
     * 构造方法。
     *
     * @param username 用户名
     * @param password 密码
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
