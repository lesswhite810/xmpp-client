package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;

/**
 * TLS 相关元素（STARTTLS 和 PROCEED）。
 *
 * @since 2026-02-09
 */
public final class TlsElements {

    private TlsElements() {
    }

    /**
     * STARTTLS 扩展元素。
     * <p>
     * 表示升级连接为 TLS 的请求。
     *
     * @since 2026-02-09
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
     * TLS PROCEED 元素。
     * <p>
     * 表示服务端响应继续进行 TLS 协商。
     *
     * @since 2026-02-09
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
