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

import eu.delving.metadata.*;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.StorageException;

import javax.swing.Timer;
import javax.xml.validation.Validator;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static eu.delving.sip.files.DataSetState.ABSENT;

/**
 * An observable hole to put the current dataset, where the contents of the dataset directory is checked frequently
 * to notice independently when the state has changed through the disappearance or arrival of files with particular
 * name patterns.  Many classes throughout the SIP-Creator observe this model and the state changes are broadcast
 * in the Swing thread so that they can directly influence the GUI.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DataSetModel implements RecDefModel {
    private DataSet dataSet;
    private DataSetState currentState = ABSENT;
    private MappingModel mappingModel;
    private StateCheckTimer stateCheckTimer;

    public DataSetModel() { // test only
        this.mappingModel = new MappingModel();
    }

    public DataSetModel(SipModel sipModel) {
        this.mappingModel = new MappingModel(sipModel);
        stateCheckTimer = new StateCheckTimer(sipModel);
    }

    public void shutdown() {
        stateCheckTimer.shutdown();
    }

    public MappingModel getMappingModel() {
        return mappingModel;
    }

    public boolean isEmpty() {
        return dataSet == null;
    }

    public DataSetState getDataSetState() {
        return isEmpty() ? ABSENT : dataSet.getState(getPrefix());
    }

    public List<String> getInvalidPrefixes() throws StorageException {
        List<String> invalid = new ArrayList<String>();
        if (!isEmpty()) {
            for (SchemaVersion schemaVersion : dataSet.getSchemaVersions()) {
                String prefix = schemaVersion.getPrefix();
                if (!dataSet.isValidated(prefix)) invalid.add(prefix);
            }
        }
        return invalid;
    }

    public RecMapping getRecMapping() throws StorageException {
        return getDataSet().getRecMapping(getPrefix(), this);
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public String getPrefix() {
        return mappingModel.getPrefix();
    }

    public Validator newValidator() throws MetadataException {
        try {
            return dataSet.newValidator(getPrefix());
        }
        catch (StorageException e) {
            throw new MetadataException("Unable to get validator", e);
        }
    }

    @Override
    public RecDefTree createRecDefTree(SchemaVersion schemaVersion) throws MetadataException {
        try {
            RecDef recDef = dataSet.getRecDef(schemaVersion.getPrefix());
            return RecDefTree.create(recDef);
        }
        catch (StorageException e) {
            throw new MetadataException(e);
        }
    }

    public void setDataSet(final DataSet dataSet, String prefix) throws StorageException {
        this.dataSet = dataSet;
        this.currentState = null;
        if (dataSet != null) {
            mappingModel.setRecMapping(dataSet.getRecMapping(prefix, this));
        }
        else {
            mappingModel.setRecMapping(null);
        }
    }

    public void clearDataSet() {
        dataSet = null;
        mappingModel.setRecMapping(null);
    }

    public boolean deleteValidation() throws StorageException {
        return !isEmpty() && dataSet.deleteValidation(getPrefix());
    }

    private class StateCheckTimer implements Work, ActionListener, Work.DataSetWork {
        private SipModel sipModel;
        private Timer timer = new Timer(1000, this);
        private boolean running = true;

        private StateCheckTimer(SipModel sipModel) {
            this.sipModel = sipModel;
            timer.setRepeats(false);
            timer.start();
        }

        @Override
        public void run() {
            if (!running) return;
            final DataSetState freshState = getDataSetState();
            if (freshState != currentState) {
                currentState = freshState;
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        for (SwingListener listener : listeners) listener.stateChanged(DataSetModel.this, freshState);
                    }
                });
            }
            timer.restart();
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.exec(this);
        }

        @Override
        public Job getJob() {
            return Job.CHECK_STATE;
        }

        @Override
        public DataSet getDataSet() {
            if (sipModel.getDataSetModel().isEmpty()) return null;
            return sipModel.getDataSetModel().getDataSet();
        }

        public void shutdown() {
            running = false;
        }
    }

    private List<SwingListener> listeners = new CopyOnWriteArrayList<SwingListener>();

    public void addListener(SwingListener listener) {
        listeners.add(listener);
    }

    public interface SwingListener {

        void stateChanged(DataSetModel model, DataSetState state);

    }
}
