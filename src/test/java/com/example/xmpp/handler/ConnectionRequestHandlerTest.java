package com.example.xmpp.handler;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.ConnectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConnectionRequestHandler 单元测试。
 */
class ConnectionRequestHandlerTest {

    private ConnectionRequestHandler handler;
    private AtomicReference<ConnectionRequest> receivedRequest;

    @BeforeEach
    void setUp() {
        receivedRequest = new AtomicReference<>();
        handler = new ConnectionRequestHandler(receivedRequest::set);
    }

    @Test
    @DisplayName("构造函数应正确初始化")
    void testConstructor() {
        assertNotNull(handler);
        assertThrows(NullPointerException.class, () ->
                new ConnectionRequestHandler(null));
    }

    @Test
    @DisplayName("handleIqRequest 应解析 ConnectionRequest 并触发回调")
    void testHandleIqRequest() {
        String from = "acs@example.com";
        String username = "test-user";
        String password = "test-password";

        ConnectionRequest request = ConnectionRequest.builder()
                .username(username)
                .password(password)
                .build();

        Iq iqRequest = new Iq.Builder(Iq.Type.SET)
                .id("test-id")
                .from(from)
                .childElement(request)
                .build();

        Iq response = handler.handleIqRequest(iqRequest);

        assertNotNull(response);
        assertEquals(Iq.Type.RESULT, response.getType());
        assertEquals("test-id", response.getId());
        assertEquals(from, response.getTo());

        ConnectionRequest received = receivedRequest.get();
        assertNotNull(received);
        assertEquals(username, received.getUsername());
        assertEquals(password, received.getPassword());
    }

    @Test
    @DisplayName("handleIqRequest 应返回 IQ result 类型")
    void testHandleIqRequestReturnsResult() {
        ConnectionRequest request = ConnectionRequest.builder()
                .username("user")
                .password("pass")
                .build();

        Iq iqRequest = new Iq.Builder(Iq.Type.SET)
                .id("test-id")
                .from("acs@example.com")
                .childElement(request)
                .build();

        Iq response = handler.handleIqRequest(iqRequest);

        assertNotNull(response);
        assertEquals(Iq.Type.RESULT, response.getType());
        assertEquals("test-id", response.getId());
    }

    @Test
    @DisplayName("handleIqRequest 无 ConnectionRequest 时不应触发回调")
    void testHandleIqRequestWithoutConnectionRequest() {
        Iq iqRequest = new Iq.Builder(Iq.Type.SET)
                .id("test-id")
                .from("acs@example.com")
                .build();

        Iq response = handler.handleIqRequest(iqRequest);

        assertNotNull(response);
        assertEquals(Iq.Type.RESULT, response.getType());
        assertNull(receivedRequest.get());
    }

    @Test
    @DisplayName("handleIqRequest 回调异常不应传播")
    void testHandleIqRequestCallbackException() {
        handler = new ConnectionRequestHandler(req -> {
            throw new RuntimeException("Callback error");
        });

        ConnectionRequest request = ConnectionRequest.builder()
                .username("user")
                .password("pass")
                .build();

        Iq iqRequest = new Iq.Builder(Iq.Type.SET)
                .id("test-id")
                .childElement(request)
                .build();

        assertDoesNotThrow(() -> handler.handleIqRequest(iqRequest));
    }

    @Test
    @DisplayName("getElement 应返回 connectionRequest")
    void testGetElement() {
        assertEquals("connectionRequest", handler.getElement());
    }

    @Test
    @DisplayName("getNamespace 应返回正确命名空间")
    void testGetNamespace() {
        assertEquals(ConnectionRequest.NAMESPACE, handler.getNamespace());
    }

    @Test
    @DisplayName("getIqType 应返回 SET")
    void testGetIqType() {
        assertEquals(Iq.Type.SET, handler.getIqType());
    }

    @Test
    @DisplayName("getMode 应返回 SYNC")
    void testGetMode() {
        assertEquals(IqRequestHandler.Mode.SYNC, handler.getMode());
    }

    @Test
    @DisplayName("handleIqRequest 应处理带扩展的 ConnectionRequest")
    void testHandleIqRequestWithExtensions() {
        ConnectionRequest request = ConnectionRequest.builder()
                .username("user")
                .password("pass")
                .build();

        Iq iqRequest = new Iq.Builder(Iq.Type.SET)
                .id("test-id")
                .from("acs@example.com")
                .addExtension(request)
                .build();

        Iq response = handler.handleIqRequest(iqRequest);

        assertNotNull(response);
        assertEquals(Iq.Type.RESULT, response.getType());
        assertNotNull(receivedRequest.get());
    }
}
