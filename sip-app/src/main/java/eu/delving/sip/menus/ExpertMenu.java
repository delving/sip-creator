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

package eu.delving.sip.menus;

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import eu.delving.stats.Stats;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import static eu.delving.sip.files.Storage.XSD_VALIDATION;

/**
 * Special functions for experts, not to be spoken of in mixed company, or among people with potential heart
 * conditions.
 *
 */

public class ExpertMenu extends JMenu {
    private SipModel sipModel;
    private AllFrames allFrames;

    public ExpertMenu(final SipModel sipModel, AllFrames allFrames) {
        super("Expert");
        this.sipModel = sipModel;
        this.allFrames = allFrames;
        add(new ToggleXSDValidation());
        add(new SourceIncludedAction());
        add(new MaxUniqueValueLengthAction());
        add(new ReloadMappingAction());
        add(new ToggleFrameArrangements());
        add(new ShowMemory());
        add(new ShowMemoryConfigAction());
    }

    private class MaxUniqueValueLengthAction extends AbstractAction {

        private MaxUniqueValueLengthAction() {
            super("Set Maximum Unique Value Length");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String answer = sipModel.getFeedback().ask(
                    "Enter the maximum length for unique element value",
                    String.valueOf(Stats.DEFAULT_MAX_UNIQUE_VALUE_LENGTH)
            );
            if (answer != null) {
                answer = answer.trim();
                try {
                    int max = Integer.parseInt(answer);
                    sipModel.getStatsModel().setMaxUniqueValueLength(max);
                }
                catch (NumberFormatException e) {
                    sipModel.getFeedback().alert("Not a number: " + answer);
                }
            }
        }
    }

    private class SourceIncludedAction extends AbstractAction {

        private SourceIncludedAction() {
            super("Include source in upload");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String sourceIncludedString = sipModel.getStatsModel().getHintsModel().get("sourceIncluded");
            if (sourceIncludedString == null) sourceIncludedString = "false";
            String answer = sipModel.getFeedback().ask(
                    "Type 'true' if you want to have source included in the upload",
                    sourceIncludedString
            );
            if (answer != null) {
                answer = answer.trim();
                try {
                    boolean sourceIncluded = Boolean.parseBoolean(answer);
                    sipModel.getStatsModel().getHintsModel().set("sourceIncluded", String.valueOf(sourceIncluded));
                }
                catch (NumberFormatException e) {
                    sipModel.getFeedback().alert("Must be 'true' or 'false': " + answer);
                }
            }
        }
    }

    private class ReloadMappingAction extends AbstractAction {
        private ReloadMappingAction() {
            super("Reload record definition");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (!sipModel.getMappingModel().hasRecMapping()) return;
            sipModel.exec(new Work.DataSetPrefixWork() {

                final MappingModel mm = sipModel.getMappingModel();
                final DataSetModel dsm = sipModel.getDataSetModel();

                @Override
                public String getPrefix() {
                    return sipModel.getMappingModel().getPrefix();
                }

                @Override
                public DataSet getDataSet() {
                    return sipModel.getDataSetModel().getDataSet();
                }

                @Override
                public Job getJob() {
                    return Job.RELOAD_MAPPING;
                }

                @Override
                public void run() {
                    try {
                        dsm.getDataSet().setRecMapping(dsm.getRecMapping(), true);
                        mm.setRecMapping(dsm.getRecMapping());
                    }
                    catch (StorageException e) {
                        sipModel.getFeedback().alert("Cannot setNarthexEntries the mapping", e);
                    }
                }
            });
        }
    }

    private class ToggleFrameArrangements extends AbstractAction {
        public ToggleFrameArrangements() {
            super("Toggle Frame Arrangement Editing");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            for (FrameBase frame : allFrames.getFrames()) frame.toggleEditMenu();
        }
    }


    private class ToggleXSDValidation extends JCheckBoxMenuItem implements ItemListener {
        public ToggleXSDValidation() {
            super("Toggle XSD Validation");
            boolean state = sipModel.getPreferences().getBoolean(XSD_VALIDATION, false);
            setState(state);
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            boolean selected = state == ItemEvent.SELECTED;
            sipModel.getPreferences().putBoolean(XSD_VALIDATION, selected);
        }
    }


    private class ShowMemory extends AbstractAction {
        public ShowMemory() {
            super("Memory Capacity");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Runtime r = Runtime.getRuntime();
            sipModel.getFeedback().alert(
                    show("Total Memory", r.totalMemory()) +
                            show("Max Memory", r.maxMemory()) +
                            show("Free Memory", r.freeMemory())
            );
        }

        private String show(String name, long value) {
            return String.format("%s: %dMb\n", name, value / 1024 / 1024);
        }
    }


    private class ShowMemoryConfigAction extends AbstractAction {

        private ShowMemoryConfigAction() {
            super("Show how to configure more memory");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String os = System.getProperty("os.name");
            Runtime rt = Runtime.getRuntime();
            int totalMemory = (int) (rt.totalMemory() / 1024 / 1024);
            StringBuilder out = new StringBuilder();
            String JAR_NAME = "SIP-Creator-2014-XX-XX.jar";
            if (os.startsWith("Windows")) {
                out.append(":: SIP-Creator Startup Batch file for Windows (more memory than ").append(totalMemory).append("Mb)\n");
                out.append("java -jar -Xms1024m -Xmx1024m ").append(JAR_NAME);
            }
            else if (os.startsWith("Mac")) {
                out.append("# SIP-Creator Startup Script for Mac OSX (more memory than ").append(totalMemory).append("Mb)\n");
                out.append("java -jar -Xms1024m -Xmx1024m ").append(JAR_NAME);
            }
            else {
                System.out.println("Unrecognized OS: " + os);
            }
            String script = out.toString();
            final JDialog dialog = new JDialog(null, "Memory Not Configured Yet!", Dialog.ModalityType.APPLICATION_MODAL);
            JTextArea scriptArea = new JTextArea(3, 40);
            scriptArea.setText(script);
            scriptArea.setSelectionStart(0);
            scriptArea.setSelectionEnd(script.length());
            JPanel scriptPanel = new JPanel(new BorderLayout());
            scriptPanel.setBorder(BorderFactory.createTitledBorder("Script File"));
            scriptPanel.add(scriptArea, BorderLayout.CENTER);
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            JButton ok = new JButton("OK");
            ok.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dialog.setVisible(false);
                }
            });
            buttonPanel.add(ok);
            JPanel centralPanel = new JPanel(new GridLayout(0, 1));
            centralPanel.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
            centralPanel.add(new JLabel(
                    "<html><b>The SIP-Creator started directly can have too little default memory allocated." +
                            "<br>It should be started with the following script:</b>"
            ));
            centralPanel.add(scriptPanel);
            centralPanel.add(new JLabel(
                    "<html><b>Please copy the above text into a batch or script file and execute that instead.</b>"
            ));
            dialog.getContentPane().add(centralPanel, BorderLayout.CENTER);
            dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
            dialog.pack();
            Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (int) ((dimension.getWidth() - dialog.getWidth()) / 2);
            int y = (int) ((dimension.getHeight() - dialog.getHeight()) / 2);
            dialog.setLocation(x, y);
            dialog.setVisible(true);

        }
    }
}
