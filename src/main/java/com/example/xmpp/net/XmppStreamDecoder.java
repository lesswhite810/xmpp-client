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
import com.example.xmpp.protocol.model.stream.StreamError;
import com.example.xmpp.protocol.model.stream.StreamFeatures;
import com.example.xmpp.protocol.model.stream.TlsElements;
import com.example.xmpp.protocol.provider.GenericExtensionProvider;
import com.example.xmpp.util.XmlParserUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * XMPP 流解码器。
 *
 * <p>负责从 Netty {@link ByteBuf} 中切分出完整 XML 帧，并解析为对应的
 * XMPP 协议对象。</p>
 */
@Slf4j
public class XmppStreamDecoder extends ByteToMessageDecoder {

    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newFactory();
    private static final int MAX_BUFFER_SIZE = 1024 * 1024;
    private static final String STREAMS_NAMESPACE = "http://etherx.jabber.org/streams";
    private static final String TLS_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-tls";
    private static final String SASL_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-sasl";
    private static final String DECODER_ROOT_PREFIX =
            "<decoder-root xmlns='jabber:client' xmlns:stream='" + STREAMS_NAMESPACE + "'>";
    private static final String DECODER_ROOT_SUFFIX = "</decoder-root>";

    private enum FrameType {
        ELEMENT,
        SKIP
    }

    private record FrameBoundary(FrameType type, int length) {
    }

    /**
     * 提取 Stanza 共有属性，便于构建具体的协议对象。
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
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) {
            return;
        }

        int initialReaderIndex = in.readerIndex();
        while (in.isReadable()) {
            skipLeadingWhitespace(in);
            if (!in.isReadable()) {
                break;
            }

            Optional<FrameBoundary> boundary = findNextFrame(in);
            if (boundary.isEmpty()) {
                break;
            }

            FrameBoundary frameBoundary = boundary.orElseThrow();
            if (frameBoundary.type() == FrameType.SKIP) {
                in.skipBytes(frameBoundary.length());
                continue;
            }

            byte[] xmlBytes = new byte[frameBoundary.length()];
            in.readBytes(xmlBytes);
            parseFrame(xmlBytes).ifPresent(out::add);
        }

        if (initialReaderIndex == in.readerIndex()) {
            handleIncompleteBuffer(in);
        }
    }

    /**
     * 从缓冲区中解析所有已完整到达的顶层 XML 元素。
     *
     * @param buf 字节缓冲区
     * @return 已解析出的协议元素列表
     */
    protected List<Object> parseFromByteBuf(ByteBuf buf) {
        List<Object> elements = new ArrayList<>();
        int readerIndex = buf.readerIndex();
        while (readerIndex < buf.writerIndex()) {
            readerIndex = skipLeadingWhitespace(buf, readerIndex);
            if (readerIndex >= buf.writerIndex()) {
                break;
            }

            Optional<FrameBoundary> boundary = findNextFrame(buf, readerIndex);
            if (boundary.isEmpty()) {
                break;
            }

            FrameBoundary frameBoundary = boundary.orElseThrow();
            if (frameBoundary.type() == FrameType.ELEMENT) {
                byte[] xmlBytes = new byte[frameBoundary.length()];
                buf.getBytes(readerIndex, xmlBytes);
                parseFrame(xmlBytes).ifPresent(elements::add);
            }
            readerIndex += frameBoundary.length();
        }
        return elements;
    }

    private void handleIncompleteBuffer(ByteBuf in) {
        int bufferSize = in.readableBytes();
        if (bufferSize > MAX_BUFFER_SIZE) {
            log.warn("Buffer size {} exceeded max {}, clearing buffer", bufferSize, MAX_BUFFER_SIZE);
            in.skipBytes(bufferSize);
            return;
        }
        log.debug("Waiting for more data to complete XML frame, buffer size: {}", bufferSize);
    }

    private Optional<Object> parseFrame(byte[] xmlBytes) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        String wrappedXml = DECODER_ROOT_PREFIX + xml + DECODER_ROOT_SUFFIX;

        XMLEventReader reader = null;
        try {
            reader = INPUT_FACTORY.createXMLEventReader(new StringReader(wrappedXml));
            return parseWrappedFrame(reader);
        } catch (XMLStreamException e) {
            log.warn("XML parsing error: {}", e.getMessage());
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

    private Optional<Object> parseWrappedFrame(XMLEventReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (!event.isStartElement()) {
                continue;
            }

            StartElement start = event.asStartElement();
            String localName = start.getName().getLocalPart();
            if ("decoder-root".equals(localName)) {
                continue;
            }
            if (isStreamOpenElement(localName, start.getName().getNamespaceURI())) {
                return Optional.empty();
            }
            return parseElement(reader, start, localName, start.getName().getNamespaceURI());
        }
        return Optional.empty();
    }

    private void skipLeadingWhitespace(ByteBuf in) {
        int newReaderIndex = skipLeadingWhitespace(in, in.readerIndex());
        if (newReaderIndex > in.readerIndex()) {
            in.readerIndex(newReaderIndex);
        }
    }

    private int skipLeadingWhitespace(ByteBuf in, int index) {
        while (index < in.writerIndex() && Character.isWhitespace((char) in.getByte(index))) {
            index++;
        }
        return index;
    }

    private Optional<FrameBoundary> findNextFrame(ByteBuf in) {
        return findNextFrame(in, in.readerIndex());
    }

    private Optional<FrameBoundary> findNextFrame(ByteBuf in, int startIndex) {
        if (startIndex >= in.writerIndex()) {
            return Optional.empty();
        }
        if (in.getByte(startIndex) != '<') {
            int nextTagIndex = findNextTagStart(in, startIndex);
            if (nextTagIndex < 0) {
                return Optional.empty();
            }
            return Optional.of(new FrameBoundary(FrameType.SKIP, nextTagIndex - startIndex));
        }

        if (matches(in, startIndex, "<?")) {
            return findSequence(in, startIndex + 2, "?>")
                    .map(end -> new FrameBoundary(FrameType.SKIP, end - startIndex));
        }
        if (matches(in, startIndex, "<!--")) {
            return findSequence(in, startIndex + 4, "-->")
                    .map(end -> new FrameBoundary(FrameType.SKIP, end - startIndex));
        }
        if (matches(in, startIndex, "</")) {
            return findTagEnd(in, startIndex + 2)
                    .map(tagEnd -> new FrameBoundary(FrameType.SKIP, tagEnd - startIndex));
        }

        Optional<Integer> openingTagEnd = findTagEnd(in, startIndex + 1);
        if (openingTagEnd.isEmpty()) {
            return Optional.empty();
        }

        String tagName = readTagName(in, startIndex + 1, openingTagEnd.orElseThrow()).orElse("");
        if (tagName.isBlank()) {
            return Optional.empty();
        }
        if (isStreamOpenTag(tagName)) {
            return Optional.of(new FrameBoundary(FrameType.SKIP, openingTagEnd.orElseThrow() - startIndex));
        }
        if (isSelfClosingTag(in, openingTagEnd.orElseThrow())) {
            return Optional.of(new FrameBoundary(FrameType.ELEMENT, openingTagEnd.orElseThrow() - startIndex));
        }

        int depth = 1;
        int index = openingTagEnd.orElseThrow();
        while (index < in.writerIndex()) {
            int nextTagStart = findNextTagStart(in, index);
            if (nextTagStart < 0) {
                return Optional.empty();
            }

            if (matches(in, nextTagStart, "<!--")) {
                Optional<Integer> commentEnd = findSequence(in, nextTagStart + 4, "-->");
                if (commentEnd.isEmpty()) {
                    return Optional.empty();
                }
                index = commentEnd.orElseThrow();
                continue;
            }
            if (matches(in, nextTagStart, "<![CDATA[")) {
                Optional<Integer> cdataEnd = findSequence(in, nextTagStart + 9, "]]>");
                if (cdataEnd.isEmpty()) {
                    return Optional.empty();
                }
                index = cdataEnd.orElseThrow();
                continue;
            }
            if (matches(in, nextTagStart, "<?")) {
                Optional<Integer> processingEnd = findSequence(in, nextTagStart + 2, "?>");
                if (processingEnd.isEmpty()) {
                    return Optional.empty();
                }
                index = processingEnd.orElseThrow();
                continue;
            }

            Optional<Integer> tagEnd = findTagEnd(in, nextTagStart + 1);
            if (tagEnd.isEmpty()) {
                return Optional.empty();
            }

            if (matches(in, nextTagStart, "</")) {
                depth--;
                index = tagEnd.orElseThrow();
                if (depth == 0) {
                    return Optional.of(new FrameBoundary(FrameType.ELEMENT, tagEnd.orElseThrow() - startIndex));
                }
                continue;
            }

            if (!isSelfClosingTag(in, tagEnd.orElseThrow())) {
                depth++;
            }
            index = tagEnd.orElseThrow();
        }
        return Optional.empty();
    }

    private int findNextTagStart(ByteBuf in, int startIndex) {
        for (int index = startIndex; index < in.writerIndex(); index++) {
            if (in.getByte(index) == '<') {
                return index;
            }
        }
        return -1;
    }

    private Optional<Integer> findTagEnd(ByteBuf in, int startIndex) {
        boolean inQuote = false;
        byte quoteChar = 0;
        for (int index = startIndex; index < in.writerIndex(); index++) {
            byte current = in.getByte(index);
            if ((current == '"' || current == '\'') && (!inQuote || current == quoteChar)) {
                inQuote = !inQuote;
                quoteChar = current;
                continue;
            }
            if (!inQuote && current == '>') {
                return Optional.of(index + 1);
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> findSequence(ByteBuf in, int startIndex, String sequence) {
        byte[] bytes = sequence.getBytes(StandardCharsets.UTF_8);
        for (int index = startIndex; index <= in.writerIndex() - bytes.length; index++) {
            boolean matched = true;
            for (int offset = 0; offset < bytes.length; offset++) {
                if (in.getByte(index + offset) != bytes[offset]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return Optional.of(index + bytes.length);
            }
        }
        return Optional.empty();
    }

    private boolean matches(ByteBuf in, int startIndex, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (startIndex + bytes.length > in.writerIndex()) {
            return false;
        }
        for (int index = 0; index < bytes.length; index++) {
            if (in.getByte(startIndex + index) != bytes[index]) {
                return false;
            }
        }
        return true;
    }

    private Optional<String> readTagName(ByteBuf in, int startIndex, int tagEndExclusive) {
        int index = startIndex;
        while (index < tagEndExclusive && Character.isWhitespace((char) in.getByte(index))) {
            index++;
        }
        int nameStart = index;
        while (index < tagEndExclusive) {
            byte current = in.getByte(index);
            if (Character.isWhitespace((char) current) || current == '/' || current == '>') {
                break;
            }
            index++;
        }
        if (nameStart == index) {
            return Optional.empty();
        }
        byte[] nameBytes = new byte[index - nameStart];
        in.getBytes(nameStart, nameBytes);
        return Optional.of(new String(nameBytes, StandardCharsets.UTF_8));
    }

    private boolean isSelfClosingTag(ByteBuf in, int tagEndExclusive) {
        int index = tagEndExclusive - 2;
        while (index >= 0 && Character.isWhitespace((char) in.getByte(index))) {
            index--;
        }
        return index >= 0 && in.getByte(index) == '/';
    }

    private boolean isStreamOpenTag(String tagName) {
        return "stream:stream".equals(tagName) || "stream".equals(tagName);
    }

    private boolean isStreamOpenElement(String localName, String namespace) {
        return "stream".equals(localName) && STREAMS_NAMESPACE.equals(namespace);
    }

    private Optional<Object> parseElement(XMLEventReader reader, StartElement start,
                                          String localName, String namespace) throws XMLStreamException {
        return switch (localName) {
            case "iq" -> Optional.of(parseIq(reader, start));
            case "message" -> Optional.of(parseMessage(reader, start));
            case "presence" -> Optional.of(parsePresence(reader, start));
            default -> parseOtherElement(reader, start, localName, namespace);
        };
    }

    private Optional<Object> parseOtherElement(XMLEventReader reader, StartElement start,
                                               String localName, String namespace) throws XMLStreamException {
        Optional<Object> streamElement = parseStreamElement(reader, start, localName, namespace);
        if (streamElement.isPresent()) {
            return streamElement;
        }
        return tryParseWithProvider(reader, localName, namespace);
    }

    private Optional<Object> parseStreamElement(XMLEventReader reader, StartElement start,
                                                String localName, String namespace) throws XMLStreamException {
        if (STREAMS_NAMESPACE.equals(namespace)) {
            return switch (localName) {
                case "features" -> Optional.of(parseFeatures(reader));
                case "error" -> Optional.of(parseStreamError(reader));
                default -> Optional.empty();
            };
        }
        if (TLS_NAMESPACE.equals(namespace)) {
            return switch (localName) {
                case "starttls" -> Optional.of(TlsElements.StartTls.INSTANCE);
                case "proceed" -> Optional.of(TlsElements.TlsProceed.INSTANCE);
                default -> Optional.empty();
            };
        }
        if (SASL_NAMESPACE.equals(namespace)) {
            return switch (localName) {
                case "auth" -> Optional.of(parseSaslAuth(reader, start));
                case "challenge" -> Optional.of(SaslChallenge.builder()
                        .content(XmlParserUtils.getElementText(reader))
                        .build());
                case "response" -> Optional.of(new SaslResponse(XmlParserUtils.getElementText(reader)));
                case "success" -> Optional.of(SaslSuccess.builder()
                        .content(XmlParserUtils.getElementText(reader))
                        .build());
                case "failure" -> Optional.of(parseSaslFailure(reader));
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

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
                default -> {
                }
            }
        }
        return StreamFeatures.builder()
                .mechanisms(mechanisms)
                .starttlsAvailable(startTls)
                .bindAvailable(bind)
                .build();
    }

    private Auth parseSaslAuth(XMLEventReader reader, StartElement element) throws XMLStreamException {
        String mechanism = getAttributeValue(element, "mechanism");
        String content = XmlParserUtils.getElementText(reader);
        return new Auth(mechanism, content.isEmpty() ? null : content);
    }

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

    private StreamError parseStreamError(XMLEventReader reader) throws XMLStreamException {
        StreamError.Condition condition = StreamError.Condition.UNDEFINED_CONDITION;
        String text = null;
        String by = null;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (isEndElement(event, "error")) {
                break;
            }
            if (!event.isStartElement()) {
                continue;
            }

            StartElement element = event.asStartElement();
            String name = element.getName().getLocalPart();
            if ("text".equals(name)) {
                text = XmlParserUtils.getElementText(reader);
            } else if ("by".equals(name)) {
                by = XmlParserUtils.getElementText(reader);
            } else {
                condition = StreamError.Condition.fromString(name);
            }
        }

        return StreamError.builder()
                .condition(condition)
                .text(text)
                .by(by)
                .build();
    }

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

    private void parseIqChildElement(XMLEventReader reader, StartElement start, Iq.Builder builder)
            throws XMLStreamException {
        String name = start.getName().getLocalPart();
        String namespace = start.getName().getNamespaceURI();

        if ("error".equals(name)) {
            XmppError error = parseError(reader, start);
            builder.error(error);
            return;
        }

        parseExtensionElement(reader, start, name, namespace, builder::childElement);
    }

    private XmppError parseError(XMLEventReader reader, StartElement element) throws XMLStreamException {
        String typeStr = getAttributeValue(element, "type");
        Optional<XmppError.Type> type = typeStr != null ? parseErrorType(typeStr) : Optional.empty();
        XmppError.Condition condition = XmppError.Condition.UNDEFINED_CONDITION;
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
                condition = XmppError.Condition.fromString(name);
            }
        }

        XmppError.Builder errorBuilder = new XmppError.Builder(condition);
        if (type.isPresent()) {
            errorBuilder.type(type.orElseThrow());
        }
        if (text != null) {
            errorBuilder.text(text);
        }
        return errorBuilder.build();
    }

    private Optional<XmppError.Type> parseErrorType(String typeStr) {
        try {
            return Optional.of(XmppError.Type.valueOf(typeStr.toUpperCase().replace("-", "_")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

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

    private void parsePriority(XMLEventReader reader, Presence.Builder builder) {
        try {
            builder.priority(Integer.parseInt(XmlParserUtils.getElementText(reader)));
        } catch (NumberFormatException | XMLStreamException e) {
            log.warn("Invalid priority value in presence stanza");
        }
    }

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

    private boolean isEndElement(XMLEvent event, String elementName) {
        return event.isEndElement()
                && elementName.equals(event.asEndElement().getName().getLocalPart());
    }

    private String getAttributeValue(StartElement element, String attrName) {
        var attr = element.getAttributeByName(new QName(attrName));
        return attr != null ? attr.getValue() : null;
    }
}
