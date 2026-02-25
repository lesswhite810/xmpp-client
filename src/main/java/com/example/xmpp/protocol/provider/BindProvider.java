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

    /** Bind 元素名称 */
    public static final String ELEMENT = "bind";

    /** Bind 命名空间 */
    public static final String NAMESPACE = "urn:ietf:params:xml:ns:xmpp-bind";

    private static final String ELEMENT_JID = "jid";
    private static final String ELEMENT_RESOURCE = "resource";

    /**
     * 获取元素名称。
     *
     * @return 元素名称 "bind"
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间 "urn:ietf:params:xml:ns:xmpp-bind"
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 解析 Bind 元素，提取 jid 和 resource 子元素内容。
     *
     * @param reader XMLEventReader
     * @return 解析后的 Bind 对象
     * @throws XMLStreamException 如果解析过程中发生错误
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
     * @param xml  XmlStringBuilder
     */
    @Override
    protected void serializeInstance(Bind bind, XmlStringBuilder xml) {
        xml.append(bind.toXml());
    }
}
