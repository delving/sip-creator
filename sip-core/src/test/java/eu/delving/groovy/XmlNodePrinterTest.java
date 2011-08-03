/*
 * Copyright 2010 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.groovy;

import groovy.util.Node;
import groovy.xml.QName;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Verify if the XmlNodePrinter prints nodes in the expected format.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class XmlNodePrinterTest {

    private static final Logger LOG = Logger.getRootLogger();

    private static final String SINGLE_EXPECTED = "<record>\n" +
            "  <europeana:uri>http://doesntexist</europeana:uri>\n" +
            "  <europeana:type>IMAGE</europeana:type>\n" +
            "</record>\n";

    private static final String DOUBLE_EXPECTED = "<record>\n" +
            "  <europeana:uri>http://doesntexist</europeana:uri>\n" +
            "  <europeana:type>IMAGE</europeana:type>\n" +
            "</record>\n" +
            "<record>\n" +
            "  <europeana:uri>http://fake</europeana:uri>\n" +
            "  <europeana:type>SOUND</europeana:type>\n" +
            "</record>\n";

    private String prefix = "europeana";
    private String namespace = "http://www.europeana.eu/schemas/ese";

    @Test
    public void testSingleNodePrint() throws Exception {
        Node rootNode = new Node(null, "record");
        rootNode.append(createChildNode("uri", "http://doesntexist"));
        rootNode.append(createChildNode("type", "IMAGE"));
        StringWriter writer = new StringWriter();
        XmlNodePrinter xmlNodePrinter = new XmlNodePrinter(new PrintWriter(writer));
        xmlNodePrinter.print(rootNode);
        LOG.info("Result :\n" + writer.toString());
        Assert.assertEquals(SINGLE_EXPECTED, writer.toString());
    }

    @Test
    public void testMultiNodePrint() throws Exception {
        StringWriter writer = new StringWriter();
        XmlNodePrinter xmlNodePrinter = new XmlNodePrinter(new PrintWriter(writer));
        Node rootNode;
        rootNode = new Node(null, "record");
        rootNode.append(createChildNode("uri", "http://doesntexist"));
        rootNode.append(createChildNode("type", "IMAGE"));
        xmlNodePrinter.print(rootNode);
        rootNode = new Node(null, "record");
        rootNode.append(createChildNode("uri", "http://fake"));
        rootNode.append(createChildNode("type", "SOUND"));
        xmlNodePrinter.print(rootNode);
        LOG.info("Result :\n" + writer.toString());
        Assert.assertEquals(DOUBLE_EXPECTED, writer.toString());
    }

    private Node createChildNode(String name, String value) {
        Node node = new Node(null, createQName(name));
        node.setValue(value);
        return node;
    }

    private QName createQName(String name) {
        return new QName(namespace, name, prefix);
    }

}
