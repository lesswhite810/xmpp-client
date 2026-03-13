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

    /**
     * Ping 元素名称。
     */
    public static final String ELEMENT = "ping";

    /**
     * Ping 命名空间。
     */
    public static final String NAMESPACE = "urn:xmpp:ping";

    /**
     * 获取元素名称。
     *
     * @return 固定返回 {@code ping}
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return Ping 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 解析 Ping 元素，Ping 元素为空元素，不包含任何内容。
     *
     * @param reader XMLEventReader，用于读取 XML 事件流
     * @return Ping 单例实例
     * @throws XMLStreamException 如果解析过程中发生 XML 错误
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
     * @param ping Ping 对象（此处未使用，因为 Ping 是单例）
     * @param xml XmlStringBuilder，用于构建 XML 输出
     */
    @Override
    protected void serializeInstance(Ping ping, XmlStringBuilder xml) {
        xml.append(ping.toXml());
    }
}
