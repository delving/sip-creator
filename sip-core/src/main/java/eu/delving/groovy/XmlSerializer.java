/*
 * Copyright 2011 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.groovy;

import org.w3c.dom.*;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Here we handle turning a DOM node into an XML Document
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class XmlSerializer {

    public static String toXml(Node node) {
        if (node == null) return "<?xml?>";
        DOMImplementation domImplementation = node.getOwnerDocument().getImplementation();
        Map<String,String> namespaces = new TreeMap<String, String>();
        gatherNamespaces(node, namespaces);
        Element element = (Element) node;
        for (Map.Entry<String,String> entry : namespaces.entrySet()) {
            if (entry.getValue().equals(element.getNamespaceURI())) continue;
            String xmlns = String.format("xmlns:%s", entry.getKey());
            if (element.getAttributeNode(xmlns) == null) element.setAttributeNS("http://www.w3.org/2000/xmlns/", xmlns, entry.getValue());
        }
        if (domImplementation.hasFeature("LS", "3.0") && domImplementation.hasFeature("Core", "2.0")) {
            DOMImplementationLS domImplementationLS = (DOMImplementationLS) domImplementation.getFeature("LS", "3.0");
            LSSerializer lsSerializer = domImplementationLS.createLSSerializer();
            DOMConfiguration domConfiguration = lsSerializer.getDomConfig();
            if (domConfiguration.canSetParameter("format-pretty-print", Boolean.TRUE)) {
                lsSerializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
                LSOutput lsOutput = domImplementationLS.createLSOutput();
                lsOutput.setEncoding("UTF-8");
                StringWriter stringWriter = new StringWriter();
                lsOutput.setCharacterStream(stringWriter);
                lsSerializer.write(node, lsOutput);
                return stringWriter.toString();
            }
            else {
                throw new RuntimeException("DOMConfiguration 'format-pretty-print' parameter isn't settable.");
            }
        }
        else {
            throw new RuntimeException("DOM 3.0 LS and/or DOM 2.0 Core not supported.");
        }
    }

    private static void gatherNamespaces(Node node, Map<String,String> namespaces) {
        if (node.getNamespaceURI() != null) namespaces.put(node.getPrefix(), node.getNamespaceURI());
        NodeList list = node.getChildNodes();
        for (int walk=0; walk<list.getLength(); walk++) {
            Node sub = list.item(walk);
            gatherNamespaces(sub, namespaces);
        }
    }
    
    public static String toXml(GroovyNode node) {
        StringWriter writer = new StringWriter();
        XmlNodePrinter xmlNodePrinter = new XmlNodePrinter(new PrintWriter(writer));
        xmlNodePrinter.print(node);
        return writer.toString();
    }
}
