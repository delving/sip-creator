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

import eu.delving.groovy.Utils;
import eu.delving.groovy.XmlSerializer;
import org.apache.jena.rdf.model.Model;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.ModelFactory;


/**
 * The result of the mapping engine is wrapped in this class so that some post-processing and checking
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

    public String sha256() {
        return Utils.sha256(root);
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
        }
        catch (URISyntaxException e) {
            return false;
        }
    }

    public List<String> getUriErrors() throws XPathExpressionException {
        List<String> errors = new ArrayList<String>();
        for (Map.Entry<String, XPathExpression> entry : recDefTree.getUriCheckPaths().entrySet()) {
            // TODO causes the largest spikes in memory usage by a large margin even after the set of URI checks was significantly. See #38738404363a326970f52626ae6ac61deaebe2ec
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
        String error = MappingResult.hasRDFError(toRDF());
        if (error.length() > 0) {
            errors.add(error);
        }
        return errors;
    }

    public Node root() {
        return root;
    }

    public String toXml() {
        return serializer.toXml(root, recDefTree != null);
    }

    public static String toJenaCompliantRDF(String rdf) {
        rdf = rdf.replaceAll("naa:RDF|edm:RDF|nant:RDF", "rdf:RDF");
        rdf = rdf.replaceAll(" xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"", "");
        rdf = rdf.replaceAll("<rdf:RDF ", "$0xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" ");
        return rdf;
    }

    public String toRDF() {
        return MappingResult.toJenaCompliantRDF(toString());
    }

    public String toString() {
        return toXml();
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
