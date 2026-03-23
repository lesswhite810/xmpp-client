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
import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.util.XmlParserUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * XMPP 流解码器。
 *
 * @since 2026-02-09
 */
@Slf4j
public class XmppStreamDecoder extends ByteToMessageDecoder {

    // XML tag markers
    private static final String PI_START = "<?";
    private static final String COMMENT_START = "<!--";
    private static final String CLOSING_TAG_START = "</";
    private static final String CDATA_START = "<![CDATA[";
    private static final String CDATA_END = "]]>";
    private static final String COMMENT_END = "-->";
    private static final String DECODER_ROOT_TAG = "decoder-root";

    // XML tag length constants (for offset calculations)
    private static final int PROCESSING_INSTRUCTION_PREFIX_LENGTH = 2;  // "<?".length()
    private static final int COMMENT_PREFIX_LENGTH = 4;                  // "<!--".length()
    private static final int CDATA_PREFIX_LENGTH = 9;                    // "<![CDATA[".length()
    private static final int CLOSING_TAG_PREFIX_LENGTH = 2;              // "</".length()
    private static final int OPENING_TAG_PREFIX_LENGTH = 1;               // "<".length()
    private static final int SELF_CLOSING_CHECK_OFFSET = 2;              // position of '/' in "/>"

    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newFactory();
    private static final int MAX_BUFFER_SIZE = 1024 * 1024;
    private static final int MAX_XML_NESTING_DEPTH = 256;
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
     * 提取 Stanza 共有属性。
     */
    private record StanzaAttrs(String type, String id, String from, String to) {
        static StanzaAttrs from(StartElement element) {
            return new StanzaAttrs(getAttribute(element, "type"), getAttribute(element, "id"),
                    getAttribute(element, "from"), getAttribute(element, "to"));
        }

        private static String getAttribute(StartElement element, String name) {
            var attr = element.getAttributeByName(new QName(name));
            return attr != null ? attr.getValue() : null;
        }

        Iq.Builder iqBuilder() {
            return new Iq.Builder(type).id(id).from(from).to(to);
        }

        Message.Builder messageBuilder() {
            return new Message.Builder().type(type).id(id).from(from).to(to);
        }

        Presence.Builder presenceBuilder() {
            Presence.Builder builder = new Presence.Builder().id(id).from(from).to(to);
            if (type != null) {
                builder.type(type);
            }
            return builder;
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
            readerIndex = findFirstNonWhitespaceIndex(buf, readerIndex);
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
            log.error("XML parsing error - ErrorType: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    log.error("Error closing XML reader: {}", e.getMessage());
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
            if (DECODER_ROOT_TAG.equals(localName)) {
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
        int newReaderIndex = findFirstNonWhitespaceIndex(in, in.readerIndex());
        if (newReaderIndex > in.readerIndex()) {
            in.readerIndex(newReaderIndex);
        }
    }

    private int findFirstNonWhitespaceIndex(ByteBuf in, int index) {
        while (index < in.writerIndex() && Character.isWhitespace((char) in.getByte(index))) {
            index++;
        }
        return index;
    }

    private Optional<FrameBoundary> findNextFrame(ByteBuf in) {
        return findNextFrame(in, in.readerIndex());
    }

    private Optional<FrameBoundary> findNextFrame(ByteBuf in, int startIndex) {
        // 1. 边界检查 + 非 '<' 字符处理
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

        // 2. 特殊标签处理: XML声明/注释/裸闭合标签 → SKIP
        if (matches(in, startIndex, PI_START)) {
            return findSequence(in, startIndex + PROCESSING_INSTRUCTION_PREFIX_LENGTH, "?>")
                    .map(end -> new FrameBoundary(FrameType.SKIP, end - startIndex));
        }
        if (matches(in, startIndex, COMMENT_START)) {
            return findSequence(in, startIndex + COMMENT_PREFIX_LENGTH, COMMENT_END)
                    .map(end -> new FrameBoundary(FrameType.SKIP, end - startIndex));
        }
        if (matches(in, startIndex, CLOSING_TAG_START)) {
            return findTagEnd(in, startIndex + CLOSING_TAG_PREFIX_LENGTH)
                    .map(tagEnd -> new FrameBoundary(FrameType.SKIP, tagEnd - startIndex));
        }

        // 3. 开标签解析 → 自闭合/special stream tag → SKIP
        Optional<Integer> openingTagEnd = findTagEnd(in, startIndex + OPENING_TAG_PREFIX_LENGTH);
        if (openingTagEnd.isEmpty()) {
            return Optional.empty();
        }
        int openingTagEndIndex = openingTagEnd.orElseThrow();

        String tagName = readTagName(in, startIndex + 1, openingTagEndIndex).orElse("");
        if (StringUtils.isBlank(tagName)) {
            return Optional.empty();
        }
        if (isStreamOpenTag(tagName)) {
            log.trace("Skipping stream open tag at index {}", openingTagEndIndex);
            return Optional.of(new FrameBoundary(FrameType.SKIP, openingTagEndIndex - startIndex));
        }
        if (isSelfClosingTag(in, openingTagEndIndex)) {
            log.trace("Skipping self-closing tag at index {}", openingTagEndIndex);
            return Optional.of(new FrameBoundary(FrameType.ELEMENT, openingTagEndIndex - startIndex));
        }

        // 4. 调用 searchFrameEnd 找匹配闭合标签
        return searchFrameEnd(in, startIndex, openingTagEndIndex, tagName);
    }

    private Optional<FrameBoundary> searchFrameEnd(ByteBuf in, int startIndex,
                                                   int openingTagEndIndex, String tagName) {
        Deque<String> tagStack = new ArrayDeque<>();
        tagStack.addLast(tagName);
        int index = openingTagEndIndex;

        while (index < in.writerIndex()) {
            int nextTagStart = findNextTagStart(in, index);
            if (nextTagStart < 0) {
                return Optional.empty();
            }

            if (matches(in, nextTagStart, COMMENT_START)) {
                Optional<Integer> commentEnd = findSequence(in, nextTagStart + COMMENT_PREFIX_LENGTH, COMMENT_END);
                if (commentEnd.isEmpty()) {
                    return Optional.empty();
                }
                log.trace("Skipping comment block from {} to {}", nextTagStart, commentEnd.orElseThrow());
                index = commentEnd.orElseThrow();
                continue;
            }
            if (matches(in, nextTagStart, CDATA_START)) {
                Optional<Integer> cdataEnd = findSequence(in, nextTagStart + CDATA_PREFIX_LENGTH, CDATA_END);
                if (cdataEnd.isEmpty()) {
                    return Optional.empty();
                }
                log.trace("Skipping CDATA block from {} to {}", nextTagStart, cdataEnd.orElseThrow());
                index = cdataEnd.orElseThrow();
                continue;
            }
            if (matches(in, nextTagStart, PI_START)) {
                Optional<Integer> processingEnd = findSequence(in, nextTagStart + PROCESSING_INSTRUCTION_PREFIX_LENGTH, "?>");
                if (processingEnd.isEmpty()) {
                    return Optional.empty();
                }
                log.trace("Skipping processing instruction from {} to {}", nextTagStart, processingEnd.orElseThrow());
                index = processingEnd.orElseThrow();
                continue;
            }

            Optional<Integer> tagEnd = findTagEnd(in, nextTagStart + OPENING_TAG_PREFIX_LENGTH);
            if (tagEnd.isEmpty()) {
                return Optional.empty();
            }
            int tagEndIndex = tagEnd.orElseThrow();

            if (matches(in, nextTagStart, CLOSING_TAG_START)) {
                String closingTagName = readTagName(in, nextTagStart + CLOSING_TAG_PREFIX_LENGTH, tagEndIndex)
                        .orElse("");
                if (StringUtils.isBlank(closingTagName) || tagStack.isEmpty()) {
                    return malformedFrameBoundary(startIndex, tagEndIndex, "invalid-closing-tag", nextTagStart);
                }

                String expectedTagName = tagStack.peekLast();
                if (!closingTagName.equals(expectedTagName)) {
                    log.trace("Mismatched closing tag at {}, expected={}, actual={}",
                            nextTagStart, expectedTagName, closingTagName);
                    return malformedFrameBoundary(startIndex, tagEndIndex, closingTagName, nextTagStart);
                }

                tagStack.removeLast();
                log.trace("Closing tag at {}, remainingDepth={}, tagName={}",
                        nextTagStart, tagStack.size(), closingTagName);
                index = tagEndIndex;
                if (tagStack.isEmpty()) {
                    log.trace("Frame complete: tagName={}, length={}", tagName, tagEndIndex - startIndex);
                    return Optional.of(new FrameBoundary(FrameType.ELEMENT, tagEndIndex - startIndex));
                }
                continue;
            }

            if (!isSelfClosingTag(in, tagEndIndex)) {
                String nestedTagName = readTagName(in, nextTagStart + OPENING_TAG_PREFIX_LENGTH, tagEndIndex)
                        .orElse("");
                if (StringUtils.isBlank(nestedTagName)) {
                    return malformedFrameBoundary(startIndex, tagEndIndex, "invalid-opening-tag", nextTagStart);
                }
                if (tagStack.size() >= MAX_XML_NESTING_DEPTH) {
                    return depthExceededFrameBoundary(startIndex, tagEndIndex,
                            nestedTagName, nextTagStart, tagStack.size() + 1);
                }
                tagStack.addLast(nestedTagName);
            }
            index = tagEndIndex;
        }
        return Optional.empty();
    }

    private Optional<FrameBoundary> depthExceededFrameBoundary(int startIndex, int tagEndIndex,
                                                               String tagName, int tagStartIndex, int depth) {
        log.warn("Dropping frame because XML nesting depth {} exceeds max {}, tagName={}, startIndex={}",
                depth, MAX_XML_NESTING_DEPTH, tagName, tagStartIndex);
        return Optional.of(new FrameBoundary(FrameType.SKIP, tagEndIndex - startIndex));
    }

    private Optional<FrameBoundary> malformedFrameBoundary(int startIndex, int tagEndIndex,
                                                           String tagName, int tagStartIndex) {
        log.trace("Dropping malformed frame from {} to {}, tagName={}", startIndex, tagStartIndex, tagName);
        return Optional.of(new FrameBoundary(FrameType.SKIP, tagEndIndex - startIndex));
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
        int index = tagEndExclusive - SELF_CLOSING_CHECK_OFFSET;
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
                case "challenge" -> Optional.of(new SaslChallenge(XmlParserUtils.getElementText(reader)));
                case "response" -> Optional.of(new SaslResponse(XmlParserUtils.getElementText(reader)));
                case "success" -> Optional.of(new SaslSuccess(XmlParserUtils.getElementText(reader)));
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
            log.error("Extension provider failed to parse {} - ErrorType: {}",
                    localName, e.getClass().getSimpleName());
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

            StartElement start = event.asStartElement();
            String name = start.getName().getLocalPart();
            String namespace = start.getName().getNamespaceURI();
            switch (name) {
                case "mechanism" -> {
                    if (SASL_NAMESPACE.equals(namespace)) {
                        mechanisms.add(XmlParserUtils.getElementText(reader));
                    }
                }
                case "starttls" -> {
                    if (TLS_NAMESPACE.equals(namespace)) {
                        startTls = true;
                    }
                }
                case "bind" -> {
                    if (XmppConstants.NS_XMPP_BIND.equals(namespace)) {
                        bind = true;
                    }
                }
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
        return new Auth(mechanism, StringUtils.defaultIfEmpty(content, null));
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

            StartElement start = event.asStartElement();
            String name = start.getName().getLocalPart();
            String namespace = start.getName().getNamespaceURI();
            if (!SASL_NAMESPACE.equals(namespace)) {
                continue;
            }
            if ("text".equals(name)) {
                text = XmlParserUtils.getElementText(reader);
            } else {
                condition = name;
            }
        }
        return new SaslFailure(condition, text);
    }

    private StreamError parseStreamError(XMLEventReader reader) throws XMLStreamException {
        StreamError.Condition condition = null;
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
            String childNamespace = element.getName().getNamespaceURI();
            if ("text".equals(name) && StreamError.NAMESPACE.equals(childNamespace)) {
                text = XmlParserUtils.getElementText(reader);
            } else if ("by".equals(name) && StreamError.NAMESPACE.equals(childNamespace)) {
                by = XmlParserUtils.getElementText(reader);
            } else if (StreamError.NAMESPACE.equals(childNamespace)) {
                Optional<StreamError.Condition> parsedCondition = parseKnownStreamErrorCondition(name);
                if (parsedCondition.isPresent()) {
                    condition = parsedCondition.orElseThrow();
                } else {
                    log.debug("Ignoring unknown stream error child <{} xmlns=\"{}\">", name, childNamespace);
                }
            } else {
                log.debug("Ignoring non-stream-error child <{} xmlns=\"{}\">", name, childNamespace);
            }
        }

        return StreamError.builder()
                .condition(condition)
                .text(text)
                .by(by)
                .build();
    }

    private Optional<StreamError.Condition> parseKnownStreamErrorCondition(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Optional.of(StreamError.Condition.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
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

        if ("error".equals(name) && isClientElementNamespace(namespace)) {
            XmppError error = parseError(reader, start);
            builder.error(error);
            return;
        }

        parseExtensionElement(reader, start, name, namespace, builder::childElement);
    }

    private XmppError parseError(XMLEventReader reader, StartElement element) throws XMLStreamException {
        String typeStr = getAttributeValue(element, "type");
        XmppError.Type errorType = typeStr != null ? parseErrorType(typeStr).orElse(null) : null;
        if (typeStr != null && errorType == null) {
            log.warn("XMPP protocol violation: unknown error type '{}' from server, treating as null", typeStr);
        }
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

            StartElement child = event.asStartElement();
            String name = child.getName().getLocalPart();
            String namespace = child.getName().getNamespaceURI();
            if ("text".equals(name) && XmppError.NAMESPACE.equals(namespace)) {
                text = XmlParserUtils.getElementText(reader);
            } else if (XmppError.NAMESPACE.equals(namespace)) {
                Optional<XmppError.Condition> parsedCondition = parseKnownXmppErrorCondition(name);
                if (parsedCondition.isPresent()) {
                    condition = parsedCondition.orElseThrow();
                } else {
                    log.debug("Ignoring unknown stanza error child <{} xmlns=\"{}\">", name, namespace);
                }
            } else {
                log.debug("Ignoring non-stanza-error child <{} xmlns=\"{}\">", name, namespace);
            }
        }

        XmppError.Builder errorBuilder = new XmppError.Builder(condition);
        if (errorType != null) {
            errorBuilder.type(errorType);
        }
        if (text != null) {
            errorBuilder.text(text);
        }
        return errorBuilder.build();
    }

    private Optional<XmppError.Condition> parseKnownXmppErrorCondition(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Optional.of(XmppError.Condition.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<XmppError.Type> parseErrorType(String typeStr) {
        try {
            return Optional.of(XmppError.Type.valueOf(typeStr.toUpperCase(Locale.ROOT).replace("-", "_")));
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

            if (!isClientElementNamespace(namespace)) {
                parseExtensionElement(reader, start, name, namespace, builder::addExtension);
                continue;
            }

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

            if (!isClientElementNamespace(namespace)) {
                parseExtensionElement(reader, start, name, namespace, builder::addExtension);
                continue;
            }

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
            log.error("Invalid priority value in presence stanza");
        }
    }

    private void parseExtensionElement(XMLEventReader reader, StartElement start,
                                       String name, String namespace,
                                       Consumer<ExtensionElement> addExtension) {
        Optional<ExtensionElementProvider<?>> provider =
                ProviderRegistry.getInstance().getExtensionProvider(name, namespace);
        if (provider.isPresent()) {
            ExtensionElementProvider<?> registeredProvider = provider.orElseThrow();
            parseWithRegisteredProvider(reader, start, name, namespace, registeredProvider, addExtension);
            return;
        }
        log.debug("No provider for <{} xmlns=\"{}\">, using generic parser", name, namespace);
        parseWithGenericProvider(reader, start, name, namespace)
                .ifPresent(addExtension);
    }

    private void parseWithRegisteredProvider(XMLEventReader reader, StartElement start,
                                             String name, String namespace,
                                             ExtensionElementProvider<?> provider,
                                             Consumer<ExtensionElement> addExtension) {
        Optional<GenericExtensionElement> genericSnapshot =
                parseWithGenericProvider(reader, start, name, namespace);
        if (genericSnapshot.isEmpty()) {
            return;
        }
        GenericExtensionElement extensionSnapshot = genericSnapshot.orElseThrow();

        if (parseExtensionSnapshotWithRegisteredProvider(provider, extensionSnapshot,
                name, namespace, addExtension)) {
            return;
        }
        log.debug("Provider fallback to generic parser for <{} xmlns=\"{}\">", name, namespace);
        addExtension.accept(extensionSnapshot);
    }

    private Optional<GenericExtensionElement> parseWithGenericProvider(XMLEventReader reader, StartElement start,
                                                                       String name, String namespace) {
        try {
            return Optional.of(GenericExtensionProvider.INSTANCE.parse(reader, start));
        } catch (XmppParseException e) {
            log.error("Generic parser failed for <{} xmlns=\"{}\"> - ErrorType: {}",
                    name, namespace, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean parseExtensionSnapshotWithRegisteredProvider(ExtensionElementProvider<?> provider,
                                                                 GenericExtensionElement genericSnapshot,
                                                                 String name, String namespace,
                                                                 Consumer<ExtensionElement> addExtension) {
        XMLEventReader replayReader = null;
        try {
            replayReader = createExtensionReplayReader(genericSnapshot);
            Object parsed = provider.parse(replayReader);
            if (parsed instanceof ExtensionElement extensionElement) {
                addExtension.accept(extensionElement);
                return true;
            }
            log.warn("Provider returned non-extension result for <{} xmlns=\"{}\">", name, namespace);
            return false;
        } catch (XmppParseException | XMLStreamException e) {
            log.error("Provider failed to parse <{} xmlns=\"{}\"> - ErrorType: {}",
                    name, namespace, e.getClass().getSimpleName());
            return false;
        } finally {
            closeReaderQuietly(replayReader);
        }
    }

    private XMLEventReader createExtensionReplayReader(GenericExtensionElement genericSnapshot)
            throws XMLStreamException {
        XMLEventReader replayReader = INPUT_FACTORY.createXMLEventReader(new StringReader(genericSnapshot.toXml()));
        while (replayReader.hasNext()) {
            XMLEvent event = replayReader.nextEvent();
            if (event.isStartElement()) {
                return replayReader;
            }
        }
        closeReaderQuietly(replayReader);
        throw new XMLStreamException("Extension snapshot does not contain a start element");
    }

    private void closeReaderQuietly(XMLEventReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException e) {
            log.error("Error closing XML reader: {}", e.getMessage());
        }
    }

    private boolean isEndElement(XMLEvent event, String elementName) {
        return event.isEndElement()
                && elementName.equals(event.asEndElement().getName().getLocalPart());
    }

    private boolean isClientElementNamespace(String namespace) {
        return StringUtils.isEmpty(namespace) || XmppConstants.NS_JABBER_CLIENT.equals(namespace);
    }

    private String getAttributeValue(StartElement element, String attrName) {
        var attr = element.getAttributeByName(new QName(attrName));
        return attr != null ? attr.getValue() : null;
    }
}
