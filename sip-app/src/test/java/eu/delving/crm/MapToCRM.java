/*
 * Copyright 2013 Delving BV
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

package eu.delving.crm;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.*;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.XppDriver;
import eu.delving.XMLToolFactory;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MapToCRM {
    private static Logger LOG = Logger.getLogger(MapToCRM.class);
    private static final XPathFactory PATH_FACTORY = XMLToolFactory.xpathFactory();
    private static final NamespaceContext NAMESPACE_CONTEXT = new XPathContext(new String[][]{
            {"lido", "http://www.lido-schema.org"}
    });

    public static Mappings readMappings(InputStream inputStream) throws IOException, ParserConfigurationException, SAXException {
        return (Mappings) stream().fromXML(inputStream);
    }

    public static String toString(Mappings mappings) {
        return stream().toXML(mappings);
    }

    private static XStream stream() {
        XStream xstream = new XStream(new PureJavaReflectionProvider(), new XppDriver(new NoNameCoder()));
        xstream.setMode(XStream.NO_REFERENCES);
        xstream.processAnnotations(Mappings.class);
        return xstream;
    }

    private static XPath path() {
        XPath path = PATH_FACTORY.newXPath();
        path.setNamespaceContext(NAMESPACE_CONTEXT);
        return path;
    }

    @XStreamAlias("mappings")
    public static class Mappings {
        @XStreamAsAttribute
        public String version;

        @XStreamAsAttribute
        public String uriPolicyClass;

        @XStreamImplicit
        public List<Mapping> mappings;

        public Graph toGraph(Element element, Func func) {
            Graph graph = new Graph();
            for (Mapping mapping : mappings) {
                mapping.apply(graph, element, func);
            }
            return graph;
        }
    }

    @XStreamAlias("mapping")
    public static class Mapping {
        public Domain domain;

        @XStreamImplicit
        public List<Link> links;

        public void apply(Graph graph, Element element, Func func) {
            domain.entity.resolveAt(element, func);
            LOG.info("Domain: " + domain.entity);
            for (Link link : links) {
                link.apply(graph, element, domain, func);
            }
        }
    }

    @XStreamAlias("domain")
    public static class Domain {
        public String source;

        public Entity entity;
    }

    @XStreamAlias("entity")
    public static class Entity {
        @XStreamAsAttribute
        public CRMEntity tag;

        @XStreamAsAttribute
        public String binding;

        @XStreamAlias("exists")
        public Exists exists;

        @XStreamAlias("uri_function")
        public URIFunction uriFunction;

        @XStreamOmitField
        public String uri;

        public boolean resolveAt(Element element, Func func) {
            if (exists != null && !exists.resolveAt(element)) return false;
            uri = uriFunction.executeAt(element, func);
            return true;
        }
    }

    @XStreamAlias("property")
    public static class Property {
        @XStreamAsAttribute
        public CRMProperty tag;

        @XStreamAlias("exists")
        public Exists exists;

        public boolean resolveAt(Element element) {
            return exists == null || exists.resolveAt(element);
        }
    }

    @XStreamAlias("link")
    public static class Link {
        public Path path;

        public Range range;

        public void apply(Graph graph, Element element, Domain domain, Func func) {
            path.apply(graph, element, domain, func);
            range.apply(graph, element, path, domain, func);
        }
    }

    @XStreamAlias("range")
    public static class Range {
        public String source;

        public Entity entity;

        @XStreamAlias("additional_node")
        public AdditionalNode additionalNode;

        public void apply(Graph graph, Element element, Path path, Domain domain, Func func) {
            // todo: stick source on top of domain source, for a new element, not this one
            entity.resolveAt(element, func);
            if (entity.uri == null) return;
            // todo: domain-path-range is a triple!
            if (additionalNode != null) {
                additionalNode.apply(graph, element, entity, func);
            }
        }
    }

    @XStreamAlias("path")
    public static class Path {
        public String source;

        public Property property;

        @XStreamAlias("internal_node")
        public InternalNode internalNode;

        public void apply(Graph graph, Element element, Domain domain, Func func) {
            if (!property.resolveAt(element)) return;
            LOG.info(property);
            if (internalNode != null) {
                internalNode.apply(graph, element, domain, property, func);
            }
            // todo: implement
        }
    }

    @XStreamAlias("additional_node")
    public static class AdditionalNode {
        public Property property;
        public Entity entity;

        public void apply(Graph graph, Element element, Entity rangeEntity, Func func) {
            entity.resolveAt(element, func);
            if (entity.uri == null) return;
            //todo: rangeEntity-property-entity is a triple!
        }
    }

    @XStreamAlias("internal_node")
    public static class InternalNode {
        public Entity entity;
        public Property property;

        public void apply(Graph graph, Element element, Domain domain, Property pathProperty, Func func) {
            entity.resolveAt(element, func);
            LOG.info("InternalNode: "+entity);
            if (!property.resolveAt(element)) return;
            // todo: domain-pathProperty-entity is a triple!
        }
    }

    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"xpath"})
    @XStreamAlias("exists")
    public static class Exists {
        @XStreamAsAttribute
        public String value;

        public String xpath;

        public boolean resolveAt(Element element) {
            // todo: evaluate the xpath
            // todo: compare if value != null
            return false;
        }
    }

    @XStreamAlias("uri_function")
    public static class URIFunction {
        @XStreamAsAttribute
        public String name;

        @XStreamImplicit
        public List<Arg> args;

        @XStreamOmitField
        public Map<String, String> argMap = new TreeMap<String, String>();

        public String executeAt(Element element, Func func) {
            if (args != null) {
                for (Arg arg : args) {
                    argMap.put(arg.name == null ? "" : arg.name, arg.resolveAt(element));
                }
            }
            return func.execute(name, argMap);
        }
    }

    @XStreamAlias("arg")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"content"})
    public static class Arg {
        @XStreamAsAttribute
        public String name;

        public String content;

        public String toString() {
            return content;
        }

        public String resolveAt(Element element) {
            return valueAt(element, content);
        }
    }

    public interface Func {
        String execute(String name, Map<String, String> argMap);
    }

    // =======================================================================
// Legacy work-around efforts to study:
//    public String apply(Node node, String className) {
//        try {
//            if ("Appellation".equals(name)) {
//                // appellationURI(String className, String subjUri, String appellation)
//                argList.add(className);
////                    argList.add(domainMapResult.uri);
//                fetchArgs(node);
//            }
//            else if ("createLiteral".equals(name)) {
//                // createLiteral(String className, String type, String note)
//                argList.add(UNUSED_CLASS_NAME);
//                argList.add(node.getNodeName());
//                fetchArgs(node);
//            }
//            else if ("dimensionURI".equals(name)) {
//                // dimensionURI(String className, String subjUri, String dimensions)
//                argList.add(UNUSED_CLASS_NAME);
////                    argList.add(domainMapResult.uri);
//                fetchArgs(node);
//            }
//            else if ("uriConceptual".equals(name)) {
//                // uriConceptual(String className, String thing)
//                argList.add(className);
//                fetchArgs(node);
//            }
//            else if ("uriEvents".equals(name)) {
//                // uriEvents(String className, String authority, String eventID, String subjUri)
//                argList.add(className);
////                    argList.add(domainMapResult.uri);
//                fetchArgs(node);
//            }
//            else if ("uriForActors".equals(name)) {
//                // uriForActors(String className, String authority, String id, String name, String birthDate)
//                argList.add(className);
//                fetchArgs(node);
//            }
//            else if ("PhysicalObject".equals(name)) {
//                // uriForPhysicalObjects(String className, String nameOfMuseum, String entry)
//                argList.add(UNUSED_CLASS_NAME);
//                fetchArgs(node);
//            }
//            else if ("Place".equals(name)) {
//                // uriForPlaces(String className, String placeName, String authority, String placeID,
//                //              Stribng coordinates, String spaces)
//                argList.add(UNUSED_CLASS_NAME);
//                fetchArg(node, 0);
//                fetchArg(node, 1);
//                fetchArg(node, 2);
//                fetchArg(node, 3); // coordinates never really used
//                argList.add(getPartOfPlaceHack(node));
//            }
//            else if ("PhysicalThing".equals(name)) {
//                // uriPhysThing(String className, String thing)
//                argList.add(className);
//                fetchArgs(node);
//            }
//            else if ("uriTimeSpan".equals(name)) {
//                // uriTimeSpan(String className, String timespan)
//                argList.add(UNUSED_CLASS_NAME);
//                fetchArgs(node);
//            }
//            else if ("Type".equals(name)) {
//                // uriType(String className, String type)
//                argList.add(className);
//                fetchArgs(node);
//            }
//            else {
//                throw new RuntimeException("Unknown function name: " + name);
//            }
//            Class<?>[] types = new Class<?>[argList.size()];
//            Arrays.fill(types, String.class);
//            try {
//                Method method = POLICIES.getClass().getMethod(name, types);
//                return (String) method.invoke(POLICIES, argList.toArray());
//            }
//            catch (NoSuchMethodException e) {
//                throw new RuntimeException(e);
//            }
//        }
//        catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private void fetchArgs(Node node) {
//        for (Arg a : args) {
//            argList.add(valueAt(node, a.content));
//        }
//    }
//
//    private void fetchArg(Node node, int index) {
//        argList.add(valueAt(node, args.get(index).content));
//    }
//
//    private String getPartOfPlaceHack(Node node) {
//        try { // iterate into partOfPlace fetching names and then join them with dash
//            List<String> places = new ArrayList<String>();
//            while (node != null) {
//                XPathExpression expr = path().compile("lido:namePlaceSet/lido:appellationValue/text()");
//                String placeName = (String) expr.evaluate(node, XPathConstants.STRING);
//                places.add(placeName);
//                expr = path().compile("lido:partOfPlace");
//                node = (Node) expr.evaluate(node, XPathConstants.NODE);
//            }
//            return StringUtils.join(places, '-');
//        }
//        catch (XPathExpressionException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
    private static String valueAt(Node context, String expressionString) {
        List<Node> nodes = nodeList(context, expressionString);
        if (nodes.isEmpty()) return "";
        String value = nodes.get(0).getNodeValue();
        if (value == null) return "";
        return value.trim();
    }

    private static List<Node> nodeList(Node context, String expressionString) {
        try {
            XPathExpression expression = path().compile(expressionString);
            NodeList nodeList = (NodeList) expression.evaluate(context, XPathConstants.NODESET);
            List<Node> list = new ArrayList<Node>(nodeList.getLength());
            for (int index = 0; index < nodeList.getLength(); index++) list.add(nodeList.item(index));
            return list;
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException("XPath Problem", e);
        }
    }

    private static class XPathContext implements NamespaceContext {
        private Map<String, String> prefixUri = new TreeMap<String, String>();
        private Map<String, String> uriPrefix = new TreeMap<String, String>();

        public XPathContext(String[][] prefixUriStrings) {
            for (String[] pair : prefixUriStrings) {
                prefixUri.put(pair[0], pair[1]);
                uriPrefix.put(pair[1], pair[0]);
            }
        }

        @Override
        public String getNamespaceURI(String prefix) {
            if (prefixUri.size() == 1) {
                return prefixUri.values().iterator().next();
            }
            return prefixUri.get(prefix);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return uriPrefix.get(namespaceURI);
        }

        @Override
        public Iterator getPrefixes(String namespaceURI) {
            String prefix = getPrefix(namespaceURI);
            if (prefix == null) return null;
            List<String> list = new ArrayList<String>();
            list.add(prefix);
            return list.iterator();
        }
    }
}
