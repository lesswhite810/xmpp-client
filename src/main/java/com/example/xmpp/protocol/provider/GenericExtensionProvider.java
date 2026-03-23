package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import com.example.xmpp.util.XmlParserUtils;
import org.apache.commons.lang3.StringUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * 通用扩展元素 Provider。
 *
 * @since 2026-02-27
 */
public class GenericExtensionProvider {

    /**
     * 创建通用扩展元素 Provider。
     */
    private GenericExtensionProvider() {
    }

    /**
     * 单例实例。
     */
    public static final GenericExtensionProvider INSTANCE = new GenericExtensionProvider();

    /**
     * 解析 XML 元素为 GenericExtensionElement。
     *
     * @param reader XML 事件读取器，已定位到开始元素
     * @param startEvent 开始元素事件
     * @return 解析后的 GenericExtensionElement
     * @throws XmppParseException 如果解析过程中发生错误，如 XML 格式错误
     */
    public GenericExtensionElement parse(XMLEventReader reader, StartElement startEvent)
            throws XmppParseException {
        if (reader == null) {
            throw new XmppParseException("XMLEventReader cannot be null");
        }
        if (startEvent == null) {
            throw new XmppParseException("StartElement cannot be null");
        }
        try {
            return parseElement(reader, startEvent);
        } catch (XMLStreamException e) {
            throw new XmppParseException("Failed to parse generic element");
        }
    }

    /**
     * 解析当前元素及其所有子元素。
     *
     * @param reader XML 事件读取器
     * @param start 开始元素事件
     * @return 解析后的 GenericExtensionElement
     * @throws XMLStreamException 如果解析过程中发生 XML 错误
     */
    private GenericExtensionElement parseElement(XMLEventReader reader, StartElement start)
            throws XMLStreamException {
        QName startName = start.getName();
        String elementName = startName.getLocalPart();
        String namespace = startName.getNamespaceURI();

        GenericExtensionElement.Builder builder = GenericExtensionElement.builder(elementName, namespace);
        builder.addAttributes(XmlParserUtils.getAttributes(start));
        boolean closed = false;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isEndElement()) {
                QName endName = event.asEndElement().getName();
                if (startName.equals(endName)) {
                    closed = true;
                    break;
                }
                throw new XMLStreamException("Mismatched end element: expected "
                        + startName + " but found " + endName);
            } else if (event.isStartElement()) {
                GenericExtensionElement child = parseElement(reader, event.asStartElement());
                builder.addChild(child);
            } else if (event.isCharacters()) {
                String text = event.asCharacters().getData();
                if (StringUtils.isNotEmpty(text)) {
                    builder.text(text);
                }
            }
        }

        if (!closed) {
            throw new XMLStreamException("Unexpected end of XML while parsing <" + elementName + ">");
        }
        return builder.build();
    }

    /**
     * 从当前位置解析元素（reader 尚未调用 next）。
     *
     * @param reader XML 事件读取器
     * @return 解析后的 GenericExtensionElement
     * @throws XmppParseException 如果 reader 未定位在开始元素，或解析失败
     */
    public GenericExtensionElement parseCurrentElement(XMLEventReader reader) throws XmppParseException {
        try {
            XMLEvent event = reader.nextEvent();
            if (event == null || !event.isStartElement()) {
                throw new XmppParseException("Reader is not positioned at a start element");
            }
            return parse(reader, event.asStartElement());
        } catch (XMLStreamException e) {
            throw new XmppParseException("Failed to peek event");
        }
    }
}
