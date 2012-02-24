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

import eu.delving.metadata.*;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.StorageException;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.xml.validation.Validator;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An observable hole to put the current data set
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DataSetModel implements RecDefModel {
    private DataSet dataSet;
    private Map<String, Validator> validatorMap = new TreeMap<String, Validator>();
    private DataSetState dataSetState = DataSetState.EMPTY;
    private List<FactDefinition> factDefinitions = new ArrayList<FactDefinition>();

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

    public Validator getValidator(String metadataPrefix) throws MetadataException {
        try {
            Validator found = validatorMap.get(metadataPrefix);
            if (found == null) {
                validatorMap.put(metadataPrefix, found = getDataSet().getValidator(metadataPrefix));
            }
            return found;
        }
        catch (StorageException e) {
            throw new MetadataException("Unable to get validator", e);
        }
    }

    @Override
    public List<FactDefinition> getFactDefinitions() {
        return factDefinitions;
    }

    @Override
    public Set<String> getPrefixes() throws MetadataException {
        try {
            return new TreeSet<String>(dataSet.getRecDefPrefixes());
        }
        catch (StorageException e) {
            throw new MetadataException(e);
        }
    }

    @Override
    public RecDefTree createRecDef(String prefix) throws MetadataException {
        try {
            RecDef recDef = dataSet.getRecDef(prefix);
            return RecDefTree.create(recDef);
        }
        catch (StorageException e) {
            throw new MetadataException(e);
        }
    }

    public void clearDataSet() {
        if (SwingUtilities.isEventDispatchThread()) {
            for (Listener listener : listeners) listener.dataSetRemoved();
        }
        else {
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    for (Listener listener : listeners) listener.dataSetRemoved();
                }
            });
        }
    }

    public void setDataSet(final DataSet dataSet) throws StorageException {
        this.dataSet = dataSet;
        this.validatorMap.clear();
        this.factDefinitions.clear();
        if (dataSet != null) {
            this.factDefinitions.addAll(dataSet.getFactDefinitions());
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
        else {
            clearDataSet();
        }
    }

    public RecMapping getRecMapping(String prefix) throws StorageException {
        return getDataSet().getRecMapping(prefix, this);
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

        void dataSetRemoved();

        void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState);
    }
}
