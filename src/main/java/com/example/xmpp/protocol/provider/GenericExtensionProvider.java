package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.util.XmlStringBuilder;
import com.example.xmpp.util.XmppEventReader;

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
     * 单例实例。
     */
    public static final GenericExtensionProvider INSTANCE = new GenericExtensionProvider();

    private GenericExtensionProvider() {
        // 单例
    }

    /**
     * 解析 XML 元素为 GenericExtensionElement。
     *
     * @param reader     XML 事件读取器，已定位到开始元素
     * @param startEvent 开始元素事件
     * @return 解析后的 GenericExtensionElement
     * @throws XmppParseException 解析异常
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
     */
    private GenericExtensionElement parseElement(XMLEventReader reader, StartElement start)
            throws XMLStreamException {
        String elementName = start.getName().getLocalPart();
        String namespace = start.getName().getNamespaceURI();

        GenericExtensionElement.Builder builder = GenericExtensionElement.builder(elementName, namespace);

        // 添加所有属性
        builder.addAttributes(XmppEventReader.getAttributes(start));

        StringBuilder textBuilder = new StringBuilder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isEndElement()) {
                String endName = event.asEndElement().getName().getLocalPart();
                if (elementName.equals(endName)) {
                    break;
                }
            } else if (event.isStartElement()) {
                // 递归解析子元素
                GenericExtensionElement child = parseElement(reader, event.asStartElement());
                builder.addChild(child);
            } else if (event.isCharacters()) {
                // 收集文本内容
                textBuilder.append(event.asCharacters().getData());
            }
        }

        // 设置文本（去除首尾空白）
        String text = textBuilder.toString().trim();
        if (!text.isEmpty()) {
            builder.text(text);
        }

        return builder.build();
    }

    /**
     * 从当前位置解析元素（reader 尚未调用 next）。
     *
     * @param reader XML 事件读取器
     * @return 解析后的元素
     * @throws XmppParseException 解析异常
     */
    public GenericExtensionElement parseCurrentElement(XMLEventReader reader) throws XmppParseException {
        try {
            XMLEvent event = reader.peek();
            if (event == null || !event.isStartElement()) {
                throw new XmppParseException("Reader is not positioned at a start element");
            }
            return parse(reader, event.asStartElement());
        } catch (XMLStreamException e) {
            throw new XmppParseException("Failed to peek event", e);
        }
    }
}
