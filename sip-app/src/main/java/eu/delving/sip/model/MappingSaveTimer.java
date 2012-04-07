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

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

/**
 * Save the mapping whenever things change
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingSaveTimer implements MappingModel.ChangeListener, MappingModel.SetListener, ActionListener, Runnable {
    private long nextFreeze;
    private SipModel sipModel;
    private Timer triggerTimer = new Timer(200, this);
    private ListReceiver listReceiver;

    public interface ListReceiver {
        void mappingFileList(List<File> mappingFiles);
    }

    public MappingSaveTimer(SipModel sipModel) {
        this.sipModel = sipModel;
        triggerTimer.setRepeats(false);
        Timer kickTimer = new Timer((int) (Storage.MAPPING_FREEZE_INTERVAL / 10), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                triggerTimer.restart();
            }
        });
        kickTimer.start();
    }

    public void setListReceiver(ListReceiver listReceiver) {
        this.listReceiver = listReceiver;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Exec.work(this);
    }

    @Override
    public void run() {
        try {
            final RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                boolean freeze = false;
                if (System.currentTimeMillis() > nextFreeze) {
                    nextFreeze = System.currentTimeMillis() + Storage.MAPPING_FREEZE_INTERVAL;
                    freeze = true;
                }
                sipModel.getDataSetModel().getDataSet().setRecMapping(recMapping, freeze);
                if (freeze) {
                    if (listReceiver != null) Exec.swing(new Runnable() {
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
        triggerTimer.restart();
    }

    @Override
    public void functionChanged(MappingModel mappingModel, MappingFunction function) {
        triggerTimer.restart();
    }

    @Override
    public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        triggerTimer.restart();
    }

    @Override
    public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        triggerTimer.restart();
    }

    @Override
    public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        triggerTimer.restart();
    }
}

