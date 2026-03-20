package com.example.xmpp.protocol.provider;

import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.util.XmlStringBuilder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Bind 扩展 Provider，处理 XMPP 资源绑定（Resource Binding）扩展元素。
 *
 * @since 2026-02-09
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6120#section-7">RFC 6120 Section 7</a>
 */
public final class BindProvider extends AbstractProvider<Bind> {

    /**
     * 绑定元素名称。
     */
    public static final String ELEMENT = "bind";

    /**
     * 绑定命名空间。
     */
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:xmpp-bind";

    private static final String ELEMENT_JID = "jid";
    private static final String ELEMENT_RESOURCE = "resource";

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
     * 解析 Bind 元素。
     *
     * @param reader XML 事件读取器
     * @return Bind 对象
     * @throws XMLStreamException XML 解析失败
     */
    @Override
    protected Bind parseInstance(XMLEventReader reader) throws XMLStreamException {
        Bind.BindBuilder builder = Bind.builder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (isElementEnd(event)) {
                break;
            }

            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                String localName = start.getName().getLocalPart();

                switch (localName) {
                    case ELEMENT_JID -> {
                        String jid = getElementText(reader);
                        builder.jid(jid);
                    }
                    case ELEMENT_RESOURCE -> {
                        String resource = getElementText(reader);
                        builder.resource(resource);
                    }
                    default -> {
                    }
                }
            }
        }

        return builder.build();
    }

    /**
     * 序列化 Bind 对象为 XML。
     *
     * @param bind Bind 对象
     * @param xml XML 构建器
     */
    @Override
    protected void serializeInstance(Bind bind, XmlStringBuilder xml) {
        xml.append(bind.toXml());
    }
}
