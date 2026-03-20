package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.Iq;
import javax.xml.stream.XMLEventReader;

/**
 * IQ Provider 接口。
 *
 * @since 2026-02-27
 * @see Provider
 */
public interface IqProvider extends Provider<Iq> {

    /**
     * 解析 IQ 子元素并构建完整的 Iq 对象。
     *
     * @param reader XML 事件读取器
     * @param builder Iq 构建器
     * @return Iq 对象
     * @throws Exception 解析失败
     */
    Iq parse(XMLEventReader reader, Iq.Builder builder) throws Exception;
}
