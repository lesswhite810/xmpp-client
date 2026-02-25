package com.example.xmpp.logic;

import com.example.xmpp.protocol.StanzaListener;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.XmppStanza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TR-069 连接请求报文监听器。
 *
 * <p>监听 XMPP 消息中的 TR-069 连接请求，
 * 用于 CPE（Customer Premises Equipment）设备接收 ACS 的连接通知。</p>
 *
 * <p>当检测到包含 "Connection Request" 的消息时，
 * 触发向 ACS 发起 HTTP 连接的业务逻辑。</p>
 *
 * @see StanzaListener
 * @since 2026-02-09
 */
public class Tr069StanzaListener implements StanzaListener {

    private static final Logger logger = LoggerFactory.getLogger(Tr069StanzaListener.class);

    /**
     * 处理接收到的 XMPP 节。
     *
     * @param stanza 接收到的节
     */
    @Override
    public void processStanza(XmppStanza stanza) {
        if (stanza instanceof Message msg) {
            if (msg.getBody() != null && msg.getBody().contains("Connection Request")) {
                logger.info(">>> TR-069 CONNECTION REQUEST DETECTED (via Listener) <<<");
                logger.info("Initiating HTTP Connection to ACS...");
            }
        }
    }
}
