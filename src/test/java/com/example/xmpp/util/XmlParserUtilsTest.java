package com.example.xmpp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.StartElement;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmlParserUtils 单元测试。
 *
 * @since 2026-03-09
 */
class XmlParserUtilsTest {

    @Test
    @DisplayName("getSharedInputFactory 应返回非空实例")
    void testGetSharedInputFactory() {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        assertNotNull(factory, "Factory should not be null");
    }

    @Test
    @DisplayName("getSharedInputFactory 应返回同一实例")
    void testGetSharedInputFactorySingleton() {
        XMLInputFactory factory1 = XmlParserUtils.getSharedInputFactory();
        XMLInputFactory factory2 = XmlParserUtils.getSharedInputFactory();
        assertSame(factory1, factory2, "Should return same instance");
    }

    @Test
    @DisplayName("createInputFactory 应返回新实例")
    void testCreateInputFactory() {
        XMLInputFactory factory1 = XmlParserUtils.createInputFactory();
        XMLInputFactory factory2 = XmlParserUtils.createInputFactory();
        assertNotSame(factory1, factory2, "Should return new instance");
    }

    @Test
    @DisplayName("工厂应禁用验证")
    void testValidationDisabled() {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        assertFalse((Boolean) factory.getProperty(XMLInputFactory.IS_VALIDATING),
                "Validation should be disabled");
    }

    @Test
    @DisplayName("工厂应禁用外部实体")
    void testExternalEntitiesDisabled() {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        assertFalse((Boolean) factory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES),
                "External entities should be disabled");
    }

    @Test
    @DisplayName("工厂应禁用 DTD")
    void testDtdDisabled() {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        assertFalse((Boolean) factory.getProperty(XMLInputFactory.SUPPORT_DTD),
                "DTD should be disabled");
    }

    @Test
    @DisplayName("工厂应启用命名空间支持")
    void testNamespaceAwareEnabled() {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        assertTrue((Boolean) factory.getProperty(XMLInputFactory.IS_NAMESPACE_AWARE),
                "Namespace awareness should be enabled");
    }

    @Test
    @DisplayName("工厂应启用文本合并")
    void testCoalescingEnabled() {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        assertTrue((Boolean) factory.getProperty(XMLInputFactory.IS_COALESCING),
                "Coalescing should be enabled");
    }

    @Test
    @DisplayName("应能解析简单 XML")
    void testParseSimpleXml() throws XMLStreamException {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        String xml = "<?xml version='1.0'?><root><child>text</child></root>";
        
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        XMLStreamReader reader = factory.createXMLStreamReader(is);
        
        // 遍历到 child 元素
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("child".equals(reader.getLocalName())) {
                    assertEquals("text", reader.getElementText());
                    break;
                }
            }
        }
        
        reader.close();
    }

    @Test
    @DisplayName("应能处理命名空间")
    void testParseNamespacedXml() throws XMLStreamException {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        String xml = "<?xml version='1.0'?><root xmlns='http://example.com'><child>text</child></root>";
        
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        XMLStreamReader reader = factory.createXMLStreamReader(is);
        
        // 遍历到 root 元素
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("root".equals(reader.getLocalName())) {
                    assertEquals("http://example.com", reader.getNamespaceURI());
                    break;
                }
            }
        }
        
        reader.close();
    }

    @Test
    @DisplayName("XXE 外部实体应被阻止")
    void testXxeExternalEntityBlocked() {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        // 尝试包含外部实体的 XML
        String xml = "<?xml version='1.0'?>" +
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
                "<root>&xxe;</root>";
        
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        
        // DTD 被禁用，应该抛出异常阻止解析
        assertThrows(Exception.class, () -> {
            XMLStreamReader reader = factory.createXMLStreamReader(is);
            try {
                while (reader.hasNext()) {
                    reader.next();
                }
            } finally {
                reader.close();
            }
        }, "XXE attack should be blocked by DTD disabling");
    }

    @Test
    @DisplayName("应处理空 XML")
    void testParseEmptyContent() throws XMLStreamException {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        String xml = "<?xml version='1.0'?><root></root>";
        
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        XMLStreamReader reader = factory.createXMLStreamReader(is);
        
        boolean foundRoot = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("root".equals(reader.getLocalName())) {
                    foundRoot = true;
                }
            }
        }
        
        assertTrue(foundRoot, "Should find root element");
        reader.close();
    }

    @Test
    @DisplayName("应处理属性")
    void testParseAttributes() throws XMLStreamException {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        String xml = "<?xml version='1.0'?><root attr1='value1' attr2='value2'/>";
        
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        XMLStreamReader reader = factory.createXMLStreamReader(is);
        
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("root".equals(reader.getLocalName())) {
                    assertEquals("value1", reader.getAttributeValue(null, "attr1"));
                    assertEquals("value2", reader.getAttributeValue(null, "attr2"));
                    break;
                }
            }
        }
        
        reader.close();
    }

    @Test
    @DisplayName("getAttributes 应保留不同命名空间下的同名属性")
    void testGetAttributesPreservesNamespacedAttributes() throws XMLStreamException {
        XMLEventReader reader = XmlParserUtils.createInputFactory()
                .createXMLEventReader(new StringReader(
                        "<root xmlns:a='urn:test:a' xmlns:b='urn:test:b' id='root' a:id='first' b:id='second'/>"));
        StartElement startElement = null;
        while (reader.hasNext()) {
            var event = reader.nextEvent();
            if (event.isStartElement()) {
                startElement = event.asStartElement();
                break;
            }
        }

        assertNotNull(startElement);

        Map<QName, String> attributes = XmlParserUtils.getAttributes(startElement);

        assertEquals(3, attributes.size());
        assertEquals("root", attributes.get(new QName("id")));
        assertEquals("first", attributes.get(new QName("urn:test:a", "id", "a")));
        assertEquals("second", attributes.get(new QName("urn:test:b", "id", "b")));
        reader.close();
    }

    @Test
    @DisplayName("getElementText 应消费结束标签并推进到下一个事件")
    void testGetElementTextConsumesMatchingEndElement() throws XMLStreamException {
        XMLEventReader reader = XmlParserUtils.createInputFactory()
                .createXMLEventReader(new StringReader("<root><child>text</child><next>value</next></root>"));

        while (reader.hasNext()) {
            var event = reader.nextEvent();
            if (!event.isStartElement()) {
                continue;
            }
            if (!"child".equals(event.asStartElement().getName().getLocalPart())) {
                continue;
            }

            assertEquals("text", XmlParserUtils.getElementText(reader));

            var nextEvent = reader.nextEvent();
            assertTrue(nextEvent.isStartElement());
            assertEquals("next", nextEvent.asStartElement().getName().getLocalPart());
            reader.close();
            return;
        }

        reader.close();
        fail("Should reach child start element");
    }

    @Test
    @DisplayName("应处理特殊字符")
    void testParseSpecialCharacters() throws XMLStreamException {
        XMLInputFactory factory = XmlParserUtils.getSharedInputFactory();
        String xml = "<?xml version='1.0'?><root>&lt;tag&gt; &amp; &quot;quote&quot;</root>";
        
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        XMLStreamReader reader = factory.createXMLStreamReader(is);
        
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS) {
                String text = reader.getText().trim();
                if (!text.isEmpty()) {
                    assertTrue(text.contains("<tag>"));
                    assertTrue(text.contains("&"));
                    assertTrue(text.contains("\"quote\""));
                    break;
                }
            }
        }
        
        reader.close();
    }
}
