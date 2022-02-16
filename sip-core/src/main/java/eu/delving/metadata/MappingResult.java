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

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import eu.delving.groovy.XmlSerializer;
import org.apache.commons.codec.binary.Hex;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.ModelFactory;

import static org.checkerframework.checker.units.UnitsTools.s;

/**
 * The result of the mapping engine is wrapped in this class so that some post-processing and checking
 * can be done on the resulting Node tree.
 */
public class MappingResult {

    private final static byte[] SEPARATOR_PREFIX = "# !$".getBytes();
    private final static byte[] NEW_LINE = "\n".getBytes();

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

    private String sha1(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest mDigest = MessageDigest.getInstance("SHA1");
        byte[] result = mDigest.digest(input);
        return new String(Hex.encodeHex(result));
    }

    public byte[] toNQuad(Map<String, String> facts) {
        try {
            byte[] rdf = toRDF().getBytes(StandardCharsets.UTF_8);
            Model model = ModelFactory.createDefaultModel().read(new ByteArrayInputStream(rdf), null, "RDF/XML");

            ByteArrayOutputStream nquadBuffer = new ByteArrayOutputStream(rdf.length * 2);
            RDFDataMgr.write(nquadBuffer, model, RDFFormat.NQUADS);
            nquadBuffer.write(NEW_LINE);

            ByteArrayOutputStream quadMapBuffer = new ByteArrayOutputStream(1024 * 4);
            try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(quadMapBuffer))) {
                writer.beginObject();

                String orgID = facts.get("orgId");
                String spec = facts.get("spec");

                writer.name("hubID");
                writer.value(orgID + "_" + spec + "_" + localId);

                writer.name("orgID");
                writer.value(orgID);

                writer.name("localID");
                writer.value(localId);

                writer.name("graphURI");
                writer.value(facts.get("baseUrl"));

                writer.name("datasetID");
                writer.value(spec);

                writer.name("contentHash");
                writer.value(sha1(rdf));

                writer.endObject();
                writer.flush();
            }

            nquadBuffer.write(SEPARATOR_PREFIX);
            nquadBuffer.write(quadMapBuffer.toByteArray());
            nquadBuffer.write(NEW_LINE);
            return nquadBuffer.toByteArray();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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
            // TODO causes the largest spikes in memory usage by a large margin even after the set of URI checks was significantly reduced. See #38738404363a326970f52626ae6ac61deaebe2ec
            NodeList nodeList = (NodeList) entry.getValue().evaluate(root, XPathConstants.NODESET);
            for (int walk = 0; walk < nodeList.getLength(); walk++) {
                Node node = nodeList.item(walk);
                String content = node.getTextContent();
                if (!uriCheck(content)) {
                    errors.add(String.format(
                        "At %s: not a URI: [%s]",
                        entry.getKey(), content
                    ));
                }
            }
        }
        return errors;
    }

    public List<String> getRDFErrors() {
        List<String> errors = new ArrayList<String>();
//        String error = MappingResult.hasRDFError(toRDF());
//        if (error.length() > 0) {
//            errors.add(error);
//        }
        return errors;
    }

    public Node root() {
        return root;
    }

    public String toXml() {
        return serializer.toXml(root, recDefTree != null);
    }

    public String toRDF() {
        String rdf = toString();
        rdf = rdf.replaceAll("naa:RDF|edm:RDF|nant:RDF", "rdf:RDF");
        rdf = rdf.replaceAll(" xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"", "");
        rdf = rdf.replaceAll("<rdf:RDF ", "$0xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" ");
        return rdf;
    }

    public String toString() {
        return toXml();
    }
}
