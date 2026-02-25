package com.example.xmpp.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.namespace.QName;
import java.io.InputStream;
import java.io.Reader;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import lombok.experimental.UtilityClass;

/**
 * Woodstox XMLEventReader 高级封装工具类。
 *
 * <p>
 * 基于 XMLEventReader（事件迭代器模式）提供更易用的 XML 解析 API。
 * 相比 XMLStreamReader（游标模式），XMLEventReader 更面向对象，
 * 事件对象可重复使用，适合复杂的 XMPP Stanza 处理场景。
 * </p>
 *
 * @since 2026-02-09
 */
@UtilityClass
public class XmppEventReader {

    private static final XMLInputFactory INPUT_FACTORY = XmlParserUtils.getSharedInputFactory();

    /**
     * 从输入流创建 XMLEventReader。
     *
     * @param inputStream 输入流
     * @return XMLEventReader
     * @throws XMLStreamException 如果创建 reader 失败
     */
    public static XMLEventReader createReader(InputStream inputStream) throws XMLStreamException {
        return INPUT_FACTORY.createXMLEventReader(inputStream);
    }

    /**
     * 从 Reader 创建 XMLEventReader。
     *
     * @param reader Reader
     * @return XMLEventReader
     * @throws XMLStreamException 如果创建 reader 失败
     */
    public static XMLEventReader createReader(Reader reader) throws XMLStreamException {
        return INPUT_FACTORY.createXMLEventReader(reader);
    }

    /**
     * 从字节数组创建 XMLEventReader。
     *
     * @param bytes 字节数组
     * @return XMLEventReader
     * @throws XMLStreamException 如果创建 reader 失败
     */
    public static XMLEventReader createReader(byte[] bytes) throws XMLStreamException {
        return INPUT_FACTORY.createXMLEventReader(new ByteArrayInputStream(bytes));
    }

    /**
     * 从 Netty ByteBuf 创建 XMLEventReader。
     *
     * @param byteBuf Netty ByteBuf
     * @return XMLEventReader
     * @throws XMLStreamException 如果创建 reader 失败
     */
    public static XMLEventReader createReader(ByteBuf byteBuf) throws XMLStreamException {
        return INPUT_FACTORY.createXMLEventReader(new ByteBufInputStream(byteBuf));
    }

    /**
     * 获取开始元素的所有属性。
     *
     * @param startElement 开始元素
     * @return 属性 Map，key 为属性名，value 为属性值
     */
    public static Map<String, String> getAttributes(StartElement startElement) {
        Map<String, String> attrs = new HashMap<>();
        Iterator<Attribute> iter = startElement.getAttributes();
        while (iter.hasNext()) {
            Attribute attr = iter.next();
            attrs.put(attr.getName().getLocalPart(), attr.getValue());
        }
        return attrs;
    }

    /**
     * 获取开始元素的指定属性。
     *
     * @param startElement 开始元素
     * @param attrName     属性名称
     * @return Optional 包含属性值，如果不存在则返回空
     */
    public static Optional<String> getAttribute(StartElement startElement, String attrName) {
        Attribute attr = startElement.getAttributeByName(new QName(attrName));
        return attr != null ? Optional.of(attr.getValue()) : Optional.empty();
    }

    /**
     * 获取元素的文本内容。
     *
     * @param reader XMLEventReader
     * @return 文本内容
     * @throws XMLStreamException 如果读取失败
     */
    public static String getElementText(XMLEventReader reader) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            XMLEvent event = reader.peek();
            if (event.isCharacters()) {
                sb.append(reader.nextEvent().asCharacters().getData());
            } else if (event.isEndElement()) {
                break;
            } else {
                reader.nextEvent();
            }
        }
        return sb.toString().trim();
    }

    /**
     * 查找下一个指定名称的开始元素。
     *
     * @param reader      XMLEventReader
     * @param elementName 元素名称
     * @return Optional 包含找到的开始元素，如果未找到则返回空
     * @throws XMLStreamException 如果读取失败
     */
    public static Optional<StartElement> findNextStartElement(XMLEventReader reader, String elementName)
            throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                if (start.getName().getLocalPart().equals(elementName)) {
                    return Optional.of(start);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 检查事件是否为指定名称的开始元素。
     *
     * @param event       XML 事件
     * @param elementName 元素名称
     * @return 如果是开始元素且名称匹配则返回 true
     */
    public static boolean isStartElement(XMLEvent event, String elementName) {
        return event.isStartElement()
                && event.asStartElement().getName().getLocalPart().equals(elementName);
    }

    /**
     * 检查事件是否为指定名称的结束元素。
     *
     * @param event       XML 事件
     * @param elementName 元素名称
     * @return 如果是结束元素且名称匹配则返回 true
     */
    public static boolean isEndElement(XMLEvent event, String elementName) {
        return event.isEndElement()
                && event.asEndElement().getName().getLocalPart().equals(elementName);
    }

    /**
     * 安静地关闭 XMLEventReader。
     *
     * <p>
     * 忽略关闭时可能抛出的异常。
     * </p>
     *
     * @param reader XMLEventReader，可为 null
     */
    public static void closeQuietly(XMLEventReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                // ignore
            }
        }
    }

    /**
     * 检查元素是否具有指定的本地名称和命名空间
     *
     * @param event     XMLEvent
     * @param localName 本地元素名称
     * @param namespace 命名空间
     * @return 是否匹配
     */
    public static boolean isStartElement(XMLEvent event, String localName, String namespace) {
        if (!event.isStartElement()) {
            return false;
        }
        StartElement start = event.asStartElement();
        QName qName = start.getName();
        return qName.getLocalPart().equals(localName) &&
                (namespace == null || namespace.equals(qName.getNamespaceURI()));
    }

    /**
     * 查找下一个具有指定本地名称和命名空间的开始元素
     *
     * @param reader    XMLEventReader
     * @param localName 本地元素名称
     * @param namespace 命名空间
     * @return Optional<StartElement>
     * @throws XMLStreamException 如果解析失败
     */
    public static Optional<StartElement> findNextStartElement(XMLEventReader reader,
            String localName,
            String namespace)
            throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isStartElement(event, localName, namespace)) {
                return Optional.of(event.asStartElement());
            }
        }
        return Optional.empty();
    }

    /**
     * 获取元素的命名空间
     *
     * @param startElement StartElement
     * @return 命名空间URI
     */
    public static String getNamespace(StartElement startElement) {
        return startElement.getName().getNamespaceURI();
    }

    /**
     * 获取元素的本地名称
     *
     * @param startElement StartElement
     * @return 本地元素名称
     */
    public static String getLocalName(StartElement startElement) {
        return startElement.getName().getLocalPart();
    }

    /**
     * 获取元素的前缀
     *
     * @param startElement StartElement
     * @return 前缀
     */
    public static String getPrefix(StartElement startElement) {
        return startElement.getName().getPrefix();
    }

    /**
     * 跳过到指定元素名称的结束元素
     *
     * @param reader      XMLEventReader
     * @param elementName 元素名称
     * @throws XMLStreamException 如果解析失败
     */
    public static void skipToEndElement(XMLEventReader reader, String elementName) throws XMLStreamException {
        int depth = 0;
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement() && event.asStartElement().getName().getLocalPart().equals(elementName)) {
                depth++;
            } else if (event.isEndElement() && event.asEndElement().getName().getLocalPart().equals(elementName)) {
                depth--;
                if (depth < 0) {
                    break;
                }
            }
        }
    }

    /**
     * 跳过当前元素的所有内容，包括子元素。
     *
     * <p>
     * 此方法将 reader 定位到当前开始元素对应的结束元素之后。
     * 适用于需要忽略某个元素全部内容的场景。
     * </p>
     *
     * @param reader XMLEventReader（当前位置应为目标元素的开始元素）
     * @throws XMLStreamException 如果解析失败
     */
    public static void skipElement(XMLEventReader reader) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                depth++;
            } else if (event.isEndElement()) {
                depth--;
            }
        }
    }

    /**
     * 读取当前元素的全部文本内容，跳过任何子元素的标签。
     *
     * <p>
     * 与 {@link #getElementText(XMLEventReader)} 不同，此方法不会移动 reader 到结束元素之后，
     * 而是只读取文本内容，保留 reader 在结束元素位置。
     * </p>
     *
     * @param reader XMLEventReader
     * @return 元素的文本内容，如果没有文本则返回空字符串
     * @throws XMLStreamException 如果解析失败
     */
    public static String getTextContent(XMLEventReader reader) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            XMLEvent event = reader.peek();
            if (event.isCharacters()) {
                sb.append(reader.nextEvent().asCharacters().getData());
            } else if (event.isEndElement()) {
                break;
            } else {
                reader.nextEvent(); // 跳过非字符事件
            }
        }
        return sb.toString();
    }

    /**
     * 检查下一个事件是否为指定元素的结束标签。
     *
     * @param reader      XMLEventReader
     * @param elementName 元素名称
     * @return 如果下一个事件是指定元素的结束标签则返回 true
     * @throws XMLStreamException 如果读取失败
     */
    public static boolean isNextEndElement(XMLEventReader reader, String elementName) throws XMLStreamException {
        if (!reader.hasNext()) {
            return false;
        }
        XMLEvent event = reader.peek();
        return isEndElement(event, elementName);
    }

    /**
     * 读取直到下一个指定元素的开始或结束标签。
     *
     * <p>
     * 此方法跳过所有中间内容，将 reader 定位到目标元素。
     * </p>
     *
     * @param reader      XMLEventReader
     * @param elementName 目标元素名称
     * @return 如果找到目标元素则返回 true
     * @throws XMLStreamException 如果解析失败
     */
    public static boolean skipToElement(XMLEventReader reader, String elementName) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isStartElement(event, elementName) || isEndElement(event, elementName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取开始元素的所有属性（包括命名空间属性）。
     *
     * <p>
     * 与 {@link #getAttributes(StartElement)} 不同，此方法包含命名空间声明。
     * </p>
     *
     * @param startElement StartElement
     * @return 属性 Map，key 为属性名，value 为属性值
     */
    public static Map<String, String> getAllAttributes(StartElement startElement) {
        Map<String, String> attrs = new HashMap<>();

        // 普通属性
        Iterator<Attribute> attrIter = startElement.getAttributes();
        while (attrIter.hasNext()) {
            Attribute attr = attrIter.next();
            attrs.put(attr.getName().getLocalPart(), attr.getValue());
        }

        // 命名空间声明
        Iterator<javax.xml.stream.events.Namespace> nsIter = startElement.getNamespaces();
        while (nsIter.hasNext()) {
            javax.xml.stream.events.Namespace ns = nsIter.next();
            String prefix = ns.getPrefix();
            String key = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
            attrs.put(key, ns.getNamespaceURI());
        }

        return attrs;
    }

    /**
     * 检查元素是否包含指定属性。
     *
     * @param startElement StartElement
     * @param attrName     属性名称
     * @return 如果包含该属性则返回 true
     */
    public static boolean hasAttribute(StartElement startElement, String attrName) {
        return startElement.getAttributeByName(new QName(attrName)) != null;
    }

    /**
     * 获取属性的整数值。
     *
     * @param startElement StartElement
     * @param attrName     属性名称
     * @param defaultValue 默认值
     * @return 属性的整数值，如果属性不存在或格式错误则返回默认值
     */
    public static int getIntAttribute(StartElement startElement, String attrName, int defaultValue) {
        Attribute attr = startElement.getAttributeByName(new QName(attrName));
        if (attr == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(attr.getValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取属性的布尔值。
     *
     * @param startElement StartElement
     * @param attrName     属性名称
     * @param defaultValue 默认值
     * @return 属性的布尔值，如果属性不存在则返回默认值
     */
    public static boolean getBooleanAttribute(StartElement startElement, String attrName, boolean defaultValue) {
        Attribute attr = startElement.getAttributeByName(new QName(attrName));
        if (attr == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(attr.getValue());
    }
}
