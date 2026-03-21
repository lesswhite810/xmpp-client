package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.util.XmlParserUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GenericExtensionProviderTest {

    @Test
    void testParseCurrentElementParsesAttributesAndText() throws Exception {
        XMLEventReader reader = newReader("""
                <query xmlns="urn:test" node="root">alpha</query>
                """);

        GenericExtensionElement element = GenericExtensionProvider.INSTANCE.parseCurrentElement(reader);

        assertEquals("query", element.getElementName());
        assertEquals("urn:test", element.getNamespace());
        assertEquals("root", element.getAttributeValue("node"));
        assertEquals("alpha", element.getText());
    }

    @Test
    void testParseCurrentElementPreservesNamespacedAttributes() throws Exception {
        XMLEventReader reader = newReader("""
                <query xmlns="urn:test" xmlns:a="urn:test:a" xmlns:b="urn:test:b"
                       id="root" a:id="first" b:id="second"/>
                """);

        GenericExtensionElement element = GenericExtensionProvider.INSTANCE.parseCurrentElement(reader);

        assertEquals("root", element.getAttributeValue("id"));
        assertEquals("first", element.getAttributeValue("urn:test:a", "id"));
        assertEquals("second", element.getAttributeValue("urn:test:b", "id"));
        assertEquals(3, element.getAttributes().size());
    }

    @Test
    void testParseCurrentElementPreservesMixedContentOrder() throws Exception {
        XMLEventReader reader = newReader("""
                <query xmlns="urn:test">before<item xmlns="urn:test:child">value</item>after</query>
                """);

        GenericExtensionElement element = GenericExtensionProvider.INSTANCE.parseCurrentElement(reader);

        assertEquals(3, element.getContentNodes().size());
        assertEquals("beforeafter", element.getText());
        assertEquals("before<item xmlns=\"urn:test:child\">value</item>after",
                element.toXml().replace("<query xmlns=\"urn:test\">", "").replace("</query>", ""));
    }

    @Test
    void testParseCurrentElementFailsWhenReaderIsNotAtStartElement() throws Exception {
        XMLEventReader reader = newReader("<query/>");
        reader.nextEvent();
        reader.nextEvent();

        XmppParseException exception = assertThrows(XmppParseException.class,
                () -> GenericExtensionProvider.INSTANCE.parseCurrentElement(reader));

        assertTrue(exception.getMessage().contains("start element"));
    }

    @Test
    void testParseWrapsXmlErrors() throws Exception {
        XMLEventReader reader = Mockito.mock(XMLEventReader.class);
        StartElement startElement = newReader("<query/>").peek().asStartElement();
        when(reader.hasNext()).thenReturn(true);
        when(reader.nextEvent()).thenThrow(new XMLStreamException("boom"));

        XmppParseException exception = assertThrows(XmppParseException.class,
                () -> GenericExtensionProvider.INSTANCE.parse(reader, startElement));

        assertTrue(exception.getMessage().contains("Failed to parse generic element"));
        assertTrue(exception.getCause() instanceof XMLStreamException);
    }

    @Test
    void testParseFailsOnMismatchedEndElement() throws Exception {
        XMLEventReader reader = Mockito.mock(XMLEventReader.class);
        StartElement startElement = newReader("<query/>").peek().asStartElement();
        XMLEvent mismatchedEndEvent = Mockito.mock(XMLEvent.class);
        EndElement endElement = Mockito.mock(EndElement.class);
        when(mismatchedEndEvent.isEndElement()).thenReturn(true);
        when(mismatchedEndEvent.asEndElement()).thenReturn(endElement);
        when(endElement.getName()).thenReturn(new QName("item"));
        when(reader.hasNext()).thenReturn(true);
        when(reader.nextEvent()).thenReturn(mismatchedEndEvent);

        XmppParseException exception = assertThrows(XmppParseException.class,
                () -> GenericExtensionProvider.INSTANCE.parse(reader, startElement));

        assertTrue(exception.getMessage().contains("Failed to parse generic element"));
        assertTrue(exception.getCause() instanceof XMLStreamException);
        assertTrue(exception.getCause().getMessage().contains("item"));
    }

    @Test
    void testParseFailsOnMismatchedEndElementNamespace() throws Exception {
        XMLEventReader reader = Mockito.mock(XMLEventReader.class);
        StartElement startElement = newReader("<query xmlns='urn:test'/>").peek().asStartElement();
        XMLEvent mismatchedEndEvent = Mockito.mock(XMLEvent.class);
        EndElement endElement = Mockito.mock(EndElement.class);
        when(mismatchedEndEvent.isEndElement()).thenReturn(true);
        when(mismatchedEndEvent.asEndElement()).thenReturn(endElement);
        when(endElement.getName()).thenReturn(new QName("urn:other", "query"));
        when(reader.hasNext()).thenReturn(true);
        when(reader.nextEvent()).thenReturn(mismatchedEndEvent);

        XmppParseException exception = assertThrows(XmppParseException.class,
                () -> GenericExtensionProvider.INSTANCE.parse(reader, startElement));

        assertTrue(exception.getMessage().contains("Failed to parse generic element"));
        assertTrue(exception.getCause() instanceof XMLStreamException);
        assertTrue(exception.getCause().getMessage().contains("urn:other"));
    }

    @Test
    void testParseFailsWhenMatchingEndElementIsMissing() throws Exception {
        XMLEventReader reader = Mockito.mock(XMLEventReader.class);
        StartElement startElement = newReader("<query/>").peek().asStartElement();
        when(reader.hasNext()).thenReturn(false);

        XmppParseException exception = assertThrows(XmppParseException.class,
                () -> GenericExtensionProvider.INSTANCE.parse(reader, startElement));

        assertTrue(exception.getMessage().contains("Failed to parse generic element"));
        assertTrue(exception.getCause() instanceof XMLStreamException);
        assertTrue(exception.getCause().getMessage().contains("<query>"));
    }

    @Test
    void testParsePreservesWhitespaceOnlyCharacterFragments() throws Exception {
        XMLEventReader reader = Mockito.mock(XMLEventReader.class);
        StartElement startElement = newReader("<query xmlns='urn:test'/>").peek().asStartElement();
        XMLEvent textEvent1 = mockCharactersEvent("foo");
        XMLEvent textEvent2 = mockCharactersEvent(" ");
        XMLEvent textEvent3 = mockCharactersEvent("bar");
        XMLEvent endEvent = mockEndEvent(new QName("urn:test", "query"));
        when(reader.hasNext()).thenReturn(true, true, true, true);
        when(reader.nextEvent()).thenReturn(textEvent1, textEvent2, textEvent3, endEvent);

        GenericExtensionElement element = GenericExtensionProvider.INSTANCE.parse(reader, startElement);

        assertEquals("foo bar", element.getText());
        assertEquals(3, element.getContentNodes().size());
    }

    private XMLEvent mockCharactersEvent(String text) {
        XMLEvent event = Mockito.mock(XMLEvent.class);
        Characters characters = Mockito.mock(Characters.class);
        when(event.isCharacters()).thenReturn(true);
        when(event.asCharacters()).thenReturn(characters);
        when(characters.getData()).thenReturn(text);
        return event;
    }

    private XMLEvent mockEndEvent(QName name) {
        XMLEvent event = Mockito.mock(XMLEvent.class);
        EndElement endElement = Mockito.mock(EndElement.class);
        when(event.isEndElement()).thenReturn(true);
        when(event.asEndElement()).thenReturn(endElement);
        when(endElement.getName()).thenReturn(name);
        return event;
    }

    private XMLEventReader newReader(String xml) throws XMLStreamException {
        XMLInputFactory factory = XmlParserUtils.createInputFactory();
        XMLEventReader reader = factory.createXMLEventReader(new StringReader(xml));
        while (reader.hasNext() && !reader.peek().isStartElement()) {
            reader.nextEvent();
        }
        return reader;
    }
}
