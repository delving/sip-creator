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

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.metadata.SourceVariable;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An observable hole to put the things related to analysis: statistics, analysis tree, some list models
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class AnalysisModel {
    private SipModel sipModel;
    private FactModel hintsModel = new FactModel();
    private Statistics statistics;
    private AnalysisTree analysisTree = AnalysisTree.create("Select a data set from the File menu, or download one");
    private DefaultTreeModel analysisTreeModel = new DefaultTreeModel(analysisTree.getRoot());
    private VariableListModel variableListModel = new VariableListModel();

    public AnalysisModel(SipModel sipModel) {
        this.sipModel = sipModel;
        hintsModel.addListener(new HintSaveTimer());
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
        Path recordRoot = null;
        Path uniqueElement = null;
        if (statistics != null) {
            analysisTree = statistics.createAnalysisTree();
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
            analysisTree = AnalysisTree.create("Analysis not yet performed");
        }
        analysisTreeModel.setRoot(analysisTree.getRoot());
        setDelimiters(recordRoot, uniqueElement);
        selectStatistics(null);
    }

    private void setDelimiters(Path recordRoot, Path uniqueElement) {
        if (recordRoot != null) {
            int recordCount = AnalysisTree.setRecordRoot(analysisTreeModel, recordRoot);
            hintsModel.set(Storage.RECORD_COUNT, String.valueOf(recordCount));
            List<AnalysisTree.Node> variables = new ArrayList<AnalysisTree.Node>();
            analysisTree.getVariables(variables);
            variableListModel.setVariableList(variables);
        }
        else {
            variableListModel.clear();
        }
        if (uniqueElement != null) {
            AnalysisTree.setUniqueElement(analysisTreeModel, uniqueElement);
        }
    }

    public void set(Map<String, String> hints) {
        hintsModel.set(hints);
    }

    public boolean hasRecordRoot() {
        return hintsModel.get(Storage.RECORD_ROOT_PATH) != null && !hintsModel.get(Storage.RECORD_ROOT_PATH).isEmpty();
    }

    public void setRecordRoot(Path recordRoot) {
        int recordCount = AnalysisTree.setRecordRoot(analysisTreeModel, recordRoot);
        hintsModel.set(Storage.RECORD_ROOT_PATH, recordRoot.toString());
        hintsModel.set(Storage.RECORD_COUNT, String.valueOf(recordCount));
        fireRecordRootSet();
    }

    public Path getRecordRoot() {
        return new Path(hintsModel.get(Storage.RECORD_ROOT_PATH));
    }

    public int getRecordCount() {
        String recordCount = hintsModel.get(Storage.RECORD_COUNT);
        return recordCount == null ? 0 : Integer.parseInt(recordCount);
    }

    public long getElementCount() {
        return statistics.getElementCount();
    }

    public void setUniqueElement(Path uniqueElement) {
        AnalysisTree.setUniqueElement(analysisTreeModel, uniqueElement);
        hintsModel.set(Storage.UNIQUE_ELEMENT_PATH, uniqueElement.toString());
        fireUniqueElementSet();
    }

    public Path getUniqueElement() {
        return new Path(hintsModel.get(Storage.UNIQUE_ELEMENT_PATH));
    }

    public void selectStatistics(FieldStatistics fieldStatistics) {
        for (Listener listener : listeners) {
            listener.statisticsSelected(fieldStatistics);
        }
    }

    public TreeModel getAnalysisTreeModel() {
        return analysisTreeModel;
    }

    public ListModel getVariablesListWithCountsModel() {
        return variableListModel.getWithCounts(sipModel.getMappingModel());
    }

    public List<SourceVariable> getVariables() {
        List<AnalysisTree.Node> nodes = new ArrayList<AnalysisTree.Node>();
        analysisTree.getVariables(nodes);
        List<SourceVariable> variables = new ArrayList<SourceVariable>(nodes.size());
        for (AnalysisTree.Node node : nodes) {
            variables.add(new SourceVariable(node));
        }
        return variables;
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
        void statisticsSelected(FieldStatistics fieldStatistics);

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
