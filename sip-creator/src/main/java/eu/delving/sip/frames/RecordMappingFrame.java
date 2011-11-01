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
import eu.delving.metadata.MappingModelAdapter;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.FieldMappingListModel;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Refining the mapping interactively
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecordMappingFrame extends FrameBase {
    private JButton removeMappingButton = new JButton("Remove Selected Mapping");
    private JList mappingList;
    private JTextArea codeArea = new JTextArea();
    private CopyTemplate copyTemplate = new CopyTemplate();
    private ApplyTemplate applyTemplate = new ApplyTemplate();

    public RecordMappingFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Record Mapping", false);
        mappingList = new JList(sipModel.getFieldMappingListModel());
        mappingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mappingList.setCellRenderer(new FieldMappingListModel.CellRenderer());
        removeMappingButton.setEnabled(false);
        setJMenuBar(createBar());
        wireUp();
    }

    private JMenuBar createBar() {
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("Template");
        menu.add(copyTemplate);
        menu.add(applyTemplate);
        bar.add(menu);
        return bar;
    }

    @Override
    protected void buildContent(Container content) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Field List", createListPanel());
        tabs.add("Mapping Code", createCodePanel());
        add(tabs, BorderLayout.CENTER);
    }

    @Override
    protected void refresh() {
    }

    private JPanel createCodePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(scroll(codeArea), BorderLayout.CENTER);
        return p;
    }

    private JPanel createListPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(createFieldMappingListPanel(), BorderLayout.CENTER);
        p.add(removeMappingButton, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createFieldMappingListPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Field Mappings"));
        p.add(scroll(mappingList));
        p.setMaximumSize(new Dimension(200, 2000));
        return p;
    }

    private void wireUp() {
        sipModel.getMappingModel().addListener(new MappingModelAdapter() {

            @Override
            public void recordMappingChanged(RecordMapping recordMapping) {
                if (recordMapping != null) {
                    String code = recordMapping.toDisplayCode();
                    codeArea.setText(code);
                    copyTemplate.enableFor(recordMapping);
                    applyTemplate.enableFor(recordMapping);
                }
                else {
                    codeArea.setText("// no code");
                    copyTemplate.setEnabled(false);
                    applyTemplate.setEnabled(false);
                }
            }

            @Override
            public void recordMappingSelected(RecordMapping recordMapping) {
                recordMappingChanged(recordMapping);
            }
        });
        removeMappingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final FieldMapping fieldMapping = (FieldMapping) mappingList.getSelectedValue();
                if (fieldMapping != null) {
                    Exec.work(
                            new Runnable() {
                                @Override
                                public void run() {
                                    sipModel.getMappingModel().removeMapping(fieldMapping);

                                }
                            }
                    );
                }
            }
        });
        mappingList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                final FieldMapping fieldMapping = (FieldMapping) mappingList.getSelectedValue();
                removeMappingButton.setEnabled(fieldMapping != null);
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().selectFieldMapping(fieldMapping);
                    }
                });
            }
        });
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(400, 250);
    }

    private RecordMapping template;

    private class CopyTemplate extends AbstractAction {

        private CopyTemplate() {
            super("Copy current record mapping to apply elsewhere");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    template = sipModel.getMappingModel().getRecordMapping();
                }
            });
        }

        public void enableFor(RecordMapping mapping) {
            setEnabled(mapping != null && !mapping.getFieldMappings().isEmpty());
        }
    }

    private class ApplyTemplate extends AbstractAction {

        private ApplyTemplate() {
            super("Apply copied template to start this record mapping");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (template != null) {
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().applyTemplate(template);
                    }
                });
            }
        }

        public void enableFor(RecordMapping mapping) {
            setEnabled(mapping != null && template != null && mapping.getFieldMappings().isEmpty());
        }
    }
}
