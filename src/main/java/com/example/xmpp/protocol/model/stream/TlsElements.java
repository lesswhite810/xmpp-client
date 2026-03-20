package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;

/**
 * TLS 相关元素集合。
 *
 * @since 2026-02-09
 */
public final class TlsElements {

    private TlsElements() {
    }

    /**
     * TLS 元素抽象基类，封装统一的命名空间和 XML 序列化逻辑。
     */
    public static abstract class TlsElement implements ExtensionElement {
        public static final String NAMESPACE = XmppConstants.NS_XMPP_TLS;

        protected final String element;

        protected TlsElement(String element) {
            this.element = element;
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
         * @return XML 字符串
         */
        @Override
        public String toXml() {
            return new XmlStringBuilder().wrapElement(element, NAMESPACE, "").toString();
        }
    }

    /**
     * STARTTLS 请求元素。
     */
    public static final class StartTls extends TlsElement {
        public static final String ELEMENT = "starttls";
        public static final StartTls INSTANCE = new StartTls();

        private StartTls() {
            super(ELEMENT);
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
    }

    /**
     * TLS 继续元素。
     */
    public static final class TlsProceed extends TlsElement {
        public static final String ELEMENT = "proceed";
        public static final TlsProceed INSTANCE = new TlsProceed();

        private TlsProceed() {
            super(ELEMENT);
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
    }
}
