package com.example.xmpp.handler;

import com.example.xmpp.handler.AbstractIqRequestHandler;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.Ping;
import lombok.extern.slf4j.Slf4j;

/**
 * Ping IQ 请求处理器。
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
     * 处理 Ping 请求并返回响应。
     *
     * @param iqRequest Ping 请求
     * @return 响应 IQ
     */
    @Override
    public Iq handleIqRequest(Iq iqRequest) {
        log.debug("Handling Ping request from server, id: {}", iqRequest.getId());
        return Iq.createResultResponse(iqRequest, null);
    }
}
