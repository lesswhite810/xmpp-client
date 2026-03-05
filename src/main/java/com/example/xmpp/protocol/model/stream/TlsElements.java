package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;

/**
 * TLS 相关元素集合，用于 XMPP 连接 TLS 协商。
 * <p>
 * 包含 STARTTLS 扩展中的两个核心元素：
 * <ul>
 *     <li>StartTls - 客户端请求升级为 TLS 加密连接</li>
 *     <li>TlsProceed - 服务端响应同意继续 TLS 协商</li>
 * </ul>
 * 实现 RFC 6120 §5 中的 STARTTLS 流程。
 *
 * @since 2026-02-09
 */
public final class TlsElements {

    private TlsElements() {
    }

    /**
     * STARTTLS 请求元素，表示客户端请求将连接升级为 TLS 加密。
     * <p>
     * 客户端发送此元素到服务端，请求进行 TLS 握手。
     * 服务端可返回 TlsProceed 元素继续协商，或返回 StreamError 拒绝。
     */
    public static final class StartTls implements ExtensionElement {
        public static final String NAMESPACE = XmppConstants.NS_XMPP_TLS;
        public static final StartTls INSTANCE = new StartTls();

        private StartTls() {
        }

        /**
         * 获取元素名称。
         *
         * @return 元素名称 "starttls"
         */
        @Override
        public String getElementName() {
            return "starttls";
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
            return "<starttls xmlns=\"" + NAMESPACE + "\"/>";
        }
    }

    /**
     * TLS 继续元素，表示服务端同意继续进行 TLS 协商。
     * <p>
     * 服务端发送此元素响应客户端的 StartTls 请求，表示可以开始 TLS 握手。
     * 客户端收到此元素后应立即开始 TLS 握手。
     */
    public static final class TlsProceed implements ExtensionElement {
        public static final String NAMESPACE = XmppConstants.NS_XMPP_TLS;
        public static final TlsProceed INSTANCE = new TlsProceed();

        private TlsProceed() {
        }

        /**
         * 获取元素名称。
         *
         * @return 元素名称 "proceed"
         */
        @Override
        public String getElementName() {
            return "proceed";
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
            return "<proceed xmlns=\"" + NAMESPACE + "\"/>";
        }
    }
}
