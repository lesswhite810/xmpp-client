package com.example.xmpp.logic;

import com.example.xmpp.XmppConnection;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.XmppStanza;
import com.example.xmpp.protocol.model.XmppError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminManager 公共行为测试。
 */
class AdminManagerBehaviorTest {

    @Test
    @DisplayName("extractSessionId 回退到 XML 时应支持单引号属性")
    void testExtractSessionIdFallbackSupportsSingleQuotedAttribute() throws Exception {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq response = new Iq.Builder(Iq.Type.RESULT)
                .id("cmd-1")
                .childElement(new SingleQuotedCommandElement("single-quoted-session"))
                .build();

        Method method = AdminManager.class.getDeclaredMethod("extractSessionId", XmppStanza.class);
        method.setAccessible(true);
        Object result = method.invoke(manager, response);

        assertEquals(Optional.of("single-quoted-session"), result);
    }

    @Test
    @DisplayName("extractSessionIdFromXml 遇到格式错误属性时应返回空")
    void testExtractSessionIdFromXmlReturnsEmptyForMalformedAttribute() throws Exception {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);

        Method method = AdminManager.class.getDeclaredMethod("extractSessionIdFromXml", String.class);
        method.setAccessible(true);
        Object result = method.invoke(manager, "<command sessionid=test-session/>");

        assertEquals(Optional.empty(), result);
    }

    @Test
    @DisplayName("deleteUser 应将普通用户名补全为完整 JID")
    void testDeleteUserBuildsAccountJidFromUsername() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq executeResponse = completedCommandResponse("delete-session", "executing");
        Iq submitResponse = new Iq.Builder(Iq.Type.RESULT)
                .id("delete-submit")
                .to("example.com")
                .build();

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(executeResponse))
                .thenReturn(CompletableFuture.completedFuture(submitResponse));

        XmppStanza response = manager.deleteUser("cpe-001").join();

        assertSame(submitResponse, response);
        verify(connection, times(2)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
        assertTrue(capturedIqAt(connection, 1).toXml().contains("cpe-001@example.com"));
    }

    @Test
    @DisplayName("changePassword 应保留已有完整 JID")
    void testChangePasswordPreservesFullJid() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq executeResponse = completedCommandResponse("change-session", "executing");
        Iq submitResponse = new Iq.Builder(Iq.Type.RESULT)
                .id("change-submit")
                .to("example.com")
                .build();

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(executeResponse))
                .thenReturn(CompletableFuture.completedFuture(submitResponse));

        XmppStanza response = manager.changePassword("cpe-001@example.net", "new-secret").join();

        assertSame(submitResponse, response);
        assertTrue(capturedIqAt(connection, 1).toXml().contains("cpe-001@example.net"));
    }

    @Test
    @DisplayName("kickUser 应发送单阶段 IQ 到目标 JID")
    void testKickUserSendsSingleIqToTargetJid() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq response = new Iq.Builder(Iq.Type.RESULT)
                .id("kick-result")
                .to("admin@example.com")
                .build();

        when(connection.sendIqPacketAsync(any(Iq.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        XmppStanza result = manager.kickUser("cpe-001@example.com").join();

        assertSame(response, result);
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class));
        assertTrue(capturedIqAt(connection, 0).toXml().contains("cpe-001@example.com"));
    }

    @Test
    @DisplayName("两阶段命令在提交阶段返回错误时应抛出管理异常")
    void testDeleteUserFailsWhenSubmitResponseIsError() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq executeResponse = completedCommandResponse("delete-session", "executing");
        Iq submitError = Iq.createErrorResponse(
                new Iq.Builder(Iq.Type.SET).id("submit-error").to("example.com").build(),
                new XmppError.Builder(XmppError.Condition.SERVICE_UNAVAILABLE).build());

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(executeResponse))
                .thenReturn(CompletableFuture.completedFuture(submitError));

        CompletionException exception = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                () -> manager.deleteUser("cpe-001").join());

        assertInstanceOf(AdminCommandException.class, exception.getCause());
    }

    @Test
    @DisplayName("两阶段命令在 execute 阶段已完成时不应发送 submit")
    void testDeleteUserReturnsCompletedExecuteResponse() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq completedResponse = completedCommandResponse("delete-session", "completed");

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(completedResponse));

        XmppStanza response = manager.deleteUser("cpe-001").join();

        assertSame(completedResponse, response);
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("两阶段命令在 execute 阶段返回错误时应抛出管理异常")
    void testDeleteUserFailsWhenExecuteResponseIsError() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        Iq executeError = Iq.createErrorResponse(
                new Iq.Builder(Iq.Type.SET).id("execute-error").to("example.com").build(),
                new XmppError.Builder(XmppError.Condition.SERVICE_UNAVAILABLE).build());

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(executeError));

        CompletionException exception = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                () -> manager.deleteUser("cpe-001").join());

        assertInstanceOf(AdminCommandException.class, exception.getCause());
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("两阶段命令在 execute 响应缺少 sessionid 时应抛出管理异常")
    void testDeleteUserFailsWhenSessionIdMissing() {
        XmppConnection connection = mock(XmppConnection.class);
        AdminManager manager = new AdminManager(connection, "admin", "example.com", 3000);
        GenericExtensionElement commandElement = GenericExtensionElement.builder("command",
                        "http://jabber.org/protocol/commands")
                .addAttribute("status", "executing")
                .build();
        Iq executeResponse = new Iq.Builder(Iq.Type.RESULT)
                .id("execute-no-session")
                .to("example.com")
                .childElement(commandElement)
                .build();

        when(connection.sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(CompletableFuture.completedFuture(executeResponse));

        CompletionException exception = org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                () -> manager.deleteUser("cpe-001").join());

        assertInstanceOf(AdminCommandException.class, exception.getCause());
        verify(connection, times(1)).sendIqPacketAsync(any(Iq.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
    }

    private static Iq completedCommandResponse(String sessionId, String status) {
        GenericExtensionElement commandElement = GenericExtensionElement.builder("command",
                        "http://jabber.org/protocol/commands")
                .addAttribute("sessionid", sessionId)
                .addAttribute("status", status)
                .build();
        return new Iq.Builder(Iq.Type.RESULT)
                .id("execute-" + sessionId)
                .to("example.com")
                .childElement(commandElement)
                .build();
    }

    private static Iq capturedIqAt(XmppConnection connection, int callIndex) {
        return mockingDetails(connection).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("sendIqPacketAsync"))
                .skip(callIndex)
                .findFirst()
                .map(invocation -> (Iq) invocation.getArgument(0))
                .orElseThrow();
    }

    private record SingleQuotedCommandElement(String sessionId) implements ExtensionElement {

        @Override
        public String getElementName() {
            return "command";
        }

        @Override
        public String getNamespace() {
            return "http://jabber.org/protocol/commands";
        }

        @Override
        public String toXml() {
            return "<command xmlns='http://jabber.org/protocol/commands' sessionid='"
                    + sessionId + "' status='executing'/>";
        }
    }
}
