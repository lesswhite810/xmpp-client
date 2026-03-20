package com.example.xmpp.protocol.provider;

import com.example.xmpp.protocol.model.extension.ConnectionRequest;
import com.example.xmpp.util.XmlParserUtils;
import com.example.xmpp.util.XmlStringBuilder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * ConnectionRequest 扩展 Provider。
 *
 * @since 2026-03-18
 */
public final class ConnectionRequestProvider extends AbstractProvider<ConnectionRequest> {

    /**
     * ConnectionRequest 元素名称。
     */
    public static final String ELEMENT = "connectionRequest";

    /**
     * ConnectionRequest 命名空间。
     */
    public static final String NAMESPACE = "urn:broadband-forum-org:cwmp:xmppConnReq-1-0";

    /**
     * 用户名字段名称。
     */
    private static final String USERNAME = "username";

    /**
     * 密码字段名称。
     */
    private static final String PASSWORD = "password";

    /**
     * 获取元素名称。
     *
     * @return 固定返回 connectionRequest
     */
    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * 获取命名空间。
     *
     * @return ConnectionRequest 命名空间
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 解析 ConnectionRequest 元素。
     *
     * @param reader XMLEventReader，用于读取 XML 事件流
     * @return 解析后的 ConnectionRequest 实例
     * @throws XMLStreamException 如果解析过程中发生 XML 错误
     */
    @Override
    protected ConnectionRequest parseInstance(XMLEventReader reader) throws XMLStreamException {
        String username = null;
        String password = null;

        if (reader.hasNext() && reader.peek().isStartElement()) {
            reader.nextEvent();
        }

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                String localName = event.asStartElement().getName().getLocalPart();
                String text = XmlParserUtils.getElementText(reader);

                switch (localName) {
                    case USERNAME -> username = text;
                    case PASSWORD -> password = text;
                    default -> log().trace("Ignoring unknown element: {}", localName);
                }
            }

            if (isElementEnd(event)) {
                break;
            }
        }

        try {
            return ConnectionRequest.builder()
                    .username(username)
                    .password(password)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new XMLStreamException("Invalid ConnectionRequest payload", e);
        }
    }

    /**
     * 序列化 ConnectionRequest 对象为 XML。
     *
     * @param connectionRequest ConnectionRequest 对象
     * @param xml XmlStringBuilder，用于构建 XML 输出
     */
    @Override
    protected void serializeInstance(ConnectionRequest connectionRequest, XmlStringBuilder xml) {
        xml.append(connectionRequest.toXml());
    }
}
