package com.example.xmpp.protocol.provider;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.protocol.model.GenericExtensionElement;
import com.example.xmpp.util.XmlParserUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
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

    private XMLEventReader newReader(String xml) throws XMLStreamException {
        XMLInputFactory factory = XmlParserUtils.createInputFactory();
        XMLEventReader reader = factory.createXMLEventReader(new StringReader(xml));
        while (reader.hasNext() && !reader.peek().isStartElement()) {
            reader.nextEvent();
        }
        return reader;
    }
}
