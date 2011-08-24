/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

package eu.delving.sip.frames;

import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.FieldMappingListModel;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Choose and execute from the possible obvious mappings
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ObviousMappingsPopup extends FrameBase {
    private ObviousListModel obviousListModel = new ObviousListModel();
    private JList obviousMappingsList;

    public ObviousMappingsPopup(FrameBase parent) {
        super(parent, parent.getSipModel(), "Obvious Mappings", true);
        obviousMappingsList = new JList(obviousListModel);
        obviousMappingsList.setCellRenderer(new FieldMappingListModel.CellRenderer());
        obviousMappingsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void mappingChanged(RecordMapping recordMapping, FieldMapping fieldMapping) {
                if (fieldMapping == null) {
                    obviousListModel.refresh();
                }
            }
        });
        setDefaultSize(400, 400);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createCenter(), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
        obviousListModel.refresh();
    }

    private JComponent createCenter() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Obvious Mappings"));
        p.add(scroll(obviousMappingsList));
        return p;
    }

    private JComponent createSouth() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(new JButton(new CreateMappingsAction()));
        p.add(createFinishedPanel());
        return p;
    }

    private JPanel createFinishedPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(new JButton(new FinishedAction()));
        return panel;
    }

    private class CreateMappingsAction extends AbstractAction {
        private CreateMappingsAction() {
            super("Create Selected Mappings");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            for (Object fm : obviousMappingsList.getSelectedValues()) {
                FieldMapping fieldMapping = (FieldMapping) fm;
                sipModel.addFieldMapping(fieldMapping);
            }
            obviousListModel.refresh();
        }
    }

    private class FinishedAction extends AbstractAction {

        private FinishedAction() {
            putValue(NAME, "Finished");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            closeFrame();
        }
    }

    private class ObviousListModel extends AbstractListModel {
        private List<FieldMapping> obvious = new ArrayList<FieldMapping>();

        public void refresh() {
            CodeGenerator codeGenerator = new CodeGenerator();
            List<FieldMapping> fresh = codeGenerator.createObviousMappings(sipModel.getUnmappedFields(), sipModel.getVariables());
            if (!obvious.isEmpty()) {
                int size = obvious.size();
                obvious.clear();
                fireIntervalRemoved(this, 0, size);
            }
            if (!fresh.isEmpty()) {
                obvious.addAll(fresh);
                fireIntervalAdded(this, 0, getSize());
            }
            else {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        closeFrame();
                    }
                });
            }
            ObviousMappingsPopup.this.getAction().setEnabled(!fresh.isEmpty());
        }

        @Override
        public int getSize() {
            return obvious.size();
        }

        @Override
        public Object getElementAt(int i) {
            return obvious.get(i);
        }
    }

}
