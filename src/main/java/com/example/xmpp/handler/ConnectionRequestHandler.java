package com.example.xmpp.handler;

import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.extension.ConnectionRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * CPE 连接请求处理器。
 *
 * @since 2026-03-18
 */
@Slf4j
public class ConnectionRequestHandler extends AbstractIqRequestHandler {

    private final Consumer<ConnectionRequest> callback;

    /**
     * 创建连接请求处理器。
     *
     * @param callback 回调
     */
    public ConnectionRequestHandler(Consumer<ConnectionRequest> callback) {
        super(ConnectionRequest.ELEMENT, ConnectionRequest.NAMESPACE, Iq.Type.SET);
        this.callback = Objects.requireNonNull(callback, "Callback cannot be null");
    }

    /**
     * 处理 IQ 请求。
     *
     * @param iqRequest 收到的 IQ 请求
     * @return 结果响应 IQ
     */
    @Override
    public Iq handleIqRequest(Iq iqRequest) {
        log.debug("Received ConnectionRequest from: {}", iqRequest.getFrom());

        ConnectionRequest connectionRequest = extractConnectionRequest(iqRequest);

        if (connectionRequest != null) {
            log.info("Processing ConnectionRequest for CPE callback");
            try {
                callback.accept(connectionRequest);
                log.debug("ConnectionRequest callback executed successfully");
            } catch (Exception e) {
                log.warn("Error executing ConnectionRequest callback: {}", e.getMessage(), e);
            }
        } else {
            log.warn("No ConnectionRequest found in IQ: {}", iqRequest.getId());
        }

        return Iq.createResultResponse(iqRequest, null);
    }

    /**
     * 从 IQ 请求中提取 ConnectionRequest。
     *
     * @param iq IQ 请求
     * @return ConnectionRequest，或 null
     */
    private ConnectionRequest extractConnectionRequest(Iq iq) {
        if (iq.getChildElement() instanceof ConnectionRequest request) {
            return request;
        }

        return iq.getExtension(ConnectionRequest.class).orElse(null);
    }

}
