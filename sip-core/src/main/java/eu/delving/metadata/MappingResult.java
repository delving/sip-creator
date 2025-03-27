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

import org.apache.jena.rdf.model.ModelFactory;

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

    public MappingResult(XmlSerializer serializer, String localId, Node root, RecDefTree recDefTree) {
        this.serializer = serializer;
        this.localId = localId;
        this.root = root;
        this.recDefTree = recDefTree;
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
        String error = MappingResult.hasRDFError(toRDF());
        if (error.length() > 0) {
            errors.add(error);
        }
        return errors;
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
        return toXml(Collections.emptyMap());
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
        rdf = rdf.replaceAll("<rdf:RDF ", "$0xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" ");
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
            Model mm = ModelFactory.createDefaultModel().read(in, null, "RDF/XML");
        } catch (Exception e) {
            return e.toString();
        }
        return "";
    }

}
