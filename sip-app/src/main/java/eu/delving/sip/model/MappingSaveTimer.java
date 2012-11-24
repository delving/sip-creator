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
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

/**
 * Save the mapping whenever things change.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingSaveTimer implements MappingModel.ChangeListener, MappingModel.SetListener, ActionListener, Work.DataSetPrefixWork {
    private long nextFreeze;
    private SipModel sipModel;
    private Timer triggerTimer = new Timer(200, this);
    private ListReceiver listReceiver;
    private boolean freezeMode;
    private boolean running = true;

    @Override
    public Job getJob() {
        return Job.SAVE_MAPPING;
    }

    @Override
    public String getPrefix() {
        if (!sipModel.getMappingModel().hasRecMapping()) return null;
        return sipModel.getMappingModel().getPrefix();
    }

    @Override
    public DataSet getDataSet() {
        if (sipModel.getDataSetModel().isEmpty()) return null;
        return sipModel.getDataSetModel().getDataSet();
    }

    public interface ListReceiver {
        void mappingFileList(List<File> mappingFiles);
    }

    public MappingSaveTimer(SipModel sipModel) {
        this.sipModel = sipModel;
        triggerTimer.setRepeats(false);
        Timer kickTimer = new Timer((int) (Storage.MAPPING_FREEZE_INTERVAL / 10), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                kick(true);
            }
        });
        kickTimer.start();
    }

    public void setListReceiver(ListReceiver listReceiver) {
        this.listReceiver = listReceiver;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (running) sipModel.exec(this);
    }

    @Override
    public void run() {
        if (!running) return;
        try {
            final RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                boolean freeze = false;
                if (System.currentTimeMillis() > nextFreeze) {
                    nextFreeze = System.currentTimeMillis() + Storage.MAPPING_FREEZE_INTERVAL;
                    freeze = true;
                }
                if (!freezeMode || freeze) {
                    sipModel.getDataSetModel().getDataSet().setRecMapping(recMapping, freeze);
                }
                if (freeze) {
                    if (listReceiver != null) sipModel.exec(new Swing() {
                        @Override
                        public void run() {
                            try {
                                List<File> recMappingFiles = sipModel.getDataSetModel().getDataSet().getRecMappingFiles(recMapping.getPrefix());
                                listReceiver.mappingFileList(recMappingFiles);
                            }
                            catch (StorageException e) {
                                sipModel.getFeedback().alert("Unable to fetch mapping files", e);
                            }
                        }
                    });
                }
            }
        }
        catch (StorageException e) {
            sipModel.getFeedback().alert("Unable to save mapping", e);
        }
    }

    @Override
    public void recMappingSet(MappingModel mappingModel) {
        kick(false);
    }

    @Override
    public void lockChanged(MappingModel mappingModel, boolean locked) {
        kick(false);
    }

    @Override
    public void functionChanged(MappingModel mappingModel, MappingFunction function) {
        kick(false);
    }

    @Override
    public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
        kick(false);
    }

    @Override
    public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        kick(false);
    }

    @Override
    public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        kick(false);
    }

    @Override
    public void populationChanged(MappingModel mappingModel, RecDefNode node) {
    }

    public void shutdown() {
        running = false;
        triggerTimer.stop();
    }

    private void kick(boolean freeze) {
        this.freezeMode = freeze;
        triggerTimer.restart();
    }
}

