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
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.StorageException;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Save the mapping whenever things change
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingSaveTimer implements MappingModel.ChangeListener, MappingModel.SetListener, ActionListener, Runnable {
    private SipModel sipModel;
    private Timer timer = new Timer(200, this);


    public MappingSaveTimer(SipModel sipModel) {
        this.sipModel = sipModel;
        timer.setRepeats(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Exec.work(this);
    }

    @Override
    public void run() {
        try {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                sipModel.getDataSetModel().getDataSet().setRecMapping(recMapping);
            }
        }
        catch (StorageException e) {
            sipModel.getFeedback().alert("Unable to save mapping", e);
        }
    }

    @Override
    public void recMappingSet(MappingModel mappingModel) {
        timer.restart();
    }

    @Override
    public void factChanged(MappingModel mappingModel, String name) {
        timer.restart();
    }

    @Override
    public void functionChanged(MappingModel mappingModel, String name) {
        timer.restart();
    }

    @Override
    public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        timer.restart();
    }

    @Override
    public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        timer.restart();
    }

    @Override
    public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        timer.restart();
    }
}

