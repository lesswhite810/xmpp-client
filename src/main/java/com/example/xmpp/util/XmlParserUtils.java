package com.example.xmpp.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/**
 * XML 解析器工具类。
 *
 * @since 2026-02-09
 */
@Slf4j
@UtilityClass
public class XmlParserUtils {

    /**
     * 共享的安全 XML 输入工厂。
     */
    private static final XMLInputFactory SHARED_INPUT_FACTORY = createInputFactoryInternal();

    /**
     * 安全的 XML 解析器。
     *
     * @return null，表示拒绝解析外部实体
     */
    private static final XMLResolver SECURE_XML_RESOLVER = (publicID, systemID, baseURI, namespace) -> {
        log.warn("Blocked external entity reference");
        return null;
    };

    /**
     * 创建配置好的 XMLInputFactory 实例。
     *
     * @return 已启用安全选项的 XMLInputFactory 实例
     */
    private static XMLInputFactory createInputFactoryInternal() {
        XMLInputFactory factory = XMLInputFactory.newInstance();

        factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setXMLResolver(SECURE_XML_RESOLVER);

        setPropertyIfSupported(factory, "com.ctc.wstx.enableTDs", Boolean.FALSE);
        setPropertyIfSupported(factory, "javax.xml.stream.supportDTD", Boolean.FALSE);

        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);

        log.debug("XMLInputFactory created with XXE protection enabled");
        return factory;
    }

    /**
     * 尝试设置属性，如果不支持则忽略。
     *
     * @param factory XML 输入工厂
     * @param name 属性名称
     * @param value 属性值
     */
    private static void setPropertyIfSupported(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
            log.debug("Set XML parser property: {} = {}", name, value);
        } catch (Exception e) {
            log.trace("XML parser property {} not supported - ErrorType: {}", name, e.getClass().getSimpleName());
        }
    }

    /**
     * 获取共享的 XMLInputFactory 实例。
     *
     * @return 共享的 XMLInputFactory 实例
     */
    public static XMLInputFactory getSharedInputFactory() {
        return SHARED_INPUT_FACTORY;
    }

    /**
     * 创建新的 XMLInputFactory 实例。
     *
     * @return 新的 XMLInputFactory 实例
     */
    public static XMLInputFactory createInputFactory() {
        return createInputFactoryInternal();
    }

    /**
     * 从字节数组创建 XMLEventReader。
     *
     * @param bytes 字节数组
     * @return 新创建的 XMLEventReader 实例
     * @throws XMLStreamException 如果创建 reader 失败
     */
    public static XMLEventReader createReader(byte[] bytes) throws XMLStreamException {
        return SHARED_INPUT_FACTORY.createXMLEventReader(new ByteArrayInputStream(bytes));
    }

    /**
     * 获取开始元素的所有属性。
     *
     * @param startElement 开始元素
     * @return 属性映射，键为属性名，值为属性值
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
     * 获取元素的文本内容。
     *
     * @param reader XMLEventReader
     * @return 去除首尾空白后的文本内容
     * @throws XMLStreamException 如果读取失败
     */
    public static String getElementText(XMLEventReader reader) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            var event = reader.peek();
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
}
