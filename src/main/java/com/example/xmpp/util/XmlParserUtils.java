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
 * <p>提供 XML 解析器配置和辅助方法。</p>
 *
 * <h2>安全配置</h2>
 * <p>本类实现了完整的 XXE（XML External Entity）防护：</p>
 * <ul>
 *   <li>禁用外部实体解析（IS_SUPPORTING_EXTERNAL_ENTITIES）</li>
 *   <li>禁用 DTD 处理（SUPPORT_DTD）</li>
 *   <li>禁用验证（IS_VALIDATING）</li>
 *   <li>设置安全解析器拒绝所有外部引用</li>
 * </ul>
 *
 * @since 2026-02-09
 */
@Slf4j
@UtilityClass
public class XmlParserUtils {

    /** 共享的 XMLInputFactory 实例（线程安全） */
    private static final XMLInputFactory SHARED_INPUT_FACTORY = createInputFactoryInternal();

    /**
     * 安全的 XML 解析器，拒绝所有外部实体引用。
     */
    private static final XMLResolver SECURE_XML_RESOLVER = (publicID, systemID, baseURI, namespace) -> {
        log.warn("Blocked external entity reference - publicID: {}, systemID: {}, baseURI: {}",
                publicID, systemID, baseURI);
        return null;
    };

    /**
     * 创建配置好的 XMLInputFactory 实例。
     */
    private static XMLInputFactory createInputFactoryInternal() {
        XMLInputFactory factory = XMLInputFactory.newInstance();

        // 安全配置（XXE 防护）
        factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setXMLResolver(SECURE_XML_RESOLVER);

        // Woodstox 特定属性
        setPropertyIfSupported(factory, "com.ctc.wstx.enableTDs", Boolean.FALSE);
        setPropertyIfSupported(factory, "javax.xml.stream.supportDTD", Boolean.FALSE);

        // 功能配置
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);

        log.debug("XMLInputFactory created with XXE protection enabled");
        return factory;
    }

    /**
     * 尝试设置属性，如果不支持则忽略。
     */
    private static void setPropertyIfSupported(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
            log.debug("Set XML parser property: {} = {}", name, value);
        } catch (Exception e) {
            log.trace("XML parser property {} not supported: {}", name, e.getMessage());
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
     * @return XMLEventReader
     * @throws XMLStreamException 如果创建 reader 失败
     */
    public static XMLEventReader createReader(byte[] bytes) throws XMLStreamException {
        return SHARED_INPUT_FACTORY.createXMLEventReader(new ByteArrayInputStream(bytes));
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
     * 获取元素的文本内容。
     *
     * @param reader XMLEventReader
     * @return 文本内容
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
