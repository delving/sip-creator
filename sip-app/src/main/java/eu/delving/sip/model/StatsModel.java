/*
 * Copyright 2011 DELVING BV
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
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.StatsTree;
import eu.delving.sip.base.StatsTreeNode;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;

import javax.swing.Timer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An observable hole to put the things related to analysis: statistics, analysis tree, some list models
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StatsModel {
    private SipModel sipModel;
    private FactModel hintsModel = new FactModel();
    private StatsTree statsTree = StatsTree.create("Select a data set from the File menu, or download one");
    private DefaultTreeModel statsTreeModel = new DefaultTreeModel(statsTree.getRoot());

    public StatsModel(SipModel sipModel) {
        this.sipModel = sipModel;
        hintsModel.addListener(new HintSaveTimer());
    }

    public void setStatistics(Statistics statistics) {
        Path recordRoot = null;
        Path uniqueElement = null;
        if (statistics != null) {
            statsTree = statistics.createAnalysisTree(sipModel.getDataSetFacts().getFacts());
            if (statistics.isSourceFormat()) {
                recordRoot = Storage.RECORD_ROOT;
                uniqueElement = Storage.UNIQUE_ELEMENT;
            }
            else {
                recordRoot = getRecordRoot();
                uniqueElement = getUniqueElement();
            }
        }
        else {
            statsTree = StatsTree.create("Analysis not yet performed");
        }
        statsTreeModel.setRoot(statsTree.getRoot());
        setDelimiters(recordRoot, uniqueElement);
    }

    private void setDelimiters(Path recordRoot, Path uniqueElement) {
        if (recordRoot != null) {
            int recordCount = StatsTree.setRecordRoot(statsTreeModel, recordRoot);
            hintsModel.set(Storage.RECORD_COUNT, String.valueOf(recordCount));
            List<StatsTreeNode> variables = new ArrayList<StatsTreeNode>();
            statsTree.getVariables(variables);
        }
        if (uniqueElement != null) {
            StatsTree.setUniqueElement(statsTreeModel, uniqueElement);
        }
    }

    public void set(Map<String, String> hints) {
        hintsModel.set(hints);
    }

    public boolean hasRecordRoot() {
        return hintsModel.get(Storage.RECORD_ROOT_PATH) != null && !hintsModel.get(Storage.RECORD_ROOT_PATH).isEmpty();
    }

    public void setRecordRoot(Path recordRoot) {
        int recordCount = StatsTree.setRecordRoot(statsTreeModel, recordRoot);
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
        StatsTree.setUniqueElement(statsTreeModel, uniqueElement);
        hintsModel.set(Storage.UNIQUE_ELEMENT_PATH, uniqueElement.toString());
        fireUniqueElementSet();
    }

    public Path getUniqueElement() {
        return Path.create(hintsModel.get(Storage.UNIQUE_ELEMENT_PATH));
    }

    public TreeModel getStatsTreeModel() {
        return statsTreeModel;
    }

    public SortedSet<StatsTreeNode> findNodesForInputPaths(NodeMapping nodeMapping) {
        SortedSet<StatsTreeNode> nodes = new TreeSet<StatsTreeNode>();
        if (!(statsTreeModel.getRoot() instanceof StatsTreeNode)) {
            nodeMapping.clearStatsTreeNodes();
        }
        else if (!nodeMapping.hasStatsTreeNodes()) {
            for (Path path : nodeMapping.getInputPaths()) {
                TreePath treePath = findNodeForInputPath(path, (StatsTreeNode) statsTreeModel.getRoot());
                if (treePath != null) nodes.add((StatsTreeNode)treePath.getLastPathComponent());
            }
            if (nodes.isEmpty()) {
                nodeMapping.clearStatsTreeNodes();
            }
            else {
                StatsTreeNode.setStatsTreeNodes(nodes, nodeMapping);
            }
        }
        else {
            for (Object node : nodeMapping.getStatsTreeNodes()) nodes.add((StatsTreeNode) node);
        }
        return nodes.isEmpty() ? null : nodes;
    }

    private TreePath findNodeForInputPath(Path path, StatsTreeNode node) {
        Path nodePath = node.getPath(false);
        if (nodePath.equals(path)) return node.getTreePath();
        for (StatsTreeNode sub : node.getChildren()) {
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
        void recordRootSet(Path recordRootPath);

        void uniqueElementSet(Path uniqueElementPath);
    }

    private class HintSaveTimer implements FactModel.Listener, ActionListener, Runnable {
        private Timer timer = new Timer(200, this);

        private HintSaveTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Exec.work(this);
        }

        @Override
        public void run() {
            try {
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
    }


}
