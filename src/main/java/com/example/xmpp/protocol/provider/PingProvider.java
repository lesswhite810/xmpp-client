package com.example.xmpp.protocol.provider;

import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.util.XmlStringBuilder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * Ping 扩展 Provider，处理 XMPP Ping（XEP-0199）扩展元素。
 *
 * @since 2026-02-09
 * @see <a href="https://xmpp.org/extensions/xep-0199.html">XEP-0199: XMPP Ping</a>
 */
public final class PingProvider extends AbstractProvider<Ping> {

    /** Ping 元素名称 */
    public static final String ELEMENT = "ping";

    /** Ping 命名空间 */
    public static final String NAMESPACE = "urn:xmpp:ping";

    /**
     * 获取元素名称。
     *
     * @return 元素名称 "ping"
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间 "urn:xmpp:ping"
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 解析 Ping 元素，Ping 元素为空元素，不包含任何内容。
     *
     * @param reader XMLEventReader
     * @return Ping 单例实例
     * @throws XMLStreamException 如果解析过程中发生错误
     */
    @Override
    protected Ping parseInstance(XMLEventReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isElementEnd(event)) {
                break;
            }
        }
        return Ping.INSTANCE;
    }

    /**
     * 序列化 Ping 对象为 XML。
     *
     * @param ping Ping 对象
     * @param xml  XmlStringBuilder
     */
    @Override
    protected void serializeInstance(Ping ping, XmlStringBuilder xml) {
        xml.append(ping.toXml());
    }
}
