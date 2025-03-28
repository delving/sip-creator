/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving;

import com.ctc.wstx.stax.WstxInputFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class XMLToolFactory {

    private static Logger LOG = LoggerFactory.getLogger(XMLToolFactory.class);
    private static XPathFactory XPATH_FACTORY;

    static {
        try {
            XPATH_FACTORY = net.sf.saxon.xpath.XPathFactoryImpl.newInstance();
        }
        catch (Exception e) {
            LOG.error("Error initializing Xpath? ", e);
            throw new RuntimeException(e);
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
//        System.setProperty("javax.xml.validation.SchemaFactory:http://www.w3.org/XML/XMLSchema/v1.1", "org.apache.xerces.jaxp.validation.XMLSchema11Factory");
        System.setProperty("javax.xml.validation.SchemaFactory:http://www.w3.org/XML/XMLSchema/v1.0", "org.apache.xerces.jaxp.validation.XMLSchemaFactory");
        schemaFactory = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.0");
//        try {
//            schemaFactory.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.CTA_FULL_XPATH_CHECKING_FEATURE, true);
//        }
//        catch (Exception e) {
//            throw new RuntimeException("Configuring schema factory", e);
//        }
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
