package com.example.xmpp.protocol;

import com.example.xmpp.protocol.model.Iq;

/**
 * IQ Provider 接口。
 *
 * <p>用于解析完整的 IQ 节，返回带有子元素的 Iq 对象。</p>
 *
 * <p>IQ 的通用属性（type, id, from, to）由解码器解析，
 * Provider 只需解析 IQ 的子元素并构建完整的 Iq 对象。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * public class VersionIqProvider implements IqProvider {
 *     @Override
 *     public Iq parse(XMLEventReader reader, Iq.Builder builder) {
 *         // 解析 <query xmlns="jabber:iq:version"> 子元素
 *         String name = null, version = null, os = null;
 *         while (reader.hasNext()) {
 *             XMLEvent event = reader.nextEvent();
 *             if (event.isStartElement()) {
 *                 switch (event.asStartElement().getName().getLocalPart()) {
 *                     case "name" -> name = getElementText(reader);
 *                     case "version" -> version = getElementText(reader);
 *                     case "os" -> os = getElementText(reader);
 *                 }
 *             } else if (event.isEndElement() && "query".equals(...)) {
 *                 break;
 *             }
 *         }
 *         return builder.childElement(new Version(name, version, os)).build();
 *     }
 * }
 * }</pre>
 *
 * @since 2026-02-27
 * @see Provider
 */
public interface IqProvider extends Provider<Iq> {

    /**
     * 解析 IQ 子元素并构建完整的 Iq 对象。
     *
     * @param reader  XML 事件读取器，已定位到子元素的开始标签
     * @param builder Iq 构建器，已设置 type, id, from, to 属性
     * @return 完整的 Iq 对象
     * @throws Exception 解析异常
     */
    Iq parse(javax.xml.stream.XMLEventReader reader, Iq.Builder builder) throws Exception;
}
