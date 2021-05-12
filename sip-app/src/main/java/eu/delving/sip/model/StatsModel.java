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
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static eu.delving.sip.files.Storage.MAX_UNIQUE_VALUE_LENGTH;

/**
 * An observable hole to put the things related to analysis: statistics, analysis tree, some list models
 *
 *
 */

public class StatsModel {
    private SipModel sipModel;
    private FactModel hintsModel = new FactModel();
    private SourceTreeNode sourceTree = SourceTreeNode.create("Select a data set from the File menu, or download one");
    private FilterTreeModel sourceTreeModel = new FilterTreeModel(sourceTree);
    // TODO record count is never used
    private int recordCount;

    public StatsModel(SipModel sipModel) {
        this.sipModel = sipModel;
        hintsModel.addListener(new HintSaveTimer());
    }

    private TreePath findNodeForInputPath(Path path, SourceTreeNode node) {
        Path nodePath = node.getUnwrappedPath();
//        System.out.println(node.getPath() + ": " + nodePath + " =?= " + path);
        if (nodePath.equals(path)) return node.getTreePath();
        for (SourceTreeNode sub : node.getChildren()) {
            TreePath subPath = findNodeForInputPath(path, sub);
            if (subPath != null) return subPath;
        }
        return null;
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

    public void setStatistics(Stats stats) {
        if (stats != null) {
            sourceTree = SourceTreeNode.create(stats.fieldValueMap, sipModel.getDataSetFacts().getFacts());
            setSourceTree(sourceTree);
            if (sipModel.getMappingModel().hasRecMapping()) {
                // merge the mapping into the source tree
                for (NodeMapping nodeMapping : sipModel.getMappingModel().getRecMapping().getNodeMappings()) {
                    findNodesForInputPaths(nodeMapping);
                }
            }
        }
        else {
            setSourceTree(SourceTreeNode.create("Analysis not yet performed"));
        }
    }

    private void setSourceTree(SourceTreeNode sourceTreeRoot) {
        this.sourceTree = sourceTreeRoot;
        sourceTreeModel.setRoot(sourceTreeRoot);
        setDelimiters();
    }

    public FactModel getHintsModel() {
        return hintsModel;
    }

    // TODO record count is never used
    public int getRecordCount() {
        return recordCount;
    }

    public void setMaxUniqueValueLength(int max) {
        hintsModel.set(Storage.MAX_UNIQUE_VALUE_LENGTH, String.valueOf(max));
    }

    public int getMaxUniqueValueLength() {
        String max = hintsModel.get(MAX_UNIQUE_VALUE_LENGTH);
        return max == null ? Stats.DEFAULT_MAX_UNIQUE_VALUE_LENGTH : Integer.parseInt(max);
    }

    public SourceTreeNode getSourceTree() {
        return sourceTree;
    }

    public TreeModel getSourceTreeModel() {
        return sourceTreeModel;
    }

    private void setDelimiters() {
        sourceTree.setRecordContainer(Storage.RECORD_CONTAINER);
        sourceTree.setUniqueElement(Storage.UNIQUE_ELEMENT);
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
