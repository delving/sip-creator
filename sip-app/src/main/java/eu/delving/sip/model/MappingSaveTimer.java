/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.model;

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

/**
 * Save the mapping whenever things change.
 *
 *
 */

public class MappingSaveTimer implements MappingModel.ChangeListener, MappingModel.SetListener, ActionListener, Work.DataSetPrefixWork {
    private SipModel sipModel;
    private final Timer triggerTimer;
    private final Timer freezeTimer;
    private ListReceiver listReceiver;
    private boolean freezeMode = false;
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
        triggerTimer = new Timer(Storage.MAPPING_SAVE_DELAY, this);
        triggerTimer.setRepeats(false);
        freezeTimer = new Timer(Storage.MAPPING_FREEZE_INTERVAL, e -> {
            freezeMode = true;
            if (!triggerTimer.isRunning()) {
                triggerTimer.restart();
            }
        });
        freezeTimer.setRepeats(false);
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
                sipModel.getDataSetModel().getDataSet().setRecMapping(recMapping, freezeMode);
                freezeMode = false;
                if (listReceiver != null) {
                    sipModel.exec(() -> {
                        try {
                            List<File> recMappingFiles = sipModel.getDataSetModel().getDataSet().getRecMappingFiles();
                            listReceiver.mappingFileList(recMappingFiles);
                        } catch (StorageException e) {
                            sipModel.getFeedback().alert("Unable to fetch mapping files", e);
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
        kick();
    }

    @Override
    public void lockChanged(MappingModel mappingModel, boolean locked) {
        kick();
    }

    @Override
    public void functionChanged(MappingModel mappingModel, MappingFunction function) {
        kick();
    }

    @Override
    public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
        kick();
    }

    @Override
    public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        kick();
    }

    @Override
    public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        kick();
    }

    @Override
    public void populationChanged(MappingModel mappingModel, RecDefNode node) {
        kick();
    }

    public void shutdown() {
        running = false;
        triggerTimer.stop();
        freezeTimer.stop();
    }

    private void kick() {
        if (!triggerTimer.isRunning()) {
            triggerTimer.restart();
        }
        if (!freezeTimer.isRunning()) {
            freezeTimer.restart();
        }
    }
}
