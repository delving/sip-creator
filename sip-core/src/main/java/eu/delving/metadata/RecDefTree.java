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

import eu.delving.XMLToolFactory;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class takes a RecDef instance and wraps itself around, ensuring
 * event propagation by brokering the Listener that is passed to every
 * contained RecDefNode.
 * <p/>
 * The RecDefNode composite elements of the tree are held at "root" and
 * they contain NodeMappings from the RecMapping, so this plust the fact
 * that the RecDef's data is always available makes this the class
 * responsible for writing the code of the Groovy builder which will
 * ultimately do the transformation.
 * <p/>
 * Code generation is done in two ways, with or without a particular
 * path selected, so that the user interface can give people only a tiny
 * part of the whole mapping to deal with at one time.
 *
 *
 */

public class RecDefTree implements RecDefNodeListener {
    private RecDefNamespaceContext pathContext;
    private RecDef recDef;
    private RecDefNode root;
    private RecDefNodeListener listener;
    private Map<String, XPathExpression> uriCheckPaths = new TreeMap<String, XPathExpression>();

    public static RecDefTree create(RecDef recDef) {
        RecDefTree tree = new RecDefTree(recDef);
        try {
            tree.resolve();
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException("XPath problem");
        }
        return tree;
    }

    private RecDefTree(RecDef recDef) {
        this.recDef = recDef;
        this.root = RecDefNode.create(this, recDef);
        this.pathContext = new RecDefNamespaceContext(recDef.namespaces);
    }

    public void setListener(RecDefNodeListener listener) {
        this.listener = listener;
    }

    public RecDef getRecDef() {
        return recDef;
    }

    public RecDefNode getRoot() {
        return root;
    }

    public Map<String, XPathExpression> getUriCheckPaths() {
        return uriCheckPaths;
    }

    public RecDefNode getRecDefNode(Path path) {
        return root.getNode(path);
    }

    public List<Path> collectUriCheckPaths() {
        List<Path> paths = new ArrayList<Path>();
        collectUriCheckPaths(root, Path.create(), paths);
        return paths;
    }

    public RecDefNode getFirstRecDefNode(Tag tag) {
        return root.getFirstNode(tag);
    }

    public List<NodeMapping> getNodeMappings() {
        List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();
        root.collectNodeMappings(nodeMappings);
        return nodeMappings;
    }

    public List<DynOpt> getDynOpts() {
        List<DynOpt> dynOpts = new ArrayList<DynOpt>();
        root.collectDynOpts(dynOpts);
        return dynOpts;
    }

    @Override
    public void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping, NodeMappingChange change) {
        if (listener != null) listener.nodeMappingChanged(recDefNode, nodeMapping, change);
    }

    @Override
    public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingAdded(recDefNode, nodeMapping);
        root.checkPopulated();
    }

    @Override
    public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingRemoved(recDefNode, nodeMapping);
        root.checkPopulated();
    }

    @Override
    public void populationChanged(RecDefNode recDefNode) {
        if (listener != null) listener.populationChanged(recDefNode);
    }

    private void collectUriCheckPaths(RecDefNode node, Path path, List<Path> paths) {
        path = path.child(node.getTag());
        if (node.hasUriCheck()) paths.add(path);
        for (RecDefNode child : node.getChildren()) collectUriCheckPaths(child, path, paths);
    }

    private void resolve() throws XPathExpressionException {
        for (Path uriCheckPath :collectUriCheckPaths()) {
            String path = uriCheckPath.toString();
            uriCheckPaths.put(path, createPath().compile(path));
        }
        root.checkPopulated();
    }

    private XPath createPath() {
        return XMLToolFactory.xpath(pathContext);
    }
}
