package com.example.xmpp.protocol.model.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StreamError} 单元测试。
 */
class StreamErrorTest {

    @Test
    @DisplayName("Condition.fromString 传入 null 时应返回 UNDEFINED_CONDITION")
    void testConditionFromStringWithNull() {
        assertEquals(StreamError.Condition.UNDEFINED_CONDITION, StreamError.Condition.fromString(null));
    }

    @Test
    @DisplayName("StreamError 应使用 RFC 6120 定义的 stream errors 命名空间")
    void testStreamErrorShouldUseRfc6120Namespace() {
        StreamError streamError = StreamError.builder()
                .condition(StreamError.Condition.NOT_AUTHORIZED)
                .text("Authentication required")
                .by("example.com")
                .build();

        assertEquals("error", StreamError.ELEMENT);
        assertEquals(StreamError.ELEMENT, streamError.getElementName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-streams", StreamError.NAMESPACE);
        assertEquals(StreamError.NAMESPACE, streamError.getNamespace());
        assertEquals("<stream:error><not-authorized xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\"/>"
                        + "<text xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\">Authentication required</text>"
                        + "<by xmlns=\"urn:ietf:params:xml:ns:xmpp-streams\">example.com</by></stream:error>",
                streamError.toXml());
    }

    @Test
    @DisplayName("StreamError 源码应保留本地 stream errors 命名空间常量")
    void testStreamErrorSourceShouldKeepLocalNamespaceConstant() throws IOException {
        Path sourcePath = Path.of("src/main/java/com/example/xmpp/protocol/model/stream/StreamError.java");
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);

        assertFalse(source.contains("XmppConstants.NS_XMPP_STREAM_FEATURES"));
        assertFalse(source.contains("XmppConstants.NS_XMPP_STREAMS"));
        assertTrue(source.contains("public static final String NAMESPACE = \"urn:ietf:params:xml:ns:xmpp-streams\";"));
    }
}
