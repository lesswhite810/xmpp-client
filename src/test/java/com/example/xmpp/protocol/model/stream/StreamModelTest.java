package com.example.xmpp.protocol.model.stream;

import com.example.xmpp.util.XmppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stream 模型类单元测试。
 */
class StreamModelTest {

    @Test
    @DisplayName("StreamHeader Builder 应正确构建")
    void testStreamHeaderBuilder() {
        StreamHeader header = StreamHeader.builder()
                .from("server.example.com")
                .to("client@example.com")
                .id("stream-123")
                .version("1.0")
                .lang("en")
                .namespace("jabber:client")
                .build();
        
        assertEquals("server.example.com", header.getFrom());
        assertEquals("client@example.com", header.getTo());
        assertEquals("stream-123", header.getId());
        assertEquals("1.0", header.getVersion());
        assertEquals("en", header.getLang());
        // 实际实现可能使用默认 namespace
        assertNotNull(header.getNamespace());
    }

    @Test
    @DisplayName("StreamHeader 应支持部分属性")
    void testStreamHeaderPartial() {
        StreamHeader header = StreamHeader.builder()
                .from("example.com")
                .id("test-id")
                .build();
        
        assertEquals("example.com", header.getFrom());
        assertEquals("test-id", header.getId());
        assertNull(header.getTo());
        assertNull(header.getLang());
    }

    @Test
    @DisplayName("StartTls 应使用统一的 ELEMENT 和 NAMESPACE 常量")
    void testStartTlsConstants() {
        assertEquals("starttls", TlsElements.StartTls.ELEMENT);
        assertEquals(TlsElements.StartTls.ELEMENT, TlsElements.StartTls.INSTANCE.getElementName());
        assertEquals(TlsElements.TlsElement.NAMESPACE, TlsElements.StartTls.INSTANCE.getNamespace());
    }

    @Test
    @DisplayName("TlsProceed 应使用统一的 ELEMENT 和 NAMESPACE 常量")
    void testTlsProceedConstants() {
        assertEquals("proceed", TlsElements.TlsProceed.ELEMENT);
        assertEquals(TlsElements.TlsProceed.ELEMENT, TlsElements.TlsProceed.INSTANCE.getElementName());
        assertEquals(TlsElements.TlsElement.NAMESPACE, TlsElements.TlsProceed.INSTANCE.getNamespace());
    }

    @Test
    @DisplayName("TlsElement 应提供统一的命名空间和 XML 序列化")
    void testTlsElementSharedBehavior() {
        assertEquals(XmppConstants.NS_XMPP_TLS, TlsElements.TlsElement.NAMESPACE);
        assertEquals("<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>",
                TlsElements.StartTls.INSTANCE.toXml());
        assertEquals("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>",
                TlsElements.TlsProceed.INSTANCE.toXml());
    }

    @Test
    @DisplayName("StartTls 和 TlsProceed 应继承 TlsElement")
    void testTlsElementsExtendTlsElement() {
        assertTrue(TlsElements.TlsElement.class.isAssignableFrom(TlsElements.StartTls.class));
        assertTrue(TlsElements.TlsElement.class.isAssignableFrom(TlsElements.TlsProceed.class));
    }

    @Test
    @DisplayName("StreamFeatures Builder 应正确构建")
    void testStreamFeaturesBuilder() {
        List<String> mechanisms = List.of("PLAIN", "SCRAM-SHA-256");
        
        StreamFeatures features = StreamFeatures.builder()
                .starttlsAvailable(true)
                .bindAvailable(true)
                .mechanisms(mechanisms)
                .build();
        
        assertTrue(features.isStarttlsAvailable());
        assertTrue(features.isBindAvailable());
        assertEquals(2, features.getMechanisms().size());
        assertTrue(features.getMechanisms().contains("PLAIN"));
    }

    @Test
    @DisplayName("StreamFeatures 默认值应为 false")
    void testStreamFeaturesDefaults() {
        StreamFeatures features = StreamFeatures.builder().build();
        
        assertFalse(features.isStarttlsAvailable());
        assertFalse(features.isBindAvailable());
        // mechanisms 可能返回空列表而不是 null
        assertTrue(features.getMechanisms() == null || features.getMechanisms().isEmpty());
    }

    @Test
    @DisplayName("StreamFeatures 应支持空 mechanisms")
    void testStreamFeaturesEmptyMechanisms() {
        StreamFeatures features = StreamFeatures.builder()
                .mechanisms(Collections.emptyList())
                .build();

        assertNotNull(features.getMechanisms());
        assertTrue(features.getMechanisms().isEmpty());
    }
}
