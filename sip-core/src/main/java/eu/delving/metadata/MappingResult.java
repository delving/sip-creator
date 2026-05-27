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

package eu.delving.metadata;

import eu.delving.XMLToolFactory;
import eu.delving.groovy.Utils;
import eu.delving.groovy.XmlSerializer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.bind.DatatypeConverter;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The result of the mapping engine is wrapped in this class so that some
 * post-processing and checking
 * can be done on the resulting Node tree.
 *
 */
public class MappingResult {
    private XmlSerializer serializer;
    private Node root;
    private String localId;
    private RecDefTree recDefTree;
    private Map<String, String> facts;

    public MappingResult(XmlSerializer serializer, String localId, Node root, RecDefTree recDefTree) {
        this(serializer, localId, root, recDefTree, Collections.emptyMap());
    }

    public MappingResult(
            XmlSerializer serializer,
            String localId,
            Node root,
            RecDefTree recDefTree,
            Map<String, String> facts) {
        this.serializer = serializer;
        this.localId = localId;
        this.root = root;
        this.recDefTree = recDefTree;
        this.facts = facts == null ? Collections.emptyMap() : facts;
    }

    public String getLocalId() {
        return localId;
    }

    public RecDefTree getRecDefTree() {
        return recDefTree;
    }

    private boolean uriCheck(String maybeUri) {
        try {
            URI uri = new URI(maybeUri);
            return uri.isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public List<String> getUriErrors() throws XPathExpressionException {
        List<String> errors = new ArrayList<String>();
        for (Map.Entry<String, XPathExpression> entry : recDefTree.getUriCheckPaths().entrySet()) {
            // TODO causes the largest spikes in memory usage by a large margin even after
            // the set of URI checks was significantly. See
            // #38738404363a326970f52626ae6ac61deaebe2ec
            NodeList nodeList = (NodeList) entry.getValue().evaluate(root, XPathConstants.NODESET);
            for (int walk = 0; walk < nodeList.getLength(); walk++) {
                Node node = nodeList.item(walk);
                String content = node.getTextContent();
                if (!uriCheck(content)) {
                    errors.add(String.format(
                            "At %s: not a URI: [%s]",
                            entry.getKey(), content));
                }
            }
        }
        return errors;
    }

    public List<String> getRDFErrors() {
        List<String> errors = new ArrayList<String>();
        collectMissingTopLevelSubjectErrors(root, errors);
        collectRelativeUriErrors(root, errors);
        if (recDefTree != null) {
            String error = MappingResult.hasRDFError(toRDF());
            if (error.length() > 0) {
                errors.add(error);
            }
        }
        return errors;
    }

    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private void collectMissingTopLevelSubjectErrors(Node node, List<String> errors) {
        if (!isRdfRoot(node)) return;
        NodeList children = node.getChildNodes();
        int topLevelResourceCount = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            topLevelResourceCount++;
            Node about = child.getAttributes().getNamedItemNS(RDF_NS, "about");
            String value = about == null ? null : about.getNodeValue().trim();
            if (value != null && !value.isEmpty() && !value.startsWith("_:")) {
                return;
            }
        }
        if (topLevelResourceCount == 0) {
            errors.add("RDF output has no top-level resources; "
                + "map rdf:about on the record root using internalRecordURI()");
        }
        else {
            errors.add("RDF output has no top-level resource with a non-blank rdf:about; "
                + "map rdf:about on the record root using internalRecordURI()");
        }
    }

    private boolean isRdfRoot(Node node) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) return false;
        String localName = node.getLocalName();
        if (localName != null) return "RDF".equals(localName);
        return node.getNodeName() != null && node.getNodeName().endsWith(":RDF");
    }

    private void collectRelativeUriErrors(Node node, List<String> errors) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            checkRdfAttribute(node, "about", errors);
            checkRdfAttribute(node, "resource", errors);
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                collectRelativeUriErrors(children.item(i), errors);
            }
        }
    }

    private void checkRdfAttribute(Node node, String localName, List<String> errors) {
        Node attr = node.getAttributes().getNamedItemNS(RDF_NS, localName);
        if (attr == null) return;
        String value = attr.getNodeValue();
        if (value == null || value.isEmpty()) return;
        if (value.startsWith("_:")) return; // blank node
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute()) {
                errors.add(String.format(
                    "rdf:%s contains a relative URI [%s] on element <%s> — this will produce wrong triples downstream",
                    localName, value, node.getNodeName()));
            }
        } catch (URISyntaxException e) {
            errors.add(String.format(
                "rdf:%s contains an invalid URI [%s] on element <%s>: %s",
                localName, value, node.getNodeName(), e.getMessage()));
        }
    }

    public Node root() {
        return root;
    }

    /*
     * public String toXml() {
     * return serializer.toXml(root, recDefTree != null);
     * }
     */

    public String toXml() {
        return toXml(facts);
    }

    public String toXml(Map<String, String> facts) {
        try {
            return toByteArrayOutputStream(facts).toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ByteArrayOutputStream toByteArrayOutputStream(Map<String, String> facts) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(8192);
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA1"); // Changed to SHA1
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        DigestOutputStream digestStream = new DigestOutputStream(outputStream, digest);
        OutputStreamWriter writer = new OutputStreamWriter(digestStream, "UTF-8");
        serializer.writeXml(writer, root, recDefTree != null);

        // Use DatatypeConverter like the Scala version
        String hash = DatatypeConverter.printHexBinary(digestStream.getMessageDigest().digest()).toLowerCase();

        // XML comments may not contain double dashes (--) so if there are any then
        // divide them with a space (- -)
        String orgId = facts.getOrDefault("orgId", "unknown");
        String spec = facts.getOrDefault("spec", "unknown");
        String comment = String.format("<urn:%s_%s_%s/graph__%s>", orgId, spec, getLocalId(), hash);
        writer.write("<!--");
        writer.write(comment.replaceAll("\\-\\-", "- -"));
        writer.write("-->\n");
        writer.flush();

        return outputStream;
    }

    public static String toJenaCompliantRDF(String defaultPrefix, String rdf) {
        rdf = rdf.replaceAll(defaultPrefix + ":RDF", "rdf:RDF");
        rdf = rdf.replaceAll(" xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"", "");
        rdf = rdf.replaceFirst("<rdf:RDF(\\s|>)",
                "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"$1");
        return rdf;
    }

    public String toRDF() {
        return MappingResult.toJenaCompliantRDF(recDefTree.getRoot().getDefaultPrefix(), toString());
    }

    public String toString() {
        return Utils.stripNonPrinting(toXml());
    }

    public static String hasRDFError(String rdf) {
        try {
            InputStream in = new ByteArrayInputStream(rdf.getBytes("UTF-8"));
            // Use RDFDataMgr (RIOT) instead of Model.read() because the legacy
            // ARP parser logs E201 (multiple children of property element) but
            // does not throw, silently producing invalid RDF models.
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, in, Lang.RDFXML);
        } catch (Exception e) {
            return formatRDFError(e, rdf);
        }
        return "";
    }

    private static String formatRDFError(Exception e, String rdf) {
        String message = e.getMessage();
        int errorLine = extractLineNumber(message);
        if (errorLine < 0) {
            return e.toString();
        }
        String[] lines = rdf.split("\n");
        int contextRadius = 10;
        int start = Math.max(0, errorLine - 1 - contextRadius);
        int end = Math.min(lines.length, errorLine - 1 + contextRadius + 1);
        StringBuilder context = new StringBuilder();
        context.append("RDF parsing error at line ").append(errorLine).append(":\n");
        context.append(message).append("\n\n");
        context.append("Context:\n");
        for (int i = start; i < end; i++) {
            String marker = (i == errorLine - 1) ? ">>> " : "    ";
            context.append(String.format("%s%4d: %s\n", marker, i + 1, lines[i]));
        }
        return context.toString();
    }

    private static int extractLineNumber(String message) {
        if (message == null) return -1;
        // Matches patterns like "[line: 67, col: 26]" or "(line 67 column 26)"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("line[: ]+([0-9]+)")
                .matcher(message);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

}
