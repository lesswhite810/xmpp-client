package com.example.xmpp;

import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.exception.AdminCommandException;
import com.example.xmpp.exception.XmppStanzaErrorException;
import com.example.xmpp.logic.AdminManager;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.XmppStanza;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 本地真实服务器 XEP-0133 诊断测试。
 *
 * <p>用于捕获服务器返回的管理命令错误细节，帮助确认命令是否被服务器支持。</p>
 *
 * @since 2026-03-13
 */
@Slf4j
class Xep0133ServerDiagnosticsTest {

    private XmppTcpConnection connection;
    private AdminManager adminManager;

    @BeforeEach
    void setUp() throws Exception {
        XmppClientConfig config = XmppClientConfig.builder()
                .xmppServiceDomain("lesswhite")
                .host("localhost")
                .port(5222)
                .username("admin")
                .password("admin".toCharArray())
                .securityMode(XmppClientConfig.SecurityMode.DISABLED)
                .build();

        connection = new XmppTcpConnection(config);
        connection.connect();
        Thread.sleep(2000);
        adminManager = new AdminManager(connection, config);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    /**
     * 输出命令结果详情。
     *
     * @param commandName 命令名称
     * @param commandFuture 命令 Future
     * @throws Exception 执行过程中出现的异常
     */
    private void logCommandResult(String commandName, CompletableFuture<XmppStanza> commandFuture) throws Exception {
        try {
            XmppStanza stanza = commandFuture.get(20, TimeUnit.SECONDS);
            if (stanza instanceof Iq iq) {
                log.info("{} succeeded: type={}, xml={}", commandName, iq.getType(), iq.toXml());
                return;
            }
            log.info("{} succeeded with non-IQ stanza: {}", commandName, stanza);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof XmppStanzaErrorException see) {
                Iq errorIq = see.getErrorIq();
                XmppError error = see.getXmppError();
                XmppError.Condition condition = error != null ? error.getCondition() : null;
                XmppError.Type type = error != null ? error.getType() : null;
                String text = error != null ? error.getText() : null;
                log.warn("{} failed: condition={}, type={}, text={}, xml={}",
                        commandName, condition, type, text, errorIq.toXml());
                return;
            }
            if (cause instanceof AdminCommandException ace && ace.hasErrorResponse()) {
                Iq errorIq = ace.getErrorResponse();
                XmppError error = errorIq.getError();
                XmppError.Condition condition = error != null ? error.getCondition() : null;
                XmppError.Type type = error != null ? error.getType() : null;
                String text = error != null ? error.getText() : null;
                log.warn("{} failed: condition={}, type={}, text={}, xml={}",
                        commandName, condition, type, text, errorIq.toXml());
                return;
            }
            fail("Unexpected exception for " + commandName + ": " + cause, cause);
        }
    }
}
