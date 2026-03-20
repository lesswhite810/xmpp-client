package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * TR-069 连接请求扩展元素。
 *
 * @since 2026-02-09
 */
@Getter
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
     * 构造 ConnectionRequest。
     *
     * @param username 用户名
     * @param password 密码
     */
    @lombok.Builder
    public ConnectionRequest(String username, String password) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        if (StringUtils.isBlank(password)) {
            throw new IllegalArgumentException("password must not be null or blank");
        }
        this.username = username;
        this.password = password;
    }

    /**
     * 获取元素名称。
     *
     * @return 元素名称
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
     * 序列化为 XML。
     *
     * @return XML 字符串
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .wrapElement(ELEMENT, NAMESPACE, xml -> {
                    xml.wrapElement("username", username);
                    xml.wrapElement("password", password);
                })
                .toString();
    }
}
