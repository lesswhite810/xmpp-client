package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.ExtensionElementProvider;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlParserUtils;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * 扩展元素 Provider 抽象基类，使用模板方法模式提供 XML 解析和序列化的公共逻辑。
 *
 * <p>所有扩展元素 Provider 应继承此类，只需实现 {@link #parseInstance(XMLEventReader)}
 * 和 {@link #serializeInstance(ExtensionElement, XmlStringBuilder)} 方法。</p>
 *
 * @param <T> 此 Provider 处理的扩展元素类型
 * @since 2026-02-14
 */
@Slf4j
public abstract class AbstractProvider<T extends ExtensionElement>
        implements ExtensionElementProvider<T> {

    /**
     * 解析 XML 元素。
     *
     * @param reader XMLEventReader
     * @return 解析后的对象
     * @throws XmppParseException 如果解析失败
     */
    @Override
    public final T parse(XMLEventReader reader) throws XmppParseException {
        if (reader == null) {
            throw new XmppParseException("XMLEventReader cannot be null");
        }

        if (log.isTraceEnabled()) {
            log.trace("Parsing element: <{} xmlns=\"{}\"/>", getElementName(), getNamespace());
        }

        try {
            T result = parseInstance(reader);
            if (log.isTraceEnabled()) {
                log.trace("Successfully parsed element: {}", getElementName());
            }
            return result;
        } catch (XMLStreamException e) {
            String msg = String.format("Failed to parse <%s xmlns=\"%s\">",
                    getElementName(), getNamespace());
            log.debug(msg, e);
            throw new XmppParseException(msg, e);
        }
    }

    /**
     * 序列化对象为 XML。
     *
     * @param object 要序列化的对象
     * @param xml    XmlStringBuilder
     */
    @Override
    public final void serialize(T object, XmlStringBuilder xml) {
        if (object == null) {
            if (log.isTraceEnabled()) {
                log.trace("Skip serialization: object is null for <{}>", getElementName());
            }
            return;
        }
        if (xml == null) {
            log.warn("XmlStringBuilder is null, skip serialization");
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Serializing element: <{}>", getElementName());
        }
        serializeInstance(object, xml);
    }

    /**
     * 执行具体的解析逻辑，子类在此方法中实现元素特定的解析逻辑。
     *
     * @param reader XMLEventReader
     * @return 解析后的对象
     * @throws XMLStreamException 如果解析过程中发生错误
     */
    protected abstract T parseInstance(XMLEventReader reader) throws XMLStreamException;

    /**
     * 执行具体的序列化逻辑，子类在此方法中将对象内容写入 XmlStringBuilder。
     *
     * @param object 要序列化的对象（非 null）
     * @param xml    XmlStringBuilder（非 null）
     */
    protected abstract void serializeInstance(T object, XmlStringBuilder xml);

    /**
     * 检查事件是否为当前 Provider 处理的元素的结束标签。
     *
     * @param event XMLEvent
     * @return 如果是当前元素的结束标签则返回 true
     */
    protected final boolean isElementEnd(XMLEvent event) {
        if (!event.isEndElement()) {
            return false;
        }

        String localPart = event.asEndElement().getName().getLocalPart();
        if (!getElementName().equals(localPart)) {
            return false;
        }

        String namespace = event.asEndElement().getName().getNamespaceURI();
        String expectedNamespace = getNamespace();

        if (expectedNamespace == null || expectedNamespace.isEmpty()) {
            return namespace == null || namespace.isEmpty();
        }
        return expectedNamespace.equals(namespace);
    }

    /**
     * 安全地获取元素文本内容。
     *
     * @param reader XMLEventReader
     * @return 元素文本内容，可能为 null
     * @throws XMLStreamException 如果读取失败
     */
    protected final String getElementText(XMLEventReader reader) throws XMLStreamException {
        return XmlParserUtils.getElementText(reader);
    }

    /**
     * 创建 Provider 解析异常。
     *
     * @param message 错误消息
     * @param cause   原因（可为 null）
     * @return XmppParseException
     */
    protected final XmppParseException createParseException(String message, Throwable cause) {
        return new XmppParseException(message, cause);
    }

    /**
     * 创建 Provider 解析异常（无原因）。
     *
     * @param message 错误消息
     * @return XmppParseException
     */
    protected final XmppParseException createParseException(String message) {
        return new XmppParseException(message);
    }

    /**
     * 获取日志记录器。
     *
     * @return Logger 实例
     */
    protected final org.slf4j.Logger log() {
        return log;
    }
}
