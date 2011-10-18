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

import eu.delving.metadata.FieldMapping;
import eu.delving.sip.base.Exec;
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
        setDefaultSize(400, 400);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createCenter(), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    private JComponent createCenter() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Obvious Mappings"));
        p.add(scroll(obviousMappingsList));
        return p;
    }

    private JComponent createSouth() {
        JPanel bp = new JPanel(new GridLayout(1, 0));
        bp.add(new JButton(new SelectAllAction()));
        bp.add(new JButton(new SelectNoneAction()));
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(bp);
        p.add(new JButton(new CreateMappingsAction()));
        p.add(createFinishedPanel());
        return p;
    }

    private JPanel createFinishedPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(new JButton(new FinishedAction()));
        return panel;
    }

    public void setObviousMappings(List<FieldMapping> obvious) {
        obviousListModel.setList(obvious);
    }

    private class SelectAllAction extends AbstractAction {

        private SelectAllAction() {
            super("All");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            obviousMappingsList.getSelectionModel().setSelectionInterval(0, obviousListModel.getSize() - 1);
        }
    }

    private class SelectNoneAction extends AbstractAction {

        private SelectNoneAction() {
            super("None");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            obviousMappingsList.getSelectionModel().clearSelection();
        }
    }

    private class CreateMappingsAction extends AbstractAction {
        private CreateMappingsAction() {
            super("Create Selected Mappings");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    List<FieldMapping> additions = new ArrayList<FieldMapping>();
                    for (Object fm : obviousMappingsList.getSelectedValues()) {
                        additions.add((FieldMapping) fm);
                    }
                    sipModel.getMappingModel().addMappings(additions);
                }
            });
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

        @Override
        public int getSize() {
            return obvious.size();
        }

        @Override
        public Object getElementAt(int i) {
            return obvious.get(i);
        }

        public void setList(List<FieldMapping> fresh) {
            if (!obvious.isEmpty()) {
                int wasSize = getSize();
                obvious.clear();
                fireIntervalRemoved(this, 0, wasSize);
            }
            obvious.addAll(fresh);
            if (obvious.isEmpty()) {
                closeFrame();
            }
            else {
                fireIntervalAdded(this, 0, getSize());
            }
            ObviousMappingsPopup.this.getAction().setEnabled(!obvious.isEmpty());
        }
    }

}
