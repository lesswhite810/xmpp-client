package com.example.xmpp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GenericExtensionElement} 单元测试。
 */
class GenericExtensionElementTest {

    @Test
    @DisplayName("getFirstChild 应返回匹配的子元素")
    void testGetFirstChild() {
        GenericExtensionElement child = GenericExtensionElement.builder("item", "urn:test")
                .text("value")
                .build();
        GenericExtensionElement parent = GenericExtensionElement.builder("query", "urn:test")
                .addChild(child)
                .build();

        assertTrue(parent.getFirstChild("item").isPresent());
        assertEquals(child, parent.getFirstChild("item").orElseThrow());
    }

    @Test
    @DisplayName("getFirstChild 未命中时应返回 Optional.empty")
    void testGetFirstChildWhenMissing() {
        GenericExtensionElement parent = GenericExtensionElement.builder("query", "urn:test")
                .build();

        assertTrue(parent.getFirstChild("item").isEmpty());
        assertTrue(parent.getFirstChild("item", "urn:test").isEmpty());
    }

    @Test
    @DisplayName("getFirstChild 按名称和命名空间匹配")
    void testGetFirstChildByNamespace() {
        GenericExtensionElement child = GenericExtensionElement.builder("item", "urn:test:child")
                .text("value")
                .build();
        GenericExtensionElement parent = GenericExtensionElement.builder("query", "urn:test")
                .addChild(child)
                .build();

        assertTrue(parent.getFirstChild("item", "urn:test:child").isPresent());
        assertTrue(parent.getFirstChild("item", "urn:test:other").isEmpty());
    }

    @Test
    @DisplayName("空元素序列化时应输出自闭合标签并转义属性")
    void testToXmlForEmptyElement() {
        GenericExtensionElement element = GenericExtensionElement.builder("x", "urn:test")
                .addAttribute("quote", "\"value\"")
                .addAttribute("nullable", null)
                .build();

        String xml = element.toXml();

        assertEquals("<x xmlns=\"urn:test\" quote=\"&quot;value&quot;\" nullable=\"\"/>", xml);
    }

    @Test
    @DisplayName("带文本和子元素时应输出完整 XML")
    void testToXmlWithTextAndChildren() {
        GenericExtensionElement child = GenericExtensionElement.builder("item", "urn:test:child")
                .text("child<&>")
                .build();
        GenericExtensionElement element = GenericExtensionElement.builder("query", "urn:test")
                .addAttribute("id", "q1")
                .text("root&text")
                .addChild(child)
                .build();

        String xml = element.toXml();

        assertTrue(xml.startsWith("<query xmlns=\"urn:test\" id=\"q1\">"));
        assertTrue(xml.contains("root&amp;text"));
        assertTrue(xml.contains("<item xmlns=\"urn:test:child\">child&lt;&amp;&gt;</item>"));
        assertTrue(xml.endsWith("</query>"));
    }

    @Test
    @DisplayName("Builder 应忽略空属性映射并返回不可变集合")
    void testBuilderHandlesEmptyAttributesAndReturnsUnmodifiableCollections() {
        GenericExtensionElement child = GenericExtensionElement.builder("item", "urn:test").build();
        GenericExtensionElement element = GenericExtensionElement.builder("query", "urn:test")
                .addAttributes(null)
                .addAttributes(Map.of())
                .addChild(child)
                .build();

        assertTrue(element.getAttributes().isEmpty());
        assertEquals(1, element.getChildren().size());
        assertThrows(UnsupportedOperationException.class,
                () -> element.getAttributes().put("k", "v"));
        assertThrows(UnsupportedOperationException.class,
                () -> element.getChildren().add(child));
    }

    @Test
    @DisplayName("构造时 namespace 为 null 应转为空字符串")
    void testNamespaceDefaultsToEmptyString() {
        GenericExtensionElement element = GenericExtensionElement.builder("x", null).build();

        assertEquals("", element.getNamespace());
        assertEquals("<x/>", element.toXml());
    }

    @Test
    @DisplayName("QName 和 toString 应返回基础描述信息")
    void testGetQNameAndToString() {
        GenericExtensionElement element = GenericExtensionElement.builder("item", "urn:test")
                .text("value")
                .build();

        QName qName = element.getQName();

        assertEquals(new QName("urn:test", "item"), qName);
        assertTrue(element.toString().contains("elementName='item'"));
        assertTrue(element.toString().contains("namespace='urn:test'"));
        assertFalse(element.toString().isBlank());
    }
}
