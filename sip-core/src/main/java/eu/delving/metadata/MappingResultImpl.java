/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.metadata;

import eu.delving.MappingResult;
import eu.delving.groovy.XmlSerializer;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * The result of the mapping engine
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingResultImpl implements MappingResult {
    private Logger logger = Logger.getLogger(getClass());
    private XmlSerializer serializer;
    private Map<String, List<String>> allFields = new TreeMap<String, List<String>>();
    private Map<String, List<String>> systemFields = new TreeMap<String, List<String>>();
    private Map<String, List<String>> searchFields = new TreeMap<String, List<String>>();
    private Node node;
    private RecDefTree recDefTree;

    public MappingResultImpl(XmlSerializer serializer, Node node, RecDefTree recDefTree) {
        this.serializer = serializer;
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

    @Override
    public Set<String> missingSystemFields() {
        Set<String> missing = new TreeSet<String>(Arrays.asList(REQUIRED_SYSTEM_FIELDS));
        missing.removeAll(systemFields.keySet());
        return missing;
    }

    @Override
    public void checkMissingFields() throws MissingFieldsException {
        Set<String> missing = missingSystemFields();
        if (missing.isEmpty()) return;
        Map<String, Path> missingMap = new TreeMap<String, Path>();
        for (RecDef.FieldMarker fieldMarker : recDefTree.getRecDef().fieldMarkers) {
            if (fieldMarker.name == null || fieldMarker.type != null) continue;
            if (missing.contains(fieldMarker.name)) missingMap.put(fieldMarker.name, fieldMarker.path);
        }
        StringBuilder out = new StringBuilder("Required fields missing: ");
        for (Map.Entry<String, Path> entry : missingMap.entrySet()) {
            out.append(String.format("%s (%s) ", entry.getKey(), entry.getValue()));
        }
        throw new MissingFieldsException(out.toString());
    }

    public MappingResult resolve() {
        if (recDefTree.getRecDef().flat) {
            resolveFlatRecord();
        }
        else if (recDefTree.getRecDef().prefix.equals("aff")) {
            resolveAFFRecord();
        }
        return this;
    }

    public String toString() {
        return serializer.toXml(node);
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
                    logger.warn("Attribute appeared as child of the root node: " + kid.getNodeName());
                    break;
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    RecDefNode recDefNode = getRecDefNode((Element) kid);
                    String value = getTextFromChildren(kid);
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
                    logger.warn("Attribute node appeared while resolving AFF: " + kid.getNodeName());
                    break;
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    RecDefNode recDefNode = getRecDefNode((Element) kid);
                    if (recDefNode.isLeafElem()) {
                        String value = getTextFromChildren(kid);
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
        for (RecDef.FieldMarker fieldMarker : recDefNode.getFieldMarkers()) {
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
        Path path = Path.create().child(recDefTree.getRecDef().root.tag);
        List<Element> elements = new ArrayList<Element>();
        while (element.getParentNode() != null) {
            elements.add(0, element);
            element = (Element) element.getParentNode();
        }
        for (Element el : elements) {
            String key = el.getAttribute("aff:key");
            path = path.child(Tag.element(el.getPrefix(), el.getLocalName(), key));
        }
        RecDefNode recDefNode = recDefTree.getRecDefNode(path);
        if (recDefNode == null) throw new RuntimeException("No recdef node for " + path);
        return recDefNode;
    }

    private String getTextFromChildren(Node parent) {
        StringBuilder text = new StringBuilder();
        NodeList kids = parent.getChildNodes();
        for (int walk = 0; walk < kids.getLength(); walk++) {
            Node kid = kids.item(walk);
            switch (kid.getNodeType()) {
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    text.append(kid.getNodeValue());
                    break;
            }
        }
        return text.toString();
    }

    private static final String[] REQUIRED_SYSTEM_FIELDS = {
            "TITLE",
            "DESCRIPTION",
            "PROVIDER",
            "OWNER",
            "LANDING_PAGE"
    };
}
