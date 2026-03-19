package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.protocol.model.extension.Bind;
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
    public static final String ELEMENT = "features";

    /**
     * 流特性命名空间。
     */
    public static final String NAMESPACE = XmppConstants.NS_XMPP_STREAM_FEATURES;

    /**
     * 是否支持 STARTTLS。
     */
    private final boolean starttlsAvailable;

    /**
     * 是否要求必须使用 STARTTLS。
     */
    private final boolean starttlsRequired;

    /**
     * 服务端声明的 SASL 机制列表。
     */
    @Singular
    private final List<String> mechanisms;

    /**
     * 是否支持资源绑定。
     */
    private final boolean bindAvailable;

    /**
     * 获取元素名称。
     *
     * @return 固定返回 {@code features}
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return 流特性命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 序列化为 XML 字符串。
     *
     * @return 流特性元素 XML 字符串
     */
    @Override
    public String toXml() {
        return new XmlStringBuilder()
                .wrapElement("stream:features", xml -> {
                    if (starttlsAvailable) {
                        if (starttlsRequired) {
                            xml.wrapElement(TlsElements.StartTls.ELEMENT, XmppConstants.NS_XMPP_TLS,
                                    startTls -> startTls.wrapElement("required", ""));
                        } else {
                            xml.wrapElement(TlsElements.StartTls.ELEMENT, XmppConstants.NS_XMPP_TLS, "");
                        }
                    }
                    if (mechanisms != null && !mechanisms.isEmpty()) {
                        xml.wrapElement("mechanisms", XmppConstants.NS_XMPP_SASL, mechanismXml -> {
                            for (String mechanism : mechanisms) {
                                mechanismXml.wrapElement("mechanism", mechanism);
                            }
                        });
                    }
                    if (bindAvailable) {
                        xml.wrapElement(Bind.ELEMENT, XmppConstants.NS_XMPP_BIND, "");
                    }
                })
                .toString();
    }
}
