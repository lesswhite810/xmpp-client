package com.example.xmpp.protocol.model.extension;

import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
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
    @Getter(AccessLevel.NONE)
    private final char[] password;

    /**
     * 构造 ConnectionRequest。
     *
     * @param username 用户名
     * @param password 密码
     */
    public ConnectionRequest(String username, String password) {
        this(username, password != null ? password.toCharArray() : null);
    }

    /**
     * 构造 ConnectionRequest。
     *
     * @param username 用户名
     * @param password 密码
     */
    public ConnectionRequest(String username, char[] password) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        if (ArrayUtils.isEmpty(password)) {
            throw new IllegalArgumentException("password must not be null or blank");
        }
        this.username = username;
        this.password = password.clone();
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
                    xml.wrapElement("password", String.valueOf(password));
                })
                .toString();
    }

    /**
     * 返回脱敏的字符串表示，隐藏密码。
     *
     * @return 脱敏的字符串
     */
    @Override
    public String toString() {
        return "ConnectionRequest{username=" + username + ", password=***}";
    }

    /**
     * ConnectionRequest 构造器。
     */
    public static final class Builder {
        private String username;
        private char[] password;

        private Builder() {
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password != null ? password.toCharArray() : null;
            return this;
        }

        public Builder password(char[] password) {
            this.password = password != null ? password.clone() : null;
            return this;
        }

        public ConnectionRequest build() {
            return new ConnectionRequest(username, password);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
