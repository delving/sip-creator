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

import eu.delving.groovy.XmlSerializer;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public Node root() {
        return root;
    }

    public String toXml() {
        return serializer.toXml(root, recDefTree != null);
    }

    public String toString() {
        return toXml();
    }
}
