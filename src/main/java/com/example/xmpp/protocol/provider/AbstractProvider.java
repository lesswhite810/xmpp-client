package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.ExtensionElementProvider;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.util.XmlParserUtils;
import com.example.xmpp.util.XmlStringBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * 扩展元素 Provider 抽象基类。
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
     * @param reader XML 事件读取器
     * @return 解析后的扩展元素对象
     * @throws XmppParseException 解析失败
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
            log.error("{} - ErrorType: {}", msg, e.getClass().getSimpleName());
            throw new XmppParseException(msg, e);
        }
    }

    /**
     * 序列化对象为 XML。
     *
     * @param object 要序列化的扩展元素对象
     * @param xml XML 构建器
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
     * 执行具体的解析逻辑。
     *
     * @param reader XML 事件读取器
     * @return 解析后的扩展元素对象
     * @throws XMLStreamException XML 解析失败
     */
    protected abstract T parseInstance(XMLEventReader reader) throws XMLStreamException;

    /**
     * 执行具体的序列化逻辑。
     *
     * @param object 要序列化的扩展元素对象
     * @param xml XML 构建器
     */
    protected abstract void serializeInstance(T object, XmlStringBuilder xml);

    /**
     * 检查事件是否为当前 Provider 处理的元素的结束标签。
     *
     * @param event XMLEvent
     * @return 是否为结束标签
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

        if (StringUtils.isEmpty(expectedNamespace)) {
            return StringUtils.isEmpty(namespace);
        }
        return expectedNamespace.equals(namespace);
    }

    /**
     * 安全地获取当前开始元素的文本内容。
     *
     * <p>调用前，reader 可以位于子元素的 {@code START_ELEMENT}，也可以位于它之前且可通过
     * {@code peek()} 看到该开始标签。返回时，该子元素的匹配结束标签已被消费，reader 会推进到
     * 该子元素之后的下一个事件。</p>
     *
     * @param reader XML 事件读取器
     * @return 元素文本内容
     * @throws XMLStreamException XML 解析失败
     */
    protected final String getElementText(XMLEventReader reader) throws XMLStreamException {
        return XmlParserUtils.getElementText(reader);
    }

    /**
     * 创建 Provider 解析异常。
     *
     * @param message 错误消息
     * @param cause 原因异常
     * @return 解析异常
     */
    protected final XmppParseException createParseException(String message, Throwable cause) {
        return new XmppParseException(message, cause);
    }

    /**
     * 创建 Provider 解析异常（无原因）。
     *
     * @param message 错误消息
     * @return 解析异常
     */
    protected final XmppParseException createParseException(String message) {
        return new XmppParseException(message);
    }
}
