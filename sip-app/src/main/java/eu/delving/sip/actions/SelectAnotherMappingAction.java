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
import eu.delving.sip.base.Work;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.KeystrokeHelper.*;
import static eu.delving.sip.base.SwingHelper.ICON_SELECT_ANOTHER;

/**
 * Select another mapping for the same dataset
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SelectAnotherMappingAction extends AbstractAction implements Work {
    public static final String DEFAULT_STRING = "Select other schema";
    public static final String SINGLE_SELECTION_STRING = "Select the %s schema";
    private SipModel sipModel;
    private List<SchemaVersion> choices = new ArrayList<SchemaVersion>();

    public SelectAnotherMappingAction(final SipModel sipModel) {
        configAction(this, DEFAULT_STRING, ICON_SELECT_ANOTHER, MENU_S);
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
                                    putValue(Action.NAME, String.format(SINGLE_SELECTION_STRING, choices.get(0).getPrefix().toUpperCase()));
                                    break;
                                default: // 0 or 2 or more
                                    setEnabled(!choices.isEmpty());
                                    putValue(Action.NAME, DEFAULT_STRING);
                                    break;
                            }
                            addStrokeToName(SelectAnotherMappingAction.this);
                        }
                    });
                }
            }
        });
        setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        sipModel.exec(this);
    }

    @Override
    public void run() {
        switch (choices.size()) {
            case 0:
                // nothing to do, strange situation in the first place
                break;
            case 1:
                sipModel.setDataSetPrefix(sipModel.getDataSetModel().getDataSet(), choices.get(0).getPrefix(), null);
                break;
            default:
                String prefix = askForPrefix(choices);
                if (prefix == null) return;
                sipModel.setDataSetPrefix(sipModel.getDataSetModel().getDataSet(), prefix, null);
                break;
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
        if (!sipModel.getFeedback().form("Choose Schema", buttonPanel)) {
            return null;
        }
        ButtonModel selection = buttonGroup.getSelection();
        if (selection == null) {
            return null;
        }
        return selection.getActionCommand();
    }

}
