package com.example.xmpp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
