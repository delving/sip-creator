package eu.delving;

import com.ctc.wstx.stax.WstxInputFactory;
import net.sf.saxon.xpath.XPathFactoryImpl;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class XMLToolFactory {

    public static DocumentBuilderFactory documentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    public static XPathFactory xpathFactory() {
        return new XPathFactoryImpl();
    }

    public static XMLInputFactory xmlInputFactory() {
        XMLInputFactory inputFactory = WstxInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        return inputFactory;
    }

    public static XMLOutputFactory xmlOutputFactory() {
        return XMLOutputFactory.newInstance();
    }

    public static XMLEventFactory xmlEventFactory() {
        return XMLEventFactory.newInstance();
    }

    public static SchemaFactory schemaFactory() {
        return SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
    }
}
