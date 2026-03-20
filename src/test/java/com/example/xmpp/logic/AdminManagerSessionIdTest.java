package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.exception.XmppNetworkException;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测试 extractSessionId 方法是否能正确从 GenericExtensionElement 中提取 sessionid。
 */
class AdminManagerSessionIdTest {

    private static final Logger log = LoggerFactory.getLogger(AdminManagerSessionIdTest.class);

    @Test
    void testExtractSessionIdMethodReturnsOptionalEmptyWhenMissing() throws Exception {
        AdminManager manager = new AdminManager(mock(XmppConnection.class), "admin", "example.com");
        Iq responseIq = new Iq.Builder(Iq.Type.RESULT)
                .id("missing-session-id")
                .build();

        Method method = AdminManager.class.getDeclaredMethod("extractSessionId", XmppStanza.class);
        method.setAccessible(true);
        Object result = method.invoke(manager, responseIq);

        assertInstanceOf(Optional.class, result);
        assertTrue(((Optional<?>) result).isEmpty());
    }

    @Test
    void testExtractSessionIdFromGenericExtensionElement() {
        // 模拟服务器返回的 command 元素
        GenericExtensionElement commandElement = GenericExtensionElement.builder("command", "http://jabber.org/protocol/commands")
                .addAttribute("sessionid", "XZdTcQXoGcRG5GI")
                .addAttribute("node", "http://jabber.org/protocol/admin#add-user")
                .addAttribute("status", "executing")
                .build();

        // 创建 IQ 娡拟响应
        Iq responseIq = new Iq.Builder(Iq.Type.RESULT)
                .id("add-execute-1772898465140")
                .from("lesswhite")
                .to("admin@lesswhite/xmpp")
                .childElement(commandElement)
                .build();

        // 验证 childElement 是 GenericExtensionElement
        assertTrue(responseIq.getChildElement() instanceof GenericExtensionElement);

        // 验证可以获取 sessionid
        GenericExtensionElement child = (GenericExtensionElement) responseIq.getChildElement();
        String sessionId = child.getAttributeValue("sessionid");

        assertEquals("XZdTcQXoGcRG5GI", sessionId);
        log.info("Successfully extracted sessionId: {}", sessionId);
    }

    @Test
    void testExtractSessionIdFromXml() {
        // 模拟一个没有正确设置 childElement 的 IQ（使用 toXml 回退）
        GenericExtensionElement commandElement = GenericExtensionElement.builder("command", "http://jabber.org/protocol/commands")
                .addAttribute("sessionid", "test-session-123")
                .build();

        Iq responseIq = new Iq.Builder(Iq.Type.RESULT)
                .id("test-1")
                .childElement(commandElement)
                .build();

        // 验证 toXml 包含 sessionid
        String xml = responseIq.toXml();
        log.info("Generated XML: {}", xml);
        assertTrue(xml.contains("sessionid=\"test-session-123\""));
    }

    @Test
    void testSendAdminCommandFailsImmediatelyWhenIqDispatchFails() throws Exception {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq requestIq = new Iq.Builder(Iq.Type.SET)
                .id("admin-cmd-1")
                .to("example.com")
                .build();

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.failedFuture(new XmppNetworkException("Channel is not active")));

        Method method = AdminManager.class.getDeclaredMethod("sendAdminCommand", Iq.class);
        method.setAccessible(true);
        Object invocationResult = method.invoke(manager, requestIq);
        assertInstanceOf(CompletableFuture.class, invocationResult);
        CompletableFuture<?> result = (CompletableFuture<?>) invocationResult;

        CompletionException exception = assertThrows(CompletionException.class, result::join);
        assertInstanceOf(XmppNetworkException.class, exception.getCause());
    }

    @Test
    void testSendAdminCommandUsesConfiguredTimeout() throws Exception {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 4500);
        Iq requestIq = new Iq.Builder(Iq.Type.SET)
                .id("admin-cmd-timeout")
                .to("example.com")
                .build();
        CompletableFuture<XmppStanza> expectedFuture = CompletableFuture.completedFuture(requestIq);

        when(connection.sendIqPacketAsync(any(Iq.class), eq(4500L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(expectedFuture);

        Method method = AdminManager.class.getDeclaredMethod("sendAdminCommand", Iq.class);
        method.setAccessible(true);
        Object invocationResult = method.invoke(manager, requestIq);

        assertSame(expectedFuture, invocationResult);
        verify(connection).sendIqPacketAsync(requestIq, 4500L, TimeUnit.MILLISECONDS);
    }

    @Test
    void testAddUserDoesNotEnterSubmitPhaseWhenExecutePhaseFails() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.failedFuture(new XmppNetworkException("execute phase failed")));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> manager.addUser("u1", "p1").join());

        assertInstanceOf(XmppNetworkException.class, exception.getCause());
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testAddUserDoesNotEnterSubmitPhaseWhenExecuteResponseIsIqError() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq executeError = Iq.createErrorResponse(
                new Iq.Builder(Iq.Type.SET).id("exec-error").to("example.com").build(),
                new XmppError.Builder(XmppError.Condition.SERVICE_UNAVAILABLE).build());

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(executeError));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> manager.addUser("u1", "p1").join());

        assertInstanceOf(AdminCommandException.class, exception.getCause());
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testAddUserStopsAfterCompletedExecuteResponse() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        GenericExtensionElement completedCommand = GenericExtensionElement.builder(
                        "command", "http://jabber.org/protocol/commands")
                .addAttribute("status", "completed")
                .build();
        Iq completedResponse = new Iq.Builder(Iq.Type.RESULT)
                .id("exec-completed")
                .to("example.com")
                .childElement(completedCommand)
                .build();

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(completedResponse));

        assertSame(completedResponse, manager.addUser("u1", "p1").join());
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testAddUserFailsWhenExecuteResponseHasNoSessionId() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        GenericExtensionElement executingCommand = GenericExtensionElement.builder(
                        "command", "http://jabber.org/protocol/commands")
                .addAttribute("status", "executing")
                .build();
        Iq executeResponse = new Iq.Builder(Iq.Type.RESULT)
                .id("exec-no-session")
                .to("example.com")
                .childElement(executingCommand)
                .build();

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(executeResponse));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> manager.addUser("u1", "p1").join());

        assertInstanceOf(AdminCommandException.class, exception.getCause());
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testAddUserFailsWhenSubmitPhaseReturnsIqError() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        GenericExtensionElement executingCommand = GenericExtensionElement.builder(
                        "command", "http://jabber.org/protocol/commands")
                .addAttribute("status", "executing")
                .addAttribute("sessionid", "session-1")
                .build();
        Iq executeResponse = new Iq.Builder(Iq.Type.RESULT)
                .id("exec-ok")
                .to("example.com")
                .childElement(executingCommand)
                .build();
        Iq submitError = Iq.createErrorResponse(
                new Iq.Builder(Iq.Type.SET).id("submit-error").to("example.com").build(),
                new XmppError.Builder(XmppError.Condition.SERVICE_UNAVAILABLE).build());

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(executeResponse))
                .thenReturn(CompletableFuture.completedFuture(submitError));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> manager.addUser("u1", "p1").join());

        assertInstanceOf(AdminCommandException.class, exception.getCause());
        verify(connection, times(2)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

}
