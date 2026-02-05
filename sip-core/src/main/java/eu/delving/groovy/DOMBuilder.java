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

package eu.delving.groovy;

import eu.delving.XMLToolFactory;
import eu.delving.metadata.RecDef;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.util.BuilderSupport;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Custom node builder which executes closures if they are found as attribute values, or if
 * an element closure returns a String or GString.
 * <p/>
 * This class is actually core to the transformation process because it gives the "Builder Pattern"
 * a very different character by executing closures for values.
 */

public class DOMBuilder extends BuilderSupport {
    public static final String CDATA_BEFORE = "<![CDATA[";
    public static final String CDATA_AFTER = "]]>";
    private static final String SCHEMA_LOCATION_ATTR = "xsi:schemaLocation";
    private static final String DEPTH = "depth";

    /**
     * BCP 47 language tag pattern.
     * Matches patterns like: en, nl, en-US, zh-Hans, de-AT, pt-BR, sr-Latn-RS
     * Based on RFC 5646: primary language (2-3 chars), optional script (4 chars),
     * optional region (2 chars or 3 digits), and optional variants/extensions.
     */
    private static final Pattern BCP47_PATTERN = Pattern.compile(
        "^[a-zA-Z]{2,3}(-[a-zA-Z]{4})?(-[a-zA-Z]{2}|-[0-9]{3})?(-[a-zA-Z0-9]{2,8})*$"
    );
    private Document document;
    private DocumentBuilder documentBuilder;
    private Map<String, RecDef.Namespace> namespaces;

    private final List<Node> allNodes = new ArrayList<>(100);
    private int depth;
    private final RecDef recDef;
    private final DOMBuilder instance = this;

    public static DOMBuilder createFor(RecDef recDef) {
        try {
            return new DOMBuilder(recDef, XMLToolFactory.documentBuilderFactory().newDocumentBuilder());
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private DOMBuilder(RecDef recDef, DocumentBuilder documentBuilder) {
        this.recDef = recDef;
        this.namespaces = recDef.getNamespaceMap();
        if (!recDef.elementFormDefaultQualified) {
            namespaces.remove(recDef.prefix);
        }
        this.documentBuilder = documentBuilder;
    }

    @Override
    protected void setParent(Object parent, Object child) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object createNode(Object name) {
        boolean rootNode = false;
        if (document == null) {
            document = documentBuilder.newDocument();
            rootNode = true;
        }
        TagValue tagValue = new TagValue(name.toString(), false);
        if (tagValue.isNamespaceAdded()) {
            Element element = document.createElementNS(tagValue.uri, tagValue.toString());
            if (rootNode) {
                StringBuilder schemaLocation = new StringBuilder();
                for (RecDef.Namespace namespace : namespaces.values()) {
                    if (namespace.schema == null) continue;
                    if (schemaLocation.length() > 0) schemaLocation.append(' ');
                    schemaLocation.append(namespace.uri).append(' ').append(namespace.schema);
                }
                if (schemaLocation.length() > 0) {
                    element.setAttributeNS(RecDef.XSI_NAMESPACE.uri, SCHEMA_LOCATION_ATTR, schemaLocation.toString());
                }
                document.appendChild(element);
            }
            return element;
        } else {
            return document.createElement(tagValue.localPart);
        }
    }

    @Override
    protected Object createNode(Object name, Object value) {
        throw new UnsupportedOperationException();
    }

    private class ElementFactory {

        final String name;
        final Map<String, Object> attributes;
        final Object contents;
        final int elementsRequired;

        private ElementFactory(String name, Map<String, Object> attributes, Object contents) {
            this.name = name;
            this.attributes = resolveAttributes(attributes);
            this.contents = resolveValue(contents);
            this.elementsRequired = calcElementsRequired(this.attributes, this.contents);
        }

        private Map<String, Object> resolveAttributes(Map<String, Object> attributes) {
            Map<String, Object> resolvedAttributes = new HashMap<>();
            if (attributes != null) {
                for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
                    Object value = attribute.getValue();
                    resolvedAttributes.put(attribute.getKey(), resolveValue(value));
                }
            }
            return resolvedAttributes;
        }

        private Object resolveValue(Object value) {
            if (value instanceof Closure) {
                Closure closure = (Closure) value;
                closure.setDelegate(instance);
                return closure.call();
            } else if (value instanceof List) {
                List values = (List) value;
                List resolvedValues = new ArrayList(values.size());
                for (Object item : values) {
                    resolvedValues.add(resolveValue(item));
                }
                return resolvedValues;
            }
            return value;
        }

        private int calcElementsRequired(Map<String, Object> attributes, Object contents) {
            int elementsRequired = 0;
            if (attributes != null) {
                for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
                    checkAttributeName(attribute.getKey());
                    Object attributeValue = attribute.getValue();

                    if (attributeValue instanceof List) {
                        elementsRequired = Math.max(elementsRequired, ((List<?>) attributeValue).size());
                    } else {
                        elementsRequired = Math.max(elementsRequired, 1);
                    }
                }
            }

            if (contents instanceof List) {
                List contentList = (List) contents;
                int textContentCount = 0;
                for(Object value : contentList) {
                    if (!(value instanceof Node)) textContentCount++;
                }
                elementsRequired = Math.max(elementsRequired, textContentCount);
            } else if (contents != null) {
                elementsRequired = Math.max(elementsRequired, 1);
            }
            return Math.max(1, elementsRequired);
        }

        private void checkAttributeName(String attrName) {
            if ("xmlns".equals(attrName)) throw new RuntimeException("Can't handle xmlns attribute");
            if (attrName.startsWith("xmlns:")) throw new RuntimeException("Can't handle attribute xmlns:*");
        }

        /**
         * Validates xml:lang attribute values against BCP 47 format.
         * @param attrName the attribute name
         * @param attrValue the attribute value to validate
         * @throws LanguageTagException if xml:lang value is invalid
         */
        private void validateXmlLang(String attrName, String attrValue) {
            if ("xml:lang".equals(attrName)) {
                if (attrValue == null || attrValue.isEmpty()) {
                    throw new LanguageTagException(
                        LanguageTagException.Type.INVALID_FORMAT,
                        name,
                        attrValue,
                        "Invalid xml:lang value: empty or null - must be a valid BCP 47 language tag (e.g., 'en', 'nl', 'en-US')"
                    );
                }
                if (!BCP47_PATTERN.matcher(attrValue).matches()) {
                    throw new LanguageTagException(
                        LanguageTagException.Type.INVALID_FORMAT,
                        name,
                        attrValue,
                        String.format(
                            "Invalid xml:lang value '%s' - must be a valid BCP 47 language tag (e.g., 'en', 'nl', 'en-US')",
                            attrValue.length() > 50 ? attrValue.substring(0, 50) + "..." : attrValue
                        )
                    );
                }
            }
        }

        private Object extractValue(Object value, int index) {
            if (value == null) return null;
            if (value instanceof List) {
                List values = (List) value;
                if (values.isEmpty()) return null;
                if (index >= values.size()) return values.get(0);
                return values.get(index);
            }
            return value;
        }

        public List<Element> createElements() {
            List<Element> elements = new ArrayList<>(elementsRequired);
            for (int i = 0; i < elementsRequired; i++) {
                Element element = (Element) createNode(name);
                String xmlLangValue = null;

                for (String attributeName : attributes.keySet()) {
                    Object attributeValue = extractValue(attributes.get(attributeName), i);
                    if (attributeValue == null) continue;

                    String attrValueStr = attributeValue.toString();

                    // Validate xml:lang attribute values
                    validateXmlLang(attributeName, attrValueStr);

                    // Track xml:lang for empty content validation
                    if ("xml:lang".equals(attributeName)) {
                        xmlLangValue = attrValueStr;
                    }

                    TagValue tagValue = new TagValue(attributeName, true);
                    element.setAttributeNS(
                        tagValue.isNamespaceAdded() ? tagValue.uri : null,
                        tagValue.isNamespaceAdded() ? tagValue.toString() : tagValue.localPart,
                        attrValueStr
                    );
                }

                Object contentValue = extractValue(contents, i);
                if(contentValue instanceof String || contentValue instanceof GString) {
                    if(contentValue != null) {
                        toTextNodes(contentValue.toString(), element);
                    }
                }

                // Validate that elements with xml:lang have non-empty content
                if (xmlLangValue != null) {
                    boolean hasContent = contentValue != null &&
                        (contentValue instanceof String || contentValue instanceof GString) &&
                        !contentValue.toString().trim().isEmpty();

                    if (!hasContent) {
                        throw new LanguageTagException(
                            LanguageTagException.Type.EMPTY_CONTENT,
                            name,
                            xmlLangValue,
                            String.format(
                                "Element '%s' has xml:lang='%s' but no content - either add content or remove the language tag",
                                name, xmlLangValue
                            )
                        );
                    }
                }

                elements.add(element);
            }

            return elements;
        }
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        ElementFactory elementFactory = new ElementFactory(name.toString(), attributes, value);
        return elementFactory.createElements();
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        return createNode(name, attributes, null);
    }

    private <T> T firstInstanceOf(Class<T> type, Object... objects) {
        for (Object obj : objects) {
            if (obj == null) continue;
            if (type.isAssignableFrom(obj.getClass())) return (T) obj;
        }
        return null;
    }

    @Override
    protected Object doInvokeMethod(String methodName, Object name, Object argsObj) {
        Object[] args = (Object[]) argsObj;
        Closure contentClosure = firstInstanceOf(Closure.class, args);

        depth++;
        Map<String, Object> attributes = firstInstanceOf(Map.class, args);
        List<Node> nodes = (List<Node>) createNode(methodName, attributes, contentClosure);
        for (Node node : nodes) node.setUserData(DEPTH, depth, null);
        allNodes.addAll(nodes);
        depth--;

        if (depth == 0) {
            Collections.reverse(allNodes);
            Map<Integer, Node> parentNodes = new HashMap<>();
            for(Node next : allNodes) {
                int nodeDepth = (int) next.getUserData(DEPTH);
                parentNodes.put(nodeDepth, next);
                Node parentNode = parentNodes.get(nodeDepth - 1);
                if (parentNode != null) {
                    if (parentNode.hasChildNodes()) {
                        parentNode.insertBefore(next, parentNode.getFirstChild());
                    } else {
                        parentNode.appendChild(next);
                    }
                }
            }
        }

        if (nodes.size() == 1) return nodes.get(0);
        return nodes;
    }

    private void toTextNodes(String text, Node parent) {
        while (!text.isEmpty()) {
            int before = text.indexOf(CDATA_BEFORE);
            if (before < 0) {
                parent.appendChild(document.createTextNode(text));
                break; // finished
            }
            if (before > 0) {
                parent.appendChild(document.createTextNode(text.substring(0, before)));
                text = text.substring(before);
            } else { // starts with CDATA
                text = text.substring(CDATA_BEFORE.length());
                int after = text.indexOf(CDATA_AFTER);
                if (after < 0) throw new RuntimeException("No CDATA terminator");
                String cdata = text.substring(0, after);
                parent.appendChild(document.createCDATASection(cdata));
                text = text.substring(cdata.length() + CDATA_AFTER.length());
            }
        }
    }

    private class TagValue {
        final String prefix;
        final String uri;
        final String localPart;
        final boolean attribute;

        public TagValue(String name, boolean attribute) {
            int colon = name.indexOf(':');
            if (colon > 0) {
                this.prefix = name.substring(0, colon);
                RecDef.Namespace namespace = namespaces.get(this.prefix);
                if (namespace == null) {
                    throw new RuntimeException("No namespace for " + prefix);
                }
                this.uri = namespace.uri;
                this.localPart = name.substring(colon + 1);
            } else {
                this.prefix = null;
                this.uri = null;
                this.localPart = name;
            }
            this.attribute = attribute;
        }

        public boolean isNamespaceAdded() {
            if (prefix != null && !prefix.equals(recDef.prefix)) return true;
            if (attribute) {
                return recDef.attributeFormDefaultQualified;
            } else {
                return recDef.elementFormDefaultQualified;
            }
        }

        public String toString() {
            return prefix == null ? localPart : prefix + ":" + localPart;
        }
    }
}
