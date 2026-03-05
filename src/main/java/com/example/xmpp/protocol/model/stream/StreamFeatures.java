package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * XMPP 流特性元素，实现 RFC 6120 §4.6 Stream Features。
 * <p>
 * 流特性 (stream features) 由服务端在 XMPP 流建立后、认证前发送，
 * 告知客户端当前连接支持的协议特性，包括：
 * <ul>
 *     <li>STARTTLS - 传输层安全升级</li>
 *     <li>SASL Mechanisms - 可用的 SASL 认证机制列表</li>
 *     <li>Resource Binding - 资源绑定</li>
 * </ul>
 * 客户端应根据收到的特性选择适当的流程进行协商。
 *
 * @since 2026-02-09
 */
@Getter
@Builder
public class StreamFeatures implements ExtensionElement {
    public static final String NAMESPACE = XmppConstants.NS_XMPP_STREAM_FEATURES;

    private final boolean starttlsAvailable;
    private final boolean starttlsRequired;
    @Singular
    private final List<String> mechanisms;
    private final boolean bindAvailable;

    /**
     * 获取元素名称。
     *
     * @return 元素名称 "features"
     */
    @Override
    public String getElementName() {
        return "features";
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
        XmlStringBuilder xml = new XmlStringBuilder().openElement("stream:features");
        if (starttlsAvailable) {
            if (starttlsRequired) {
                xml.openElement("starttls", "urn:ietf:params:xml:ns:xmpp-tls")
                        .emptyElement("required")
                        .closeElement("starttls");
            } else {
                xml.emptyElement("starttls", "urn:ietf:params:xml:ns:xmpp-tls");
            }
        }
        if (mechanisms != null && !mechanisms.isEmpty()) {
            xml.openElement("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl");
            for (String mechanism : mechanisms) {
                xml.textElement("mechanism", mechanism);
            }
            xml.closeElement("mechanisms");
        }
        if (bindAvailable) {
            xml.emptyElement("bind", "urn:ietf:params:xml:ns:xmpp-bind");
        }
        return xml.closeElement("stream:features").toString();
    }
}
