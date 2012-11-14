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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import static eu.delving.metadata.RecDef.*;

/**
 * The result of the mapping engine is wrapped in this class so that some post-processing and checking
 * can be done on the resulting Node tree.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingResultImpl implements MappingResult {
    private Logger logger = Logger.getLogger(getClass());
    private XmlSerializer serializer;
    private Map<String, List<String>> allFields = new TreeMap<String, List<String>>();
    private Map<String, List<String>> copyFields = new TreeMap<String, List<String>>();
    private Map<String, List<String>> searchFields = new TreeMap<String, List<String>>();
    private Node root, rootAugmented;
    private RecDefTree recDefTree;

    public MappingResultImpl(XmlSerializer serializer, Node root, RecDefTree recDefTree) {
        this.serializer = serializer;
        this.root = root;
        this.recDefTree = recDefTree; // todo: if null!!
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public Node rootAugmented() {
        return rootAugmented;
    }

    @Override
    public Map<String, List<String>> fields() {
        return allFields;
    }

    @Override
    public Map<String, List<String>> copyFields() {
        return copyFields;
    }

    @Override
    public Map<String, List<String>> searchFields() {
        return searchFields;
    }

    @Override
    public void checkMissingFields() throws MissingFieldsException {
        if (!isRecDefDelvingAware()) return;
        Set<String> missing = new TreeSet<String>();
        Set<String> keys = copyFields.keySet();
        for (String required : REQUIRED_FIELDS) {
            if (!keys.contains(required)) missing.add(required);
        }
        if (missing.isEmpty()) return;
        if (missing.size() == 1 && (missing.contains(LANDING_PAGE) || missing.contains(THUMBNAIL))) {
            return; // ok, only need one of these two
        }
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

    @Override
    public String toXml() {
        return serializer.toXml(root, recDefTree != null);
    }

    @Override
    public String toXmlAugmented() {
        return serializer.toXml(rootAugmented, true);
    }

    public MappingResult resolve() {
        if (recDefTree.getRecDef().flat) {
            resolveFlatRecord();
        }
        else if (recDefTree.getRecDef().prefix.equals("aff")) {
            resolveAFFRecord();
        }
        if (isRecDefDelvingAware()) {
            rootAugmented = root.cloneNode(true);
            Document document = rootAugmented.getOwnerDocument();
            for (Map.Entry<String, List<String>> field : copyFields.entrySet()) {
                for (String value : field.getValue()) {
                    Element freshElement = document.createElementNS(DELVING_NAMESPACE_URI, field.getKey());
                    if (!isDuplicate(freshElement, value)) {
                        Node rootChild = rootAugmented.appendChild(freshElement);
                        rootChild.appendChild(document.createTextNode(value));
                    }
                }
            }
        }
        else {
            rootAugmented = root;
        }
        return this;
    }

    public String toString() {
        return toXml();
    }

    private boolean isDuplicate(Element element, String value) {
        NodeList kids = rootAugmented.getChildNodes();
        for (int walk = 0; walk < kids.getLength(); walk++) {
            Node kid = kids.item(walk);
            switch (kid.getNodeType()) {
                case Node.ELEMENT_NODE:
                    boolean samePrefix = kid.getPrefix().equals(element.getPrefix());
                    boolean sameLocalName = kid.getLocalName().equals(element.getLocalName());
                    if (samePrefix && sameLocalName) {
                        Node text = kid.getFirstChild();
                        if (value.equals(text.getTextContent())) return true;
                    }
            }
        }
        return false;
    }

    private boolean isRecDefDelvingAware() {
        return recDefTree.getRecDef().getNamespaceMap().containsKey(DELVING_PREFIX);
    }

    private void resolveAFFRecord() {
        resolveAFFRecord((Element) root);
    }

    private void resolveFlatRecord() {
        NodeList kids = root.getChildNodes();
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
                    String name = String.format("%s_%s_%s", kid.getPrefix(), kid.getLocalName(), recDefNode.getFieldType());
                    String value = getTextFromChildren(kid);
                    put(name, value);
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
                        String name = String.format("%s_%s_%s", kid.getPrefix(), kid.getLocalName(), recDefNode.getFieldType());
                        String value = getTextFromChildren(kid);
                        put(name, value);
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
            if (fieldMarker.name.startsWith(DELVING_PREFIX)) {
                putCopyField(fieldMarker.name, value);
            }
            else if ("search".equals(fieldMarker.type)) {
                putSearchField(fieldMarker.name, value);
            }
        }
    }

    private void putCopyField(String key, String value) {
        List<String> list = copyFields.get(key);
        if (list == null) copyFields.put(key, list = new ArrayList<String>(4));
        list.add(value);
    }

    private void putSearchField(String key, String value) {
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
            if (el.getLocalName() == null) break;
            path = path.child(Tag.element(el.getPrefix(), el.getLocalName(), key));
        }
        RecDefNode recDefNode = recDefTree.getRecDefNode(path);
        if (recDefNode == null) {
            throw new RuntimeException("No recdef node for " + path);
        }
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
}
