package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import com.example.xmpp.util.XmlParserUtils;
import org.apache.commons.lang3.StringUtils;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * 通用扩展元素 Provider，用于解析未知的 XML 元素。
 *
 * <p>当 ProviderRegistry 中没有注册对应的 Provider 时，
 * 使用此 Provider 解析元素为 {@link GenericExtensionElement}，避免数据丢失。</p>
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
        try {
            return parseElement(reader, startEvent);
        } catch (XMLStreamException e) {
            throw new XmppParseException("Failed to parse generic element", e);
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
        String elementName = start.getName().getLocalPart();
        String namespace = start.getName().getNamespaceURI();

        GenericExtensionElement.Builder builder = GenericExtensionElement.builder(elementName, namespace);

        builder.addAttributes(XmlParserUtils.getAttributes(start));

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isEndElement()) {
                String endName = event.asEndElement().getName().getLocalPart();
                if (elementName.equals(endName)) {
                    break;
                }
            } else if (event.isStartElement()) {
                GenericExtensionElement child = parseElement(reader, event.asStartElement());
                builder.addChild(child);
            } else if (event.isCharacters()) {
                String text = event.asCharacters().getData();
                if (StringUtils.isNotBlank(text)) {
                    builder.text(text);
                }
            }
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
            throw new XmppParseException("Failed to peek event", e);
        }
    }
}
