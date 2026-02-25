package com.example.xmpp.net;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.Provider;
import com.example.xmpp.protocol.ProviderRegistry;
import com.example.xmpp.protocol.model.ExtensionElement;
import com.example.xmpp.protocol.model.Iq;
import com.example.xmpp.protocol.model.Message;
import com.example.xmpp.protocol.model.Presence;
import com.example.xmpp.protocol.model.extension.Bind;
import com.example.xmpp.protocol.model.extension.Ping;
import com.example.xmpp.protocol.model.sasl.Auth;
import com.example.xmpp.protocol.model.sasl.SaslChallenge;
import com.example.xmpp.protocol.model.sasl.SaslFailure;
import com.example.xmpp.protocol.model.sasl.SaslResponse;
import com.example.xmpp.protocol.model.sasl.SaslSuccess;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.protocol.model.stream.StreamHeader;
import com.example.xmpp.protocol.model.stream.TlsElements;
import com.example.xmpp.util.XmppEventReader;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * XMPP 流解码器（基于 Netty ByteToMessageDecoder）。
 *
 * <p>直接从 ByteBuf 解析 XML，将 XMPP stanza 转换为协议对象。</p>
 *
 * @since 2026-02-09
 */
public class XmppStreamDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(XmppStreamDecoder.class);
    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newFactory();

    /**
     * 解码 XMPP 流数据。
     *
     * @param ctx Netty 通道上下文
     * @param in  输入 ByteBuf
     * @param out 输出对象列表
     * @throws Exception 解析异常
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }
        parseFromByteBuf(in).ifPresent(out::add);
    }

    // ==================== 核心解析方法 ====================

    /**
     * 从 ByteBuf 解析 XML。
     *
     * @param buf 输入 ByteBuf
     * @return 解析后的对象，如果解析失败返回 Optional.empty()
     */
    private Optional<Object> parseFromByteBuf(ByteBuf buf) {
        XMLEventReader reader = null;
        try {
            reader = INPUT_FACTORY.createXMLEventReader(new ByteBufInputStream(buf));
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();

                if (!event.isStartElement()) {
                    continue;
                }

                StartElement start = event.asStartElement();
                String localName = start.getName().getLocalPart();
                String namespace = start.getName().getNamespaceURI();

                if ("stream".equals(localName)) {
                    continue;
                }

                return parseElement(reader, start, localName, namespace);
            }
            return Optional.empty();
        } catch (XMLStreamException e) {
            log.warn("XML parsing error: {}", e.getMessage());
            return Optional.empty();
        } finally {
            XmppEventReader.closeQuietly(reader);
        }
    }

    /**
     * 解析 XML 元素（优先使用 Provider，回退到内置解析）。
     *
     * @param reader     XML 事件读取器
     * @param start      起始元素
     * @param localName  元素本地名称
     * @param namespace  元素命名空间
     * @return 解析后的对象，如果无法解析返回 Optional.empty()
     * @throws XMLStreamException XML 解析异常
     */
    private Optional<Object> parseElement(XMLEventReader reader, StartElement start,
                                          String localName, String namespace) throws XMLStreamException {
        // 尝试使用 Provider 解析
        Optional<Object> result = tryParseWithProvider(reader, localName, namespace);
        if (result.isPresent()) {
            return result;
        }

        // 使用内置解析器
        return parseBuiltinElement(reader, start, localName);
    }

    /**
     * 尝试使用 Provider 解析扩展元素。
     *
     * @param reader     XML 事件读取器
     * @param localName  元素本地名称
     * @param namespace  元素命名空间
     * @return 解析后的对象，如果无对应 Provider 返回 Optional.empty()
     * @throws XMLStreamException XML 解析异常
     */
    private Optional<Object> tryParseWithProvider(XMLEventReader reader,
                                                   String localName, String namespace) throws XMLStreamException {
        Optional<Provider<?>> providerOpt = ProviderRegistry.getInstance().getProvider(localName, namespace);
        if (providerOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            Object result = providerOpt.get().parse(reader);
            return Optional.ofNullable(result);
        } catch (XmppParseException e) {
            log.warn("Provider failed to parse {}: {}", localName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析内置 XMPP 元素。
     *
     * @param reader    XML 事件读取器
     * @param start     起始元素
     * @param localName 元素本地名称
     * @return 解析后的对象，如果无法识别返回 Optional.empty()
     * @throws XMLStreamException XML 解析异常
     */
    private Optional<Object> parseBuiltinElement(XMLEventReader reader, StartElement start,
                                                  String localName) throws XMLStreamException {
        Object result = switch (localName) {
            case "iq" -> parseIq(reader, start);
            case "message" -> parseMessage(reader, start);
            case "presence" -> parsePresence(reader, start);
            case "features" -> parseFeatures(reader);
            case "starttls" -> TlsElements.StartTls.INSTANCE;
            case "proceed" -> TlsElements.TlsProceed.INSTANCE;
            case "auth" -> parseSaslAuth(reader, start);
            case "challenge" -> SaslChallenge.builder()
                    .content(XmppEventReader.getElementText(reader))
                    .build();
            case "response" -> new SaslResponse(XmppEventReader.getElementText(reader));
            case "success" -> SaslSuccess.builder()
                    .content(XmppEventReader.getElementText(reader))
                    .build();
            case "failure" -> parseSaslFailure(reader);
            default -> null;
        };
        return Optional.ofNullable(result);
    }

    // ==================== 元素解析方法 ====================

    /**
     * 解析 StreamFeatures 元素。
     *
     * @param reader XML 事件读取器
     * @return 解析后的 StreamFeatures 对象
     * @throws XMLStreamException XML 解析异常
     */
    private StreamFeatures parseFeatures(XMLEventReader reader) throws XMLStreamException {
        List<String> mechanisms = new ArrayList<>();
        boolean startTls = false;
        boolean bind = false;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isEndElement() && "features".equals(event.asEndElement().getName().getLocalPart())) {
                break;
            }
            if (event.isStartElement()) {
                String name = event.asStartElement().getName().getLocalPart();
                if ("mechanism".equals(name)) {
                    mechanisms.add(XmppEventReader.getElementText(reader));
                } else if ("starttls".equals(name)) {
                    startTls = true;
                } else if ("bind".equals(name)) {
                    bind = true;
                }
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
     * @param reader  XML 事件读取器
     * @param element 起始元素
     * @return 解析后的 Auth 对象
     * @throws XMLStreamException XML 解析异常
     */
    private Auth parseSaslAuth(XMLEventReader reader, StartElement element) throws XMLStreamException {
        String mechanism = null;
        var attr = element.getAttributeByName(new QName("mechanism"));
        if (attr != null) {
            mechanism = attr.getValue();
        }

        String content = XmppEventReader.getElementText(reader);
        return new Auth(mechanism, content.isEmpty() ? null : content);
    }

    /**
     * 解析 SASL Failure 元素。
     *
     * @param reader XML 事件读取器
     * @return 解析后的 SaslFailure 对象
     * @throws XMLStreamException XML 解析异常
     */
    private SaslFailure parseSaslFailure(XMLEventReader reader) throws XMLStreamException {
        String condition = "undefined-condition";
        String text = null;
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isEndElement() && "failure".equals(event.asEndElement().getName().getLocalPart())) {
                break;
            }
            if (event.isStartElement()) {
                String name = event.asStartElement().getName().getLocalPart();
                if ("text".equals(name)) {
                    text = XmppEventReader.getElementText(reader);
                } else {
                    condition = name;
                }
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
     * @param reader  XML 事件读取器
     * @param element 起始元素
     * @return 解析后的 Iq 对象
     * @throws XMLStreamException XML 解析异常
     */
    private Iq parseIq(XMLEventReader reader, StartElement element) throws XMLStreamException {
        Map<String, String> attrs = XmppEventReader.getAttributes(element);
        Iq.Builder builder = new Iq.Builder(attrs.get("type"));

        builder.id(attrs.get("id"));
        builder.from(attrs.get("from"));
        builder.to(attrs.get("to"));

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isEndElement() && "iq".equals(event.asEndElement().getName().getLocalPart())) {
                break;
            }
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                String name = start.getName().getLocalPart();
                String namespace = start.getName().getNamespaceURI();

                // 优先使用 Provider 解析
                Optional<Provider<?>> provider = ProviderRegistry.getInstance().getProvider(name, namespace);
                if (provider.isPresent()) {
                    try {
                        Object parsed = provider.get().parse(reader);
                        if (parsed instanceof ExtensionElement ext) {
                            builder.childElement(ext);
                        }
                    } catch (XmppParseException e) {
                        log.warn("Provider failed to parse {}: {}", name, e.getMessage());
                    }
                    continue;
                }

                // 回退到内置解析
                if ("bind".equals(name)) {
                    builder.childElement(parseBind(reader));
                } else if ("ping".equals(name) && "urn:xmpp:ping".equals(namespace)) {
                    XmppEventReader.getElementText(reader);
                    builder.childElement(Ping.INSTANCE);
                }
            }
        }
        return builder.build();
    }

    /**
     * 解析 Bind 元素。
     *
     * @param reader XML 事件读取器
     * @return 解析后的 Bind 对象
     * @throws XMLStreamException XML 解析异常
     */
    private Bind parseBind(XMLEventReader reader) throws XMLStreamException {
        Bind.BindBuilder builder = Bind.builder();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isEndElement() && "bind".equals(event.asEndElement().getName().getLocalPart())) {
                break;
            }
            if (event.isStartElement()) {
                String name = event.asStartElement().getName().getLocalPart();
                if ("resource".equals(name)) {
                    builder.resource(XmppEventReader.getElementText(reader));
                } else if ("jid".equals(name)) {
                    builder.jid(XmppEventReader.getElementText(reader));
                }
            }
        }
        return builder.build();
    }

    /**
     * 解析 Message 节。
     *
     * @param reader  XML 事件读取器
     * @param element 起始元素
     * @return 解析后的 Message 对象
     * @throws XMLStreamException XML 解析异常
     */
    private Message parseMessage(XMLEventReader reader, StartElement element) throws XMLStreamException {
        Map<String, String> attrs = XmppEventReader.getAttributes(element);
        Message.Builder builder = new Message.Builder();

        builder.type(attrs.get("type"));
        builder.id(attrs.get("id"));
        builder.from(attrs.get("from"));
        builder.to(attrs.get("to"));

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isEndElement() && "message".equals(event.asEndElement().getName().getLocalPart())) {
                break;
            }
            if (event.isStartElement()) {
                String name = event.asStartElement().getName().getLocalPart();
                switch (name) {
                    case "body" -> builder.body(XmppEventReader.getElementText(reader));
                    case "subject" -> builder.subject(XmppEventReader.getElementText(reader));
                    case "thread" -> builder.thread(XmppEventReader.getElementText(reader));
                    default -> { /* 忽略未知元素 */ }
                }
            }
        }
        return builder.build();
    }

    /**
     * 解析 Presence 节。
     *
     * @param reader  XML 事件读取器
     * @param element 起始元素
     * @return 解析后的 Presence 对象
     * @throws XMLStreamException XML 解析异常
     */
    private Presence parsePresence(XMLEventReader reader, StartElement element) throws XMLStreamException {
        Map<String, String> attrs = XmppEventReader.getAttributes(element);
        Presence.Builder builder = new Presence.Builder();

        builder.type(attrs.get("type"));
        builder.id(attrs.get("id"));
        builder.from(attrs.get("from"));
        builder.to(attrs.get("to"));

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isEndElement() && "presence".equals(event.asEndElement().getName().getLocalPart())) {
                break;
            }
            if (event.isStartElement()) {
                String name = event.asStartElement().getName().getLocalPart();
                switch (name) {
                    case "show" -> builder.show(XmppEventReader.getElementText(reader));
                    case "status" -> builder.status(XmppEventReader.getElementText(reader));
                    case "priority" -> {
                        try {
                            builder.priority(Integer.parseInt(XmppEventReader.getElementText(reader)));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid priority value in presence stanza");
                        }
                    }
                    default -> { /* 忽略未知元素 */ }
                }
            }
        }
        return builder.build();
    }

    // ==================== 静态解析方法（供测试使用） ====================

    /**
     * 解析 XML 字符串为协议对象。
     *
     * @param xml XML 字符串
     * @return 解析后的对象，如果解析失败返回 Optional.empty()
     */
    public static Optional<Object> parse(String xml) {
        if (xml == null || xml.isEmpty()) {
            return Optional.empty();
        }
        if (xml.startsWith("<?xml") || xml.contains("stream:stream")) {
            return Optional.of(parseStreamHeader(xml));
        }
        ByteBuf buf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        return new XmppStreamDecoder().parseFromByteBuf(buf);
    }

    /**
     * 解析 XML 字符串为 IQ 节。
     *
     * @param xml XML 字符串
     * @return 解析后的 Iq 对象
     * @throws IllegalArgumentException 如果 XML 不是有效的 IQ 节
     */
    public static Iq parseIq(String xml) {
        return parse(xml)
                .filter(Iq.class::isInstance)
                .map(Iq.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Not an IQ stanza"));
    }

    /**
     * 解析 XML 字符串为 Message 节。
     *
     * @param xml XML 字符串
     * @return 解析后的 Message 对象
     * @throws IllegalArgumentException 如果 XML 不是有效的 Message 节
     */
    public static Message parseMessage(String xml) {
        return parse(xml)
                .filter(Message.class::isInstance)
                .map(Message.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Not a Message stanza"));
    }

    /**
     * 解析 XML 字符串为 Presence 节。
     *
     * @param xml XML 字符串
     * @return 解析后的 Presence 对象
     * @throws IllegalArgumentException 如果 XML 不是有效的 Presence 节
     */
    public static Presence parsePresence(String xml) {
        return parse(xml)
                .filter(Presence.class::isInstance)
                .map(Presence.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Not a Presence stanza"));
    }

    /**
     * 解析 XML 字符串为 Stanza 对象。
     *
     * @param xml XML 字符串
     * @return 解析后的对象，如果解析失败返回 Optional.empty()
     */
    public static Optional<Object> parseStanza(String xml) {
        return parse(xml);
    }

    /**
     * 解析流头部。
     *
     * @param xml XML 字符串
     * @return 解析后的 StreamHeader 对象
     */
    private static StreamHeader parseStreamHeader(String xml) {
        return StreamHeader.builder()
                .from(extractAttribute(xml, "from"))
                .to(extractAttribute(xml, "to"))
                .id(extractAttribute(xml, "id"))
                .version(extractAttribute(xml, "version"))
                .lang(extractAttribute(xml, "lang"))
                .namespace("jabber:client")
                .build();
    }

    /**
     * 从 XML 字符串中提取属性值。
     *
     * @param xml  XML 字符串
     * @param name 属性名称
     * @return 属性值，如果不存在返回 null
     */
    private static String extractAttribute(String xml, String name) {
        int idx = xml.indexOf(name + "='");
        if (idx == -1) {
            idx = xml.indexOf(name + "=\"");
        }
        if (idx == -1) {
            return null;
        }
        char quote = xml.charAt(idx + name.length() + 1);
        int start = idx + name.length() + 2;
        int end = xml.indexOf(quote, start);
        return end == -1 ? null : xml.substring(start, end);
    }
}
