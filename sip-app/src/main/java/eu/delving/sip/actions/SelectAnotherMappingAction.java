/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.actions;

import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Select another mapping for the same dataset
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SelectAnotherMappingAction extends AbstractAction implements Work {
    public static final String DEFAULT_STRING = "Select another schema";
    public static final String SINGLE_SELECTION_HTML = "Select the %s schema";
    private SipModel sipModel;
    private List<SchemaVersion> choices = new ArrayList<SchemaVersion>();

    public SelectAnotherMappingAction(final SipModel sipModel) {
        super(DEFAULT_STRING);
        this.sipModel = sipModel;
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                choices.clear();
                if (mappingModel.hasRecMapping()) {
                    String prefix = mappingModel.getPrefix();
                    for (SchemaVersion schemaVersion : sipModel.getDataSetModel().getDataSet().getSchemaVersions()) {
                        if (schemaVersion.getPrefix().equals(prefix)) continue;
                        choices.add(schemaVersion);
                    }
                    sipModel.exec(new Swing() {
                        @Override
                        public void run() {
                            switch (choices.size()) {
                                case 1:
                                    setEnabled(true);
                                    putValue(Action.NAME, String.format(SINGLE_SELECTION_HTML, choices.get(0).getPrefix().toUpperCase()));
                                    break;
                                default: // 0 or 2 or more
                                    setEnabled(!choices.isEmpty());
                                    putValue(Action.NAME, DEFAULT_STRING);
                                    break;
                            }
                        }
                    });
                }
            }
        });
        setEnabled(false);
        putValue(Action.SMALL_ICON, SwingHelper.ICON_SELECT_ANOTHER);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        sipModel.exec(this);
    }

    @Override
    public void run() {
        if (choices.size() == 1) {
            sipModel.setDataSetPrefix(sipModel.getDataSetModel().getDataSet(), choices.get(0).getPrefix(), null);
        }
        else {
            String prefix = askForPrefix(choices);
            if (prefix == null) return;
            sipModel.setDataSetPrefix(sipModel.getDataSetModel().getDataSet(), prefix, null);
        }
    }

    @Override
    public Job getJob() {
        return Job.SELECT_ANOTHER_MAPPING;
    }

    private String askForPrefix(List<SchemaVersion> schemaVersions) {
        JPanel buttonPanel = new JPanel(new GridLayout(0, 1));
        ButtonGroup buttonGroup = new ButtonGroup();
        for (SchemaVersion schemaVersion : schemaVersions) {
            JRadioButton b = new JRadioButton(schemaVersion.getPrefix() + " mapping");
            if (buttonGroup.getButtonCount() == 0) b.setSelected(true);
            b.setActionCommand(schemaVersion.getPrefix());
            buttonGroup.add(b);
            buttonPanel.add(b);
        }
        return sipModel.getFeedback().form("Choose Schema", buttonPanel) ? buttonGroup.getSelection().getActionCommand() : null;
    }

}
