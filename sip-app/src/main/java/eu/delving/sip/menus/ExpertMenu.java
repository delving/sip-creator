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

package eu.delving.sip.menus;

import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.StatsModel;
import eu.delving.stats.Stats;
import org.apache.commons.lang.StringEscapeUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static eu.delving.sip.files.Storage.SHACL_VALIDATION;
import static eu.delving.sip.files.Storage.SOURCE_INCLUDED;
import static eu.delving.sip.files.Storage.XSD_VALIDATION;

import eu.delving.sip.model.SipProperties;

/**
 * Special functions for experts, not to be spoken of in mixed company, or among
 * people with potential heart
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
        add(new ToggleSHACLValidation());
        add(new SourceIncludedAction());
        add(new MaxUniqueValueLengthAction());
        add(new ReloadMappingAction());
        add(new ToggleFrameArrangements());
        add(new ShowMemory());
        add(new RuntimeConfigAction());
    }

    private class MaxUniqueValueLengthAction extends AbstractAction {

        private MaxUniqueValueLengthAction() {
            super("Set Maximum Unique Value Length");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String answer = sipModel.getFeedback().ask(
                    "Enter the maximum length for unique element value",
                    String.valueOf(Stats.DEFAULT_MAX_UNIQUE_VALUE_LENGTH));
            if (answer != null) {
                answer = answer.trim();
                try {
                    int max = Integer.parseInt(answer);
                    sipModel.getStatsModel().setMaxUniqueValueLength(max);
                } catch (NumberFormatException e) {
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
            SipProperties sipProperties = new SipProperties();
            String sourceIncludedString = sipProperties.getProp().getProperty(SOURCE_INCLUDED, "false");
            String answer = sipModel.getFeedback().ask(
                    "Type 'true' if you want to have source included in the upload (global setting)",
                    sourceIncludedString);
            if (answer != null) {
                answer = answer.trim();
                boolean sourceIncluded = Boolean.parseBoolean(answer);
                sipProperties.getProp().setProperty(SOURCE_INCLUDED, String.valueOf(sourceIncluded));
                sipProperties.saveProperties();
            }
        }
    }

    private class ReloadMappingAction extends AbstractAction {
        private ReloadMappingAction() {
            super("Reload record definition");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (!sipModel.getMappingModel().hasRecMapping())
                return;
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
                        RecMapping recMapping = dsm.getRecMapping();
                        DataSet dataSet = dsm.getDataSet();
                        recMapping.validateMappings(new StatsModel.SourceTreeImpl(sipModel.getStatsModel()));
                        dataSet.setRecMapping(recMapping, true);
                        mm.setRecMapping(recMapping);
                    } catch (StorageException e) {
                        sipModel.getFeedback().alert("Cannot setNarthexEntries the mapping", e);
                    }
                }
            });
        }
    }

    private class ToggleFrameArrangements extends JCheckBoxMenuItem implements ItemListener {
        public ToggleFrameArrangements() {
            super("Toggle Frame Arrangement Editing");
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            boolean selected = state == ItemEvent.SELECTED;
            for (FrameBase frame : allFrames.getFrames())
                frame.toggleEditMenu(selected);
        }
    }

    private class ToggleXSDValidation extends JCheckBoxMenuItem implements ItemListener {
        public ToggleXSDValidation() {
            super("Toggle XSD Validation");
            boolean state = sipModel.getPreferences().getProperty(XSD_VALIDATION, "false").contentEquals("true");
            setState(state);
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            boolean selected = state == ItemEvent.SELECTED;
            sipModel.getPreferences().setProperty(XSD_VALIDATION, String.valueOf(selected));
            sipModel.saveProperties();
        }
    }

    private class ToggleSHACLValidation extends JCheckBoxMenuItem implements ItemListener {
        public ToggleSHACLValidation() {
            super("Toggle SHACL Validation");
            boolean state = sipModel.getPreferences().getProperty(SHACL_VALIDATION, "false").contentEquals("true");
            setState(state);
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            boolean selected = state == ItemEvent.SELECTED;
            sipModel.getPreferences().setProperty(SHACL_VALIDATION, String.valueOf(selected));
            sipModel.saveProperties();
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
                            show("Free Memory", r.freeMemory()));
        }

        private String show(String name, long value) {
            return String.format("%s: %dMb\n", name, value / 1024 / 1024);
        }
    }

    private class RuntimeConfigAction extends AbstractAction {
        final static String actionTitle = "Runtime Configuration";

        private RuntimeConfigAction() {
            super(actionTitle);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File exejarDir = null;
            try {
                exejarDir = new File(ExpertMenu.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getParentFile();
            } catch (Exception ex) {
                // Ignore for now
            }
            final File exejarDir_ = exejarDir;
            File cfgFile = null, cfgFileBackup = null;
            String cfgContents = null;
            if (exejarDir != null) {
                cfgFile = new File(exejarDir, "SIP-Creator.cfg");
                cfgFileBackup = new File(exejarDir, "SIP-Creator.cfg.original");
                try {
                    cfgContents = Files.readString(cfgFile.toPath());
                    if (!cfgFileBackup.exists()) {
                        Files.copy(cfgFile.toPath(), cfgFileBackup.toPath());
                    }
                } catch (IOException ex) {
                    // Either the file couldn't be read (doesn't exist? - probably we're not in a native package)
                    //  or the file couldn't be copied (no write permission? - then probably we can't edit either)
                }
            }

            String introText = "<html><body>BE CAREFUL when editing the configuration below.<br>" +
                "Incorrect configuration will prevent the application from starting.<br>" +
                "Only add lines, or modify already added lines, under [JavaOptions].<br><br>";
            boolean isEditable = true;
            if (cfgContents != null && cfgFileBackup.exists()) {
                 introText += "In case of mistakes, please replace <b>%s</b> with <b>%s</b><br>" +
                    "in the folder that can be opened using the button, or reinstall the application.<br>" +
                    "Changes take effect when the application is restarted.";
                introText = String.format(introText,
                    StringEscapeUtils.escapeHtml(cfgFile.getName()),
                    StringEscapeUtils.escapeHtml(cfgFileBackup.getName())
                );
            } else {
                isEditable = false;
                introText += "In the folder that can be opened using the button, edit the file: <b>%s</b><br>" +
                    "Changes take effect when the application is restarted.<br>" +
                    "Please make a backup of the file, or reinstall the application in case of mistakes.";
                introText = String.format(introText,
                    StringEscapeUtils.escapeHtml(cfgFile.getName()));
                if (cfgContents == null) {
                    introText += "<br><br>If you are running the application using <code>java -jar</code> " +
                        "then add options to the command line instead.";
                }
            }

            JPanel message = new JPanel();
            message.setLayout(new GridBagLayout());
            JLabel introLabel = new JLabel(introText);
            introLabel.setBorder(new EmptyBorder(0,10,10,10));
            JLabel memoryLabel = new JLabel("To configure memory, add lines such as:");
            memoryLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
            JTextArea memoryTextArea = new JTextArea("java-options=-Xms4g\njava-options=-Xmx4g");
            memoryTextArea.setEditable(false);
            JLabel configLabel = new JLabel("SIP-Creator.cfg:");
            configLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
            JTextArea configTextArea = new JTextArea(cfgContents);
            configTextArea.setLineWrap(true);
            configTextArea.setWrapStyleWord(true);
            configTextArea.setEditable(isEditable);
            JScrollPane configScrollPane = new JScrollPane(configTextArea,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            configScrollPane.setPreferredSize(new Dimension(600, 200));
            JButton openFolderButton = new JButton("Open folder");
            openFolderButton.setEnabled(exejarDir != null);
            openFolderButton.addActionListener(e1 -> {
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.open(exejarDir_);
                } catch (IOException ex) {
                    // Ignore
                }
            });
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1;
            constraints.gridx = 0;
            message.add(introLabel, constraints);
            message.add(memoryLabel, constraints);
            message.add(memoryTextArea, constraints);
            message.add(configLabel, constraints);
            message.add(configScrollPane, constraints);
            message.add(openFolderButton, constraints);
            Object[] newOptions = { "OK", "Cancel" };
            int newOption = JOptionPane.showOptionDialog(sipModel.getDesktop(),
                message,
                actionTitle,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                newOptions,
                newOptions[1]);
            if (newOption == 0 && isEditable) {
                try {
                    Files.writeString(cfgFile.toPath(), configTextArea.getText());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(sipModel.getDesktop(),
                        "Couldn't save config file: " + ex,
                        actionTitle,
                        JOptionPane.WARNING_MESSAGE);
                }
            }
        }
    }

}
