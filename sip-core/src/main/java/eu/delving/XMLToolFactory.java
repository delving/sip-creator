package eu.delving;

import com.ctc.wstx.stax.WstxInputFactory;
import org.apache.xerces.impl.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class XMLToolFactory {

    private static XPathFactory XPATH_FACTORY;

    static {
        try {
            XPATH_FACTORY = net.sf.saxon.xpath.XPathFactoryImpl.newInstance();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static DocumentBuilderFactory documentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    public static XPath xpath(NamespaceContext pathContext) {
        XPath path = XPATH_FACTORY.newXPath();
        path.setNamespaceContext(pathContext);
        return path;
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

    public static SchemaFactory schemaFactory(String prefix) {
        SchemaFactory schemaFactory;
        System.setProperty("javax.xml.validation.SchemaFactory:http://www.w3.org/XML/XMLSchema/v1.1", "org.apache.xerces.jaxp.validation.XMLSchema11Factory");
        schemaFactory = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
        try {
            schemaFactory.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.CTA_FULL_XPATH_CHECKING_FEATURE, true);
        }
        catch (Exception e) {
            throw new RuntimeException("Configuring schema factory", e);
        }
        return schemaFactory;
    }

    public static String serialize(Document document) {
        try {
            DOMImplementationLS ls = (DOMImplementationLS) documentBuilderFactory().newDocumentBuilder().getDOMImplementation();
            LSSerializer lss = ls.createLSSerializer();
            LSOutput lso = ls.createLSOutput();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            lso.setByteStream(outputStream);
            lss.write(document, lso);
            return new String(outputStream.toByteArray(), "UTF-8");
        }
        catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
