package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.event.ConnectionEventType;
import com.example.xmpp.event.XmppEventBus;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.exception.XmppException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.PingIq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实服务器高级场景测试。
 *
 * @since 2026-03-13
 */
class XmppRealServerAdvancedScenarioTest extends AbstractRealServerTest {

    private static final String XMPP_DOMAIN = "lesswhite";
    private static final String HOST = "localhost";
    private static final int PORT = 5222;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private final List<XmppTcpConnection> openedConnections = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (XmppTcpConnection openedConnection : openedConnections) {
            openedConnection.disconnect();
        }
        openedConnections.clear();
    }

    @Test
    void testChangePasswordInvalidatesOldPasswordAndAllowsNewPassword() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "admin-change-pass");
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        String username = "change_pass_" + System.currentTimeMillis();
        String oldPassword = "OldPass123!@#";
        String newPassword = "NewPass456!@#";
        adminManager.addUser(username, oldPassword).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        try {
            try {
                adminManager.changePassword(username, newPassword).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (isUnsupportedAdminCommand(exception.getCause(), "change-password")) {
                    Assumptions.assumeTrue(false, "真实服务器当前未实现 change-password 命令");
                }
                throw exception;
            }

            XmppTcpConnection oldPasswordConnection = createConnection(username, oldPassword, "old-password");
            XmppException authFailure = assertThrows(XmppException.class, oldPasswordConnection::connect,
                    "改密后旧密码应失效");
            assertNotNull(authFailure.getMessage(), "旧密码失败应有异常信息");
            oldPasswordConnection.disconnect();

            XmppTcpConnection newPasswordConnection = openAuthenticatedConnection(username, newPassword, "new-password");
            Iq pingResponse = sendPing(newPasswordConnection, "change-pass-ping-" + System.nanoTime(), XMPP_DOMAIN);
            assertEquals(Iq.Type.RESULT, pingResponse.getType(), "改密后新密码登录应成功");
        } finally {
            adminManager.deleteUser(username).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSameAccountMultipleResourcesCanAuthenticateConcurrently() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "admin-multi-resource");
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        String username = "multi_resource_" + System.currentTimeMillis();
        String password = "Pass123!@#";
        adminManager.addUser(username, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        try {
            XmppTcpConnection resourceA = openAuthenticatedConnection(username, password, "resource-a");
            XmppTcpConnection resourceB = openAuthenticatedConnection(username, password, "resource-b");

            Iq responseA = sendPing(resourceA, "multi-res-a-" + System.nanoTime(), XMPP_DOMAIN);
            Iq responseB = sendPing(resourceB, "multi-res-b-" + System.nanoTime(), XMPP_DOMAIN);

            assertEquals(Iq.Type.RESULT, responseA.getType(), "同账号 resource-a 的 ping 应成功");
            assertEquals(Iq.Type.RESULT, responseB.getType(), "同账号 resource-b 的 ping 应成功");
            assertTrue(resourceA.isAuthenticated(), "resource-a 应保持认证");
            assertTrue(resourceB.isAuthenticated(), "resource-b 应保持认证");
        } finally {
            adminManager.deleteUser(username).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void testErrorIqDoesNotPoisonConnection() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "admin-error-reuse");

        Iq unsupportedRequest = new Iq.Builder(Iq.Type.GET)
                .id("poison-check-" + System.nanoTime())
                .to(XMPP_DOMAIN)
                .childElement(new GenericExtensionElement.Builder("query", "urn:example:advanced:unsupported").build())
                .build();

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> adminConnection.sendIqPacketAsync(unsupportedRequest).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(XmppStanzaErrorException.class, exception.getCause(), "应返回标准 stanza error");
        XmppStanzaErrorException stanzaErrorException = (XmppStanzaErrorException) exception.getCause();
        assertNotNull(stanzaErrorException.getXmppError(), "错误 IQ 应包含 xmpp error");
                assertTrue(stanzaErrorException.getXmppError().getCondition() == XmppError.Condition.SERVICE_UNAVAILABLE
                        || stanzaErrorException.getXmppError().getCondition() == XmppError.Condition.FEATURE_NOT_IMPLEMENTED,
                "未知查询应返回标准协议错误");

        Iq pingResponse = sendPing(adminConnection, "post-error-ping-" + System.nanoTime(), XMPP_DOMAIN);
        assertEquals(Iq.Type.RESULT, pingResponse.getType(), "收到 error IQ 后连接仍应可继续使用");
    }

    @Test
    void testHigherConcurrencyParallelLogins() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "admin-higher-concurrency");
        AdminManager adminManager = new AdminManager(adminConnection, adminConnection.getConfig());

        List<String> usernames = new ArrayList<>();
        List<String> passwords = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String username = "higher_login_" + i + "_" + System.currentTimeMillis();
            String password = "Pass" + i + "123!@#";
            usernames.add(username);
            passwords.add(password);
            adminManager.addUser(username, password).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        try {
            List<CompletableFuture<Iq>> futures = new ArrayList<>();
            for (int i = 0; i < usernames.size(); i++) {
                String username = usernames.get(i);
                String password = passwords.get(i);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    XmppTcpConnection userConnection = null;
                    try {
                        userConnection = openAuthenticatedConnection(username, password, "bulk-" + System.nanoTime());
                        return sendPing(userConnection, "higher-ping-" + username + "-" + System.nanoTime(), XMPP_DOMAIN);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    } finally {
                        if (userConnection != null) {
                            userConnection.disconnect();
                            openedConnections.remove(userConnection);
                        }
                    }
                }));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            for (CompletableFuture<Iq> future : futures) {
                Iq iq = future.getNow(null);
                assertNotNull(iq, "更高并发登录后的 ping 应全部成功");
                assertEquals(Iq.Type.RESULT, iq.getType(), "更高并发登录后的 ping 应返回 result");
            }
        } finally {
            for (String username : usernames) {
                adminManager.deleteUser(username).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void testLongerMixedOperationLoopRemainsStable() throws Exception {
        XmppTcpConnection adminConnection = openAuthenticatedConnection(ADMIN_USERNAME, ADMIN_PASSWORD, "admin-long-loop");
        assertTrue(adminConnection.collectors.isEmpty(), "循环开始前不应残留 collector");

        for (int i = 0; i < 60; i++) {
            if (i % 10 == 0) {
                Iq unsupportedRequest = new Iq.Builder(Iq.Type.GET)
                        .id("loop-unsupported-" + i + "-" + System.nanoTime())
                        .to(XMPP_DOMAIN)
                        .childElement(new GenericExtensionElement.Builder("query", "urn:example:loop:unsupported").build())
                        .build();

                ExecutionException exception = assertThrows(ExecutionException.class,
                        () -> adminConnection.sendIqPacketAsync(unsupportedRequest).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        "循环中的错误 IQ 应继续返回 stanza error");
                assertInstanceOf(XmppStanzaErrorException.class, exception.getCause(), "错误 IQ 应稳定映射为 stanza error");
            }

            Iq pingResponse = sendPing(adminConnection, "long-loop-ping-" + i + "-" + System.nanoTime(), XMPP_DOMAIN);
            assertEquals(Iq.Type.RESULT, pingResponse.getType(), "长循环中的 ping 应持续成功");
        }

        adminConnection.cleanupCollectors();
        assertFalse(adminConnection.collectors.stream().anyMatch(collector -> !collector.getFuture().isDone()),
                "长循环结束后不应残留未完成 collector");
    }

    private XmppTcpConnection openAuthenticatedConnection(String username, String password, String resource) throws Exception {
        XmppTcpConnection connection = createConnection(username, password, resource);
        openedConnections.add(connection);
        awaitAuthenticated(connection);
        return connection;
    }

    private XmppTcpConnection createConnection(String username, String password, String resource) {
        return new XmppTcpConnection(XmppClientConfig.builder()
                .xmppServiceDomain(XMPP_DOMAIN)
                .host(HOST)
                .port(PORT)
                .resource(resource)
                .sendPresence(true)
                .username(username)
                .password(password.toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .enabledSaslMechanisms(Set.of())
                .build());
    }

    private void awaitAuthenticated(XmppTcpConnection connection) throws Exception {
        XmppEventBus eventBus = XmppEventBus.getInstance();
        CountDownLatch authenticatedLatch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        Runnable authSubscription = eventBus.subscribe(connection, ConnectionEventType.AUTHENTICATED,
                event -> authenticatedLatch.countDown());
        Runnable errorSubscription = eventBus.subscribe(connection, ConnectionEventType.ERROR, event -> {
            errorRef.set(event.error());
            authenticatedLatch.countDown();
        });

        try {
            connection.connect();
            assertTrue(authenticatedLatch.await(15, TimeUnit.SECONDS), "认证应在超时时间内完成");
            if (errorRef.get() != null) {
                throw errorRef.get();
            }
            assertTrue(connection.isAuthenticated(), "连接应处于已认证状态");
        } finally {
            authSubscription.run();
            errorSubscription.run();
        }
    }

    private Iq sendPing(XmppTcpConnection connection, String expectedId, String to) {
        try {
            XmppStanza stanza = connection.sendIqPacketAsync(PingIq.createPingRequest(expectedId, to))
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return assertAndCastIq(stanza, expectedId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new CompletionException(e);
        }
    }

    private Iq assertAndCastIq(XmppStanza stanza, String expectedId) {
        assertInstanceOf(Iq.class, stanza, "响应应为 IQ");
        Iq iq = (Iq) stanza;
        assertEquals(expectedId, iq.getId(), "响应 id 应与请求匹配");
        return iq;
    }

    private boolean isUnsupportedAdminCommand(Throwable throwable, String commandName) {
        if (!(throwable instanceof AdminCommandException ace)) {
            return false;
        }
        if (!commandName.equals(ace.getCommandName())) {
            return false;
        }
        XmppError error = ace.getErrorResponse() != null ? ace.getErrorResponse().getError() : null;
        return error != null && error.getCondition() == XmppError.Condition.ITEM_NOT_FOUND;
    }
}
