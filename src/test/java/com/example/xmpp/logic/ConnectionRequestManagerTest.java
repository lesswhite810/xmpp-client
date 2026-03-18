package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConnectionRequestManager 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ConnectionRequestManagerTest {

    @Mock
    private XmppConnection connection;

    private ConnectionRequestManager manager;

    @BeforeEach
    void setUp() {
        manager = new ConnectionRequestManager(connection);
    }

    @Test
    @DisplayName("构造函数应正确初始化")
    void testConstructor() {
        assertNotNull(manager);
        assertNotNull(new ConnectionRequestManager(connection, 5000));
        assertNotNull(new ConnectionRequestManager(connection, 5000, 3000));
    }

    @Test
    @DisplayName("sendConnectionRequest 应发送正确的 IQ")
    void testSendConnectionRequest() {
        String cpeJid = "cpe@example.com";
        String username = "test-user";
        String password = "test-password";

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(XmppStanza.class)));

        manager.sendConnectionRequest(cpeJid, username, password);

        ArgumentCaptor<Iq> iqCaptor = ArgumentCaptor.forClass(Iq.class);
        verify(connection).sendIqPacketAsync(iqCaptor.capture(), eq(30000L), eq(TimeUnit.MILLISECONDS));

        Iq sentIq = iqCaptor.getValue();
        assertEquals(Iq.Type.SET, sentIq.getType());
        assertEquals(cpeJid, sentIq.getTo());
        assertNotNull(sentIq.getChildElement());
        assertTrue(sentIq.getChildElement().getElementName().equals("connectionRequest"));
    }

    @Test
    @DisplayName("sendConnectionRequest null JID 应抛出异常")
    void testSendConnectionRequestWithNullJid() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.sendConnectionRequest(null, "user", "pass"));
    }

    @Test
    @DisplayName("sendConnectionRequest 空 JID 应抛出异常")
    void testSendConnectionRequestWithBlankJid() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.sendConnectionRequest("  ", "user", "pass"));
    }

    @Test
    @DisplayName("sendConnectionRequest null username 应抛出异常")
    void testSendConnectionRequestWithNullUsername() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.sendConnectionRequest("cpe@example.com", null, "pass"));
    }

    @Test
    @DisplayName("sendConnectionRequest null password 应抛出异常")
    void testSendConnectionRequestWithNullPassword() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.sendConnectionRequest("cpe@example.com", "user", null));
    }

    @Test
    @DisplayName("发送失败时应正确处理异常")
    void testSendConnectionRequestFailure() {
        String cpeJid = "cpe@example.com";
        CompletableFuture<XmppStanza> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Network error"));

        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(failedFuture);

        manager.sendConnectionRequest(cpeJid, "user", "pass")
                .whenComplete((response, error) -> {
                    assertNotNull(error);
                    assertTrue(error.getMessage().contains("Network error"));
                });
    }

    @Test
    @DisplayName("未连接时应返回失败Future")
    void testSendConnectionRequestWhenDisconnected() {
        String cpeJid = "cpe@example.com";

        when(connection.isConnected()).thenReturn(false);

        manager.sendConnectionRequest(cpeJid, "user", "pass")
                .whenComplete((response, error) -> {
                    assertNotNull(error);
                    assertTrue(error.getCause() instanceof java.net.ConnectException);
                });

        verify(connection, never()).sendIqPacketAsync(any(), anyLong(), any());
    }

    @Test
    @DisplayName("IQ ID 应以 connreq- 为前缀")
    void testIqIdPrefix() {
        when(connection.isConnected()).thenReturn(true);
        when(connection.sendIqPacketAsync(any(Iq.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(XmppStanza.class)));

        manager.sendConnectionRequest("cpe@example.com", "user", "pass");

        ArgumentCaptor<Iq> iqCaptor = ArgumentCaptor.forClass(Iq.class);
        verify(connection).sendIqPacketAsync(iqCaptor.capture(), anyLong(), any(TimeUnit.class));

        String iqId = iqCaptor.getValue().getId();
        assertNotNull(iqId);
        assertTrue(iqId.startsWith("connreq-"), "IQ ID should start with 'connreq-'");
    }

    @Test
    @DisplayName("getTimeoutMs 应返回配置的超时时间")
    void testGetTimeoutMs() {
        ConnectionRequestManager customManager = new ConnectionRequestManager(connection, 10000);
        assertEquals(10000, customManager.getTimeoutMs());
    }

    @Test
    @DisplayName("getRetryDelayMs 应返回配置的重连等待时间")
    void testGetRetryDelayMs() {
        ConnectionRequestManager customManager = new ConnectionRequestManager(connection, 30000, 8000);
        assertEquals(8000, customManager.getRetryDelayMs());
    }
}
