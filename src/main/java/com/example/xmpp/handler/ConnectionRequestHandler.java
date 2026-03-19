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
public class ConnectionRequestHandler implements IqRequestHandler {

    private final Consumer<ConnectionRequest> callback;

    /**
     * 创建连接请求处理器。
     *
     * @param callback 收到 ConnectionRequest 后触发的回调，用于执行 CPE 回连逻辑
     */
    public ConnectionRequestHandler(Consumer<ConnectionRequest> callback) {
        this.callback = Objects.requireNonNull(callback, "Callback cannot be null");
    }

    /**
     * 处理 IQ 请求。
     *
     * <p>从 IQ 请求中提取 ConnectionRequest 扩展，解析认证信息，
     * 并触发回调通知上层应用。</p>
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
                log.error("Error executing ConnectionRequest callback: {}", e.getMessage(), e);
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
     * @return ConnectionRequest，如果不存在则返回 null
     */
    private ConnectionRequest extractConnectionRequest(Iq iq) {
        if (iq.getChildElement() instanceof ConnectionRequest request) {
            return request;
        }

        return iq.getExtension(ConnectionRequest.class).orElse(null);
    }

    /**
     * 获取元素名称。
     *
     * @return 固定返回 connectionRequest
     */
    @Override
    public String getElement() {
        return ConnectionRequest.ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return ConnectionRequest 命名空间
     */
    @Override
    public String getNamespace() {
        return ConnectionRequest.NAMESPACE;
    }

    /**
     * 获取 IQ 类型。
     *
     * @return 固定返回 {@link Iq.Type#SET}
     */
    @Override
    public Iq.Type getIqType() {
        return Iq.Type.SET;
    }

    /**
     * 获取处理模式。
     *
     * @return 同步模式
     */
    @Override
    public Mode getMode() {
        return Mode.SYNC;
    }
}
