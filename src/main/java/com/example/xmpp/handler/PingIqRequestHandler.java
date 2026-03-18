package com.example.xmpp.handler;

import com.example.xmpp.handler.AbstractIqRequestHandler;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
import lombok.extern.slf4j.Slf4j;

/**
 * Ping IQ 请求处理器（XEP-0199）。
 *
 * <p>处理 XMPP 服务器发来的 Ping 请求，自动响应 Pong。</p>
 *
 * @since 2026-02-26
 */
@Slf4j
public class PingIqRequestHandler extends AbstractIqRequestHandler {

    /**
     * 构造 Ping IQ 请求处理器。
     */
    public PingIqRequestHandler() {
        super(Ping.ELEMENT, Ping.NAMESPACE, Iq.Type.GET, Mode.SYNC);
    }

    /**
     * 处理 Ping 请求并返回 Pong 响应。
     *
     * @param iqRequest Ping 请求节
     *
     * @return Pong 响应节
     */
    @Override
    public Iq handleIqRequest(Iq iqRequest) {
        log.debug("Handling Ping request from server, id: {}", iqRequest.getId());
        return Iq.createResultResponse(iqRequest, null);
    }
}
