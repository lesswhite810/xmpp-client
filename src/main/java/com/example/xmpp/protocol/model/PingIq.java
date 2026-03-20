package com.example.xmpp.protocol.model;

import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.util.XmlStringBuilder;

/**
 * XMPP Ping IQ 帮助类。
 *
 * @since 2026-02-09
 * @see <a href="https://xmpp.org/extensions/xep-0199.html">XEP-0199: XMPP Ping</a>
 */
public class PingIq {
    /**
     * 私有构造器，防止实例化。
     */
    private PingIq() {
    }

    /**
     * 创建 Ping 请求 IQ。
     *
     * @param id IQ 唯一标识符
     * @param to 目标服务器 JID
     * @return Ping 请求 IQ
     */
    public static Iq createPingRequest(String id, String to) {
        return new Iq.Builder(Iq.Type.GET)
                .id(id)
                .to(to)
                .childElement(Ping.INSTANCE)
                .build();
    }

    /**
     * 创建 Ping 响应 IQ。
     *
     * @param request Ping 请求 IQ
     * @return Ping 响应 IQ
     * @throws NullPointerException 如果 request 为 null
     */
    public static Iq createPingResponse(Iq request) {
        return new Iq.Builder(Iq.Type.RESULT)
                .id(request.getId())
                .to(request.getFrom())
                .build();
    }

    /**
     * 创建带 from 属性的 Ping 请求 IQ。
     *
     * @param id IQ 唯一标识符
     * @param to 目标服务器 JID
     * @param from 发送者 JID
     * @return Ping 请求 IQ
     */
    public static Iq createPingRequest(String id, String to, String from) {
        return new Iq.Builder(Iq.Type.GET)
                .id(id)
                .to(to)
                .from(from)
                .childElement(Ping.INSTANCE)
                .build();
    }
}
