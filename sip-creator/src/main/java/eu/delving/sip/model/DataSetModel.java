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

import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordDefinition;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.StorageException;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An observable hole to put the current data set
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DataSetModel implements MetadataModel {
    private DataSet dataSet;
    private DataSetState dataSetState = DataSetState.EMPTY;
    private List<FactDefinition> factDefinitions = new ArrayList<FactDefinition>();
    private Map<String, RecordDefinition> recordDefinitions = new TreeMap<String,RecordDefinition>();

    public DataSetModel() {
        new StateCheckTimer();
    }

    public boolean hasDataSet() {
        return dataSet != null;
    }

    public DataSet getDataSet() {
        if (dataSet == null) {
            throw new IllegalStateException("There is no data set in the model!");
        }
        return dataSet;
    }

    @Override
    public List<FactDefinition> getFactDefinitions() {
        return factDefinitions;
    }

    @Override
    public Set<String> getPrefixes() {
        return recordDefinitions.keySet();
    }

    @Override
    public RecordDefinition getRecordDefinition(String prefix) {
        RecordDefinition def = recordDefinitions.get(prefix);
        if (def == null) {
            throw new RuntimeException("Expected to have a record definition for prefix "+prefix);
        }
        return def;
    }

    public void setDataSet(final DataSet dataSet) throws StorageException {
        this.dataSet = dataSet;
        this.factDefinitions.clear();
        this.factDefinitions.addAll(dataSet.getFactDefinitions());
        this.recordDefinitions.clear();
        for (RecordDefinition recordDefinition : dataSet.getRecordDefinitions(factDefinitions)) {
            recordDefinitions.put(recordDefinition.prefix, recordDefinition);
        }
        this.dataSetState = dataSet.getState();
        if (SwingUtilities.isEventDispatchThread()) {
            for (Listener listener : listeners) {
                listener.dataSetChanged(dataSet);
            }
        }
        else {
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    for (Listener listener : listeners) {
                        listener.dataSetChanged(dataSet);
                    }
                }
            });
        }
    }

    private class StateCheckTimer implements Runnable, ActionListener {
        private Timer timer = new Timer(1000, this);

        private StateCheckTimer() {
            timer.setRepeats(true);
            timer.start();
        }

        @Override
        public void run() {
            if (hasDataSet()) {
                final DataSetState actualState = dataSet.getState();
                if (actualState != dataSetState) {
                    dataSetState = actualState;
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            for (Listener listener : listeners) {
                                listener.dataSetStateChanged(dataSet, actualState);
                            }
                        }
                    });
                }
            }
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(this);
        }
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {
        void dataSetChanged(DataSet dataSet);

        void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState);
    }
}
