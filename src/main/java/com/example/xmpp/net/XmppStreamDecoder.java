package com.example.xmpp.net;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.ExtensionElementProvider;
import com.example.xmpp.protocol.ProviderRegistry;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.XmppError;
import com.example.xmpp.protocol.model.sasl.Auth;
import com.example.xmpp.protocol.model.sasl.SaslChallenge;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.sasl.SaslResponse;
import com.example.xmpp.protocol.model.sasl.SaslSuccess;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.protocol.model.stream.TlsElements;
import com.example.xmpp.protocol.provider.GenericExtensionProvider;
import com.example.xmpp.util.XmlParserUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import java.io.IOException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * XMPP 流解码器（基于 Netty ByteToMessageDecoder）。
 *
 * <p>直接从 ByteBuf 解析 XML，将 XMPP stanza 转换为协议对象。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public class XmppStreamDecoder extends ByteToMessageDecoder {

    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newFactory();

    /**
     * Stanza 公共属性（id、from、to）及类型。
     *
     * @param type 节类型
     * @param id 节 ID
     * @param from 发送者
     * @param to 接收者
     * @since 2026-02-09
     */
    private record StanzaAttrs(String type, String id, String from, String to) {
        static StanzaAttrs from(StartElement element) {
            Map<String, String> attrs = XmlParserUtils.getAttributes(element);
            return new StanzaAttrs(attrs.get("type"), attrs.get("id"), attrs.get("from"), attrs.get("to"));
        }

        Iq.Builder iqBuilder() {
            return new Iq.Builder(type).id(id).from(from).to(to);
        }

        Message.Builder messageBuilder() {
            return new Message.Builder().type(type).id(id).from(from).to(to);
        }

        Presence.Builder presenceBuilder() {
            return new Presence.Builder().type(type).id(id).from(from).to(to);
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }
        parseFromByteBuf(in).ifPresent(out::add);
    }

    /**
     * 从 ByteBuf 解析 XML。
     *
     * @param buf 字节缓冲区
     * @return 解析结果的可选对象
     */
    protected Optional<Object> parseFromByteBuf(ByteBuf buf) {
        XMLEventReader reader = null;
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            reader = INPUT_FACTORY.createXMLEventReader(in);
            return parseRootElement(reader);
        } catch (XMLStreamException e) {
            log.warn("XML parsing error: {}", e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Error closing ByteBuf stream: {}", e.getMessage());
            return Optional.empty();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    log.debug("Error closing XML reader", e);
                }
            }
        }
    }

    /**
     * 解析根元素。
     *
     * @param reader XML 事件读取器
     * @return 解析结果的可选对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Optional<Object> parseRootElement(XMLEventReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (!event.isStartElement()) {
                continue;
            }

            StartElement start = event.asStartElement();
            String localName = start.getName().getLocalPart();

            /** 跳过 stream 元素 */
            if ("stream".equals(localName)) {
                continue;
            }

            return parseElement(reader, start, localName, start.getName().getNamespaceURI());
        }
        return Optional.empty();
    }

    /**
     * 解析 XML 元素（顶层元素路由）。
     *
     * @param reader XML 事件读取器
     * @param start 开始元素
     * @param localName 元素本地名称
     * @param namespace 命名空间
     * @return 解析结果的可选对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Optional<Object> parseElement(XMLEventReader reader, StartElement start,
                                          String localName, String namespace) throws XMLStreamException {
        /** 优先处理 Stanza 元素 */
        return switch (localName) {
            case "iq" -> Optional.of(parseIq(reader, start));
            case "message" -> Optional.of(parseMessage(reader, start));
            case "presence" -> Optional.of(parsePresence(reader, start));
            default -> parseOtherElement(reader, start, localName, namespace);
        };
    }

    /**
     * 解析非 Stanza 元素（流级别元素或 Provider 扩展）。
     *
     * @param reader XML 事件读取器
     * @param start 开始元素
     * @param localName 元素本地名称
     * @param namespace 命名空间
     * @return 解析结果的可选对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Optional<Object> parseOtherElement(XMLEventReader reader, StartElement start,
                                                String localName, String namespace) throws XMLStreamException {
        Object streamElement = parseStreamElement(reader, start, localName);
        if (streamElement != null) {
            return Optional.of(streamElement);
        }
        return tryParseWithProvider(reader, localName, namespace);
    }

    /**
     * 解析流级别元素（SASL、TLS、Features 等）。
     *
     * @param reader XML 事件读取器
     * @param start 开始元素
     * @param localName 元素本地名称
     * @return 解析的流元素对象，如果没有匹配的类型返回 null
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Object parseStreamElement(XMLEventReader reader, StartElement start,
                                       String localName) throws XMLStreamException {
        return switch (localName) {
            case "features" -> parseFeatures(reader);
            case "starttls" -> TlsElements.StartTls.INSTANCE;
            case "proceed" -> TlsElements.TlsProceed.INSTANCE;
            case "auth" -> parseSaslAuth(reader, start);
            case "challenge" -> SaslChallenge.builder()
                    .content(XmlParserUtils.getElementText(reader))
                    .build();
            case "response" -> new SaslResponse(XmlParserUtils.getElementText(reader));
            case "success" -> SaslSuccess.builder()
                    .content(XmlParserUtils.getElementText(reader))
                    .build();
            case "failure" -> parseSaslFailure(reader);
            default -> null;
        };
    }

    /**
     * 尝试使用 Provider 解析元素。
     *
     * @param reader XML 事件读取器
     * @param localName 元素本地名称
     * @param namespace 命名空间
     * @return 解析结果的可选对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Optional<Object> tryParseWithProvider(XMLEventReader reader,
                                                   String localName, String namespace) throws XMLStreamException {
        Optional<ExtensionElementProvider<?>> extProvider =
                ProviderRegistry.getInstance().getExtensionProvider(localName, namespace);
        if (extProvider.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(extProvider.get().parse(reader));
        } catch (XmppParseException e) {
            log.warn("Extension provider failed to parse {}: {}", localName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析 StreamFeatures 元素。
     *
     * @param reader XML 事件读取器
     * @return 解析的 StreamFeatures 对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private StreamFeatures parseFeatures(XMLEventReader reader) throws XMLStreamException {
        List<String> mechanisms = new ArrayList<>();
        boolean startTls = false;
        boolean bind = false;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElement(event, "features")) {
                break;
            }
            if (!event.isStartElement()) {
                continue;
            }

            String name = event.asStartElement().getName().getLocalPart();
            switch (name) {
                case "mechanism" -> mechanisms.add(XmlParserUtils.getElementText(reader));
                case "starttls" -> startTls = true;
                case "bind" -> bind = true;
                default -> { /* 忽略未知元素 */ }
            }
        }
        return StreamFeatures.builder()
                .mechanisms(mechanisms)
                .starttlsAvailable(startTls)
                .bindAvailable(bind)
                .build();
    }

    /**
     * 解析 SASL Auth 元素。
     *
     * @param reader XML 事件读取器
     * @param element 开始元素
     * @return 解析的 Auth 对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Auth parseSaslAuth(XMLEventReader reader, StartElement element) throws XMLStreamException {
        String mechanism = getAttributeValue(element, "mechanism");
        String content = XmlParserUtils.getElementText(reader);
        return new Auth(mechanism, content.isEmpty() ? null : content);
    }

    /**
     * 解析 SASL Failure 元素。
     *
     * @param reader XML 事件读取器
     * @return 解析的 SaslFailure 对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private SaslFailure parseSaslFailure(XMLEventReader reader) throws XMLStreamException {
        String condition = "undefined-condition";
        String text = null;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElement(event, "failure")) {
                break;
            }
            if (!event.isStartElement()) {
                continue;
            }

            String name = event.asStartElement().getName().getLocalPart();
            if ("text".equals(name)) {
                text = XmlParserUtils.getElementText(reader);
            } else {
                condition = name;
            }
        }
        return SaslFailure.builder()
                .condition(condition)
                .text(text)
                .build();
    }

    /**
     * 解析 IQ 节。
     *
     * @param reader XML 事件读取器
     * @param element 开始元素
     * @return 解析的 Iq 对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Iq parseIq(XMLEventReader reader, StartElement element) throws XMLStreamException {
        StanzaAttrs attrs = StanzaAttrs.from(element);
        Iq.Builder builder = attrs.iqBuilder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElement(event, "iq")) {
                break;
            }
            if (!event.isStartElement()) {
                continue;
            }

            parseIqChildElement(reader, event.asStartElement(), builder);
        }
        return builder.build();
    }

    /**
     * 解析 IQ 子元素。
     *
     * @param reader XML 事件读取器
     * @param start 开始元素
     * @param builder IQ 构建器
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private void parseIqChildElement(XMLEventReader reader, StartElement start, Iq.Builder builder)
            throws XMLStreamException {
        String name = start.getName().getLocalPart();
        String namespace = start.getName().getNamespaceURI();

        /** 特殊处理 error 元素 */
        if ("error".equals(name)) {
            XmppError error = parseError(reader, start);
            builder.error(error);
            return;
        }

        /** 使用公共方法解析扩展元素 */
        parseExtensionElement(reader, start, name, namespace, builder::childElement);
    }

    /**
     * 解析 XMPP Error 元素。
     *
     * @param reader XML 事件读取器
     * @param element 开始元素
     * @return 解析的 XmppError 对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private XmppError parseError(XMLEventReader reader, StartElement element) throws XMLStreamException {
        String typeStr = getAttributeValue(element, "type");
        XmppError.Type type = typeStr != null ? parseErrorType(typeStr) : null;
        XmppError.Condition condition = null;
        String text = null;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElement(event, "error")) {
                break;
            }
            if (!event.isStartElement()) {
                continue;
            }

            String name = event.asStartElement().getName().getLocalPart();
            if ("text".equals(name)) {
                text = XmlParserUtils.getElementText(reader);
            } else {
                /** 错误条件元素 */
                condition = XmppError.Condition.fromString(name);
            }
        }

        if (condition == null) {
            condition = XmppError.Condition.undefined_condition;
        }

        XmppError.Builder errorBuilder = new XmppError.Builder(condition);
        if (type != null) {
            errorBuilder.type(type);
        }
        if (text != null) {
            errorBuilder.text(text);
        }
        return errorBuilder.build();
    }

    /**
     * 解析错误类型。
     */
    private XmppError.Type parseErrorType(String typeStr) {
        try {
            return XmppError.Type.valueOf(typeStr.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 解析 Message 节。
     *
     * @param reader XML 事件读取器
     * @param element 开始元素
     * @return 解析的 Message 对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Message parseMessage(XMLEventReader reader, StartElement element) throws XMLStreamException {
        StanzaAttrs attrs = StanzaAttrs.from(element);
        Message.Builder builder = attrs.messageBuilder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElement(event, "message")) {
                break;
            }
            if (!event.isStartElement()) {
                continue;
            }

            StartElement start = event.asStartElement();
            String name = start.getName().getLocalPart();
            String namespace = start.getName().getNamespaceURI();

            switch (name) {
                case "body" -> builder.body(XmlParserUtils.getElementText(reader));
                case "subject" -> builder.subject(XmlParserUtils.getElementText(reader));
                case "thread" -> builder.thread(XmlParserUtils.getElementText(reader));
                default -> parseExtensionElement(reader, start, name, namespace, builder::addExtension);
            }
        }
        return builder.build();
    }

    /**
     * 解析 Presence 节。
     *
     * @param reader XML 事件读取器
     * @param element 开始元素
     * @return 解析的 Presence 对象
     * @throws XMLStreamException 如果解析过程中发生 XML 流错误
     */
    private Presence parsePresence(XMLEventReader reader, StartElement element) throws XMLStreamException {
        StanzaAttrs attrs = StanzaAttrs.from(element);
        Presence.Builder builder = attrs.presenceBuilder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElement(event, "presence")) {
                break;
            }
            if (!event.isStartElement()) {
                continue;
            }

            StartElement start = event.asStartElement();
            String name = start.getName().getLocalPart();
            String namespace = start.getName().getNamespaceURI();

            switch (name) {
                case "show" -> builder.show(XmlParserUtils.getElementText(reader));
                case "status" -> builder.status(XmlParserUtils.getElementText(reader));
                case "priority" -> parsePriority(reader, builder);
                default -> parseExtensionElement(reader, start, name, namespace, builder::addExtension);
            }
        }
        return builder.build();
    }

    /**
     * 解析 priority 值。
     *
     * @param reader XML 事件读取器
     * @param builder Presence 构建器
     */
    private void parsePriority(XMLEventReader reader, Presence.Builder builder) {
        try {
            builder.priority(Integer.parseInt(XmlParserUtils.getElementText(reader)));
        } catch (NumberFormatException | XMLStreamException e) {
            log.warn("Invalid priority value in presence stanza");
        }
    }

    /**
     * 解析扩展元素（使用注册的 Provider 或通用解析器）。
     *
     * @param reader        XML 事件读取器
     * @param start         开始元素事件
     * @param name          元素名称
     * @param namespace     命名空间
     * @param addExtension  添加扩展的回调函数
     */
    private void parseExtensionElement(XMLEventReader reader, StartElement start,
                                        String name, String namespace,
                                        Consumer<ExtensionElement> addExtension) {
        Optional<ExtensionElementProvider<?>> provider =
                ProviderRegistry.getInstance().getExtensionProvider(name, namespace);
        if (provider.isPresent()) {
            try {
                Object parsed = provider.get().parse(reader);
                if (parsed instanceof ExtensionElement ext) {
                    addExtension.accept(ext);
                }
            } catch (XmppParseException e) {
                log.warn("Provider failed to parse <{} xmlns=\"{}\">: {}", name, namespace, e.getMessage());
            }
        } else {
            log.debug("No provider for <{} xmlns=\"{}\">, using generic parser", name, namespace);
            try {
                GenericExtensionElement ext = GenericExtensionProvider.INSTANCE.parse(reader, start);
                addExtension.accept(ext);
            } catch (XmppParseException e) {
                log.warn("Generic parser failed for <{} xmlns=\"{}\">: {}", name, namespace, e.getMessage());
            }
        }
    }

    /**
     * 判断是否为指定名称的结束元素。
     *
     * @param event XML 事件
     * @param elementName 元素名称
     * @return 如果是指定名称的结束元素返回 true，否则返回 false
     */
    private boolean isEndElement(XMLEvent event, String elementName) {
        return event.isEndElement()
                && elementName.equals(event.asEndElement().getName().getLocalPart());
    }

    /**
     * 获取元素属性值。
     *
     * @param element 开始元素
     * @param attrName 属性名称
     * @return 属性值，如果不存在返回 null
     */
    private String getAttributeValue(StartElement element, String attrName) {
        var attr = element.getAttributeByName(new QName(attrName));
        return attr != null ? attr.getValue() : null;
    }
}
