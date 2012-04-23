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

package eu.delving.sip;

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wrapping the mapping mechanism for easy access from Scala
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MappingEngine {
    private MetadataRecordFactory metadataRecordFactory;
    private MappingRunner mappingRunner;

    public interface Result {

        Node root();

        Map<String, List<String>> fields();

        Map<String, List<String>> systemFields();

        Map<String, List<String>> searchFields();
    }

    public MappingEngine(String mapping, ClassLoader classLoader, RecDefModel recDefModel, Map<String, String> namespaces) throws FileNotFoundException, MetadataException {
        RecMapping recMapping = RecMapping.read(new StringReader(mapping), recDefModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(classLoader);
        mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null);
        metadataRecordFactory = new MetadataRecordFactory(namespaces);
    }

    public String getCode() {
        return mappingRunner.getCode();
    }

    public Result execute(String recordXML) throws XMLStreamException, MappingException {
        MetadataRecord metadataRecord = metadataRecordFactory.fromXml(recordXML);
        return new ResultImpl(mappingRunner.runMapping(metadataRecord), mappingRunner.getRecDefTree()).resolve();
    }

    private class ResultImpl implements Result {
        private Map<String, List<String>> allFields = new TreeMap<String, List<String>>();
        private Map<String, List<String>> systemFields = new TreeMap<String, List<String>>();
        private Map<String, List<String>> searchFields = new TreeMap<String, List<String>>();
        private Node node;
        private RecDefTree recDefTree;

        private ResultImpl(Node node, RecDefTree recDefTree) {
            this.node = node;
            this.recDefTree = recDefTree;
        }

        @Override
        public Node root() {
            return node;
        }

        @Override
        public Map<String, List<String>> fields() {
            return allFields;
        }

        @Override
        public Map<String, List<String>> systemFields() {
            return systemFields;
        }

        @Override
        public Map<String, List<String>> searchFields() {
            return searchFields;
        }

        public Result resolve() {
            if (recDefTree.getRecDef().flat) {
                resolveFlatRecord();
            }
            else if (recDefTree.getRecDef().prefix.equals("aff")) {
                resolveAFFRecord();
            }
            return this;
        }

        private void resolveAFFRecord() {
            resolveAFFRecord((Element) node);
        }

        private void resolveFlatRecord() {
            NodeList kids = node.getChildNodes();
            for (int walk = 0; walk < kids.getLength(); walk++) {
                Node kid = kids.item(walk);
                switch (kid.getNodeType()) {
                    case Node.ATTRIBUTE_NODE:
                        throw new RuntimeException("Attributes not implemented");
                    case Node.TEXT_NODE:
                        throw new RuntimeException("Text Nodes not implemented");
                    case Node.ELEMENT_NODE:
                        Node textNode = getTextNode(kid);
                        RecDefNode recDefNode = getRecDefNode((Element) kid);
                        String value = textNode.getNodeValue();
                        put(String.format("%s_%s_%s", kid.getPrefix(), kid.getLocalName(), recDefNode.getFieldType()), value);
                        handleMarkedField(recDefNode, value);
                        break;
                    default:
                        throw new RuntimeException("Node type not implemented: " + kid.getNodeType());
                }
            }
        }

        private void resolveAFFRecord(Element element) {
            NodeList kids = element.getChildNodes();
            for (int walk = 0; walk < kids.getLength(); walk++) {
                Node kid = kids.item(walk);
                switch (kid.getNodeType()) {
                    case Node.ATTRIBUTE_NODE:
                        throw new RuntimeException("Attributes not implemented");
                    case Node.TEXT_NODE:
                        throw new RuntimeException("Text Nodes not implemented");
                    case Node.ELEMENT_NODE:
                        RecDefNode recDefNode = getRecDefNode((Element)kid);
                        if (recDefNode.isLeafElem()) {
                            Node textNode = getTextNode(kid);
                            if (textNode == null) throw new RuntimeException("No text subnode for content of "+recDefNode);
                            String value = textNode.getNodeValue();
                            put(String.format("%s_%s_%s", kid.getPrefix(), kid.getLocalName(), recDefNode.getFieldType()), value);
                            handleMarkedField(recDefNode, value);
                        }
                        else {
                            resolveAFFRecord((Element) kid);
                        }
                        break;
                    default:
                        throw new RuntimeException("Node type not implemented: " + kid.getNodeType());
                }
            }
        }

        private void handleMarkedField(RecDefNode recDefNode, String value) {
            RecDef.FieldMarker fieldMarker = recDefNode.getFieldMarker();
            if (fieldMarker != null) {
                if (fieldMarker.type == null) {
                    putSystem(fieldMarker.name, value);
                }
                else if ("search".equals(fieldMarker.type)) {
                    putSearch(fieldMarker.name, value);
                }
            }
        }

        private void putSystem(String key, String value) {
            put(systemFields, key, value);
        }

        private void putSearch(String key, String value) {
            put(searchFields, key, value);
        }

        private void put(String key, String value) {
            put(allFields, key, value);
        }

        private void put(Map<String, List<String>> map, String key, String value) {
            List<String> list = map.get(key);
            if (list == null) map.put(key, list = new ArrayList<String>(4));
            list.add(value);
        }

        private RecDefNode getRecDefNode(Element element) {
            Path path = Path.create().child(Tag.element(recDefTree.getRecDef().prefix, "record", null));
            List<Element> elements = new ArrayList<Element>();
            while (element.getParentNode() != null) {
                elements.add(0, element);
                element = (Element) element.getParentNode();
            }
            for (Element el : elements) path = path.child(Tag.element(el.getPrefix(), el.getLocalName(), null));
            RecDefNode recDefNode = recDefTree.getRecDefNode(path);
            if (recDefNode == null) throw new RuntimeException("No recdef node for " + path);
            return recDefNode;
        }

        private Node getTextNode(Node parent) {
            NodeList kids = parent.getChildNodes();
            if (kids.getLength() != 1) throw new RuntimeException("Expected only one grandchild node");
            Node textNode = kids.item(0);
            if (textNode.getNodeType() != Node.TEXT_NODE) throw new RuntimeException("Expected text grandchild node");
            return textNode;
        }

        private void dump(Node node, int level) {
            NodeList childNodes = node.getChildNodes();
            for (int walk = 0; walk < childNodes.getLength(); walk++) {
                Node child = childNodes.item(walk);
                String type = "?";
                switch (child.getNodeType()) {
                    case Node.ATTRIBUTE_NODE:
                        type = "attr";
                        break;
                    case Node.ELEMENT_NODE:
                        type = "elem";
                        break;
                    case Node.TEXT_NODE:
                        type = "text";
                        break;
                }
                for (int count = 0; count < level; count++) System.out.print('\t');
                String string = String.format("%d: %s: %s = %s", level, type, child.getNodeName(), child.getNodeValue());
                System.out.println(string);
                dump(child, level + 1);
            }
        }
    }
}
