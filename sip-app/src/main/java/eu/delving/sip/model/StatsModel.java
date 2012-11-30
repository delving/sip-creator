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

package eu.delving.sip.model;

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.stats.Stats;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import static eu.delving.sip.files.Storage.MAX_UNIQUE_VALUE_LENGTH;
import static eu.delving.sip.files.Storage.UNIQUE_VALUE_CONVERTER;

/**
 * An observable hole to put the things related to analysis: statistics, analysis tree, some list models
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StatsModel {
    private SipModel sipModel;
    private FactModel hintsModel = new FactModel();
    private SourceTreeNode sourceTree = SourceTreeNode.create("Select a data set from the File menu, or download one");
    private FilterTreeModel sourceTreeModel = new FilterTreeModel(sourceTree);

    public StatsModel(SipModel sipModel) {
        this.sipModel = sipModel;
        hintsModel.addListener(new HintSaveTimer());
    }

    public void setStatistics(Stats stats) {
        if (stats != null) {
            sourceTree = SourceTreeNode.create(stats.fieldValueMap, sipModel.getDataSetFacts().getFacts());
            if (stats.sourceFormat) {
                setSourceTree(sourceTree, Storage.RECORD_ROOT, Storage.UNIQUE_ELEMENT);
                if (sipModel.getMappingModel().hasRecMapping()) {
                    for (NodeMapping nodeMapping : sipModel.getMappingModel().getRecMapping().getNodeMappings()) {
                        findNodesForInputPaths(nodeMapping);
                    }
                }
            }
            else {
                setSourceTree(sourceTree, getRecordRoot(), getUniqueElement());
            }
        }
        else {
            setSourceTree(SourceTreeNode.create("Analysis not yet performed"), null, null);
        }
    }

    private void setSourceTree(SourceTreeNode sourceTreeRoot, Path recordRoot, Path uniqueElement) {
        this.sourceTree = sourceTreeRoot;
        sourceTreeModel.setRoot(sourceTreeRoot);
        setDelimiters(recordRoot, uniqueElement);
    }

    public FactModel getHintsModel() {
        return hintsModel;
    }

    public boolean hasRecordRoot() {
        return hintsModel.get(Storage.RECORD_ROOT_PATH) != null && !hintsModel.get(Storage.RECORD_ROOT_PATH).isEmpty();
    }

    public void setRecordRoot(Path recordRoot) {
        int recordCount = sourceTree.setRecordRoot(recordRoot);
        hintsModel.set(Storage.RECORD_ROOT_PATH, recordRoot.toString());
        hintsModel.set(Storage.RECORD_COUNT, String.valueOf(recordCount));
        fireRecordRootSet();
    }

    public Path getRecordRoot() {
        return Path.create(hintsModel.get(Storage.RECORD_ROOT_PATH));
    }

    public int getRecordCount() {
        String recordCount = hintsModel.get(Storage.RECORD_COUNT);
        return recordCount == null ? 0 : Integer.parseInt(recordCount);
    }

    public void setUniqueElement(Path uniqueElement) {
        sourceTree.setUniqueElement(uniqueElement);
        hintsModel.set(Storage.UNIQUE_ELEMENT_PATH, uniqueElement.toString());
        fireUniqueElementSet();
    }

    public Path getUniqueElement() {
        return Path.create(hintsModel.get(Storage.UNIQUE_ELEMENT_PATH));
    }

    public void setMaxUniqueValueLength(int max) {
        hintsModel.set(Storage.MAX_UNIQUE_VALUE_LENGTH, String.valueOf(max));
    }

    public int getMaxUniqueValueLength() {
        String max = hintsModel.get(MAX_UNIQUE_VALUE_LENGTH);
        return max == null ? Stats.DEFAULT_MAX_UNIQUE_VALUE_LENGTH : Integer.parseInt(max);
    }

    public void setUniqueValueConverter(String converter) {
        hintsModel.set(Storage.UNIQUE_VALUE_CONVERTER, converter);
    }

    public String getUniqueValueConverter() {
        return hintsModel.get(UNIQUE_VALUE_CONVERTER);
    }

    public SourceTreeNode getSourceTree() {
        return sourceTree;
    }

    public TreeModel getSourceTreeModel() {
        return sourceTreeModel;
    }

    public SortedSet<SourceTreeNode> findNodesForInputPaths(NodeMapping nodeMapping) {
        SortedSet<SourceTreeNode> nodes = new TreeSet<SourceTreeNode>();
        nodeMapping.clearSourceTreeNodes();
        if (sourceTreeModel.getRoot() instanceof SourceTreeNode) {
            for (Path path : nodeMapping.getInputPaths()) {
                TreePath treePath = findNodeForInputPath(path, (SourceTreeNode) sourceTreeModel.getRoot());
                if (treePath != null) nodes.add((SourceTreeNode) treePath.getLastPathComponent());
            }
            if (nodes.isEmpty()) {
                nodeMapping.clearSourceTreeNodes();
            }
            else {
                SourceTreeNode.setStatsTreeNodes(nodes, nodeMapping);
            }
        }
        return nodes.isEmpty() ? null : nodes;
    }

    private void setDelimiters(Path recordRoot, Path uniqueElement) {
        if (recordRoot != null) {
            int recordCount = sourceTree.setRecordRoot(recordRoot);
            hintsModel.set(Storage.RECORD_COUNT, String.valueOf(recordCount));
        }
        if (uniqueElement != null) sourceTree.setUniqueElement(uniqueElement);
    }

    private TreePath findNodeForInputPath(Path path, SourceTreeNode node) {
        Path nodePath = node.getPath(false);
        if (nodePath.equals(path)) return node.getTreePath();
        for (SourceTreeNode sub : node.getChildren()) {
            TreePath subPath = findNodeForInputPath(path, sub);
            if (subPath != null) return subPath;
        }
        return null;
    }

    private void fireRecordRootSet() {
        Path recordRoot = getRecordRoot();
        for (Listener listener : listeners) {
            listener.recordRootSet(recordRoot);
        }
    }

    private void fireUniqueElementSet() {
        Path uniqueElement = getUniqueElement();
        for (Listener listener : listeners) {
            listener.uniqueElementSet(uniqueElement);
        }
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {
        void mappingHints(List<NodeMapping> mappings);

        void recordRootSet(Path recordRootPath);

        void uniqueElementSet(Path uniqueElementPath);
    }

    private class HintSaveTimer implements FactModel.Listener, ActionListener, Work.DataSetWork {
        private Timer timer = new Timer(200, this);

        private HintSaveTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            sipModel.exec(this);
        }

        @Override
        public void run() {
            try {
                if (sipModel.getDataSetModel().isEmpty()) return;
                sipModel.getDataSetModel().getDataSet().setHints(hintsModel.getFacts());
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to save analysis hints", e);
            }
        }

        @Override
        public void factUpdated(String name, String value) {
            timer.restart();
        }

        @Override
        public void allFactsUpdated(Map<String, String> map) {
            timer.restart();
        }

        @Override
        public Job getJob() {
            return Job.SAVE_HINTS;
        }

        @Override
        public DataSet getDataSet() {
            if (sipModel.getDataSetModel().isEmpty()) return null;
            return sipModel.getDataSetModel().getDataSet();
        }
    }

}
