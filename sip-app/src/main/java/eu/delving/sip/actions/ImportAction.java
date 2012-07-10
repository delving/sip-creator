/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip.actions;

import eu.delving.sip.base.HarvestPool;
import eu.delving.sip.base.Harvestor;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import static eu.delving.sip.files.DataSetState.ABSENT;

/**
 * Import a new file
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ImportAction extends AbstractAction {
    private JDesktopPane parent;
    private SipModel sipModel;
    private final String RECENT_DIR = "recentImportDirectory";
    private JDialog dialog;
    private ChooseFileAction chooseFileAction = new ChooseFileAction();
    private HarvestAction harvestAction = new HarvestAction();
    private JFileChooser chooser = new JFileChooser("XML Metadata Source File");
    private HarvestPool harvestPool;

    public ImportAction(JDesktopPane parent, SipModel sipModel, HarvestPool harvestPool) {
        super("Import new data into this data set");
        putValue(Action.SMALL_ICON, SwingHelper.IMPORT_ICON);
        putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_I, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        this.parent = parent;
        this.sipModel = sipModel;
        this.harvestPool = harvestPool;
        this.dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Input Source", Dialog.ModalityType.APPLICATION_MODAL);
        setEnabled(false);
        prepareDialog();
        prepareChooser(sipModel);
        sipModel.getDataSetModel().addListener(new DataSetModel.SwingListener() {
            @Override
            public void stateChanged(DataSetModel model, DataSetState state) {
                setEnabled(state != ABSENT);
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        Dimension all = parent.getSize();
        Dimension d = dialog.getSize();
        dialog.setLocation((all.width - d.width) / 2, (all.height - d.height) / 2);
        dialog.setVisible(true);
    }

    private void prepareDialog() {
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                dialog.setVisible(false);
            }
        });
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bp.add(cancel);
        JPanel p = new JPanel(new GridLayout(1, 0, 15, 15));
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        p.add(new JButton(chooseFileAction));
        p.add(new JButton(harvestAction));
        dialog.getContentPane().add(p, BorderLayout.CENTER);
        dialog.getContentPane().add(bp, BorderLayout.SOUTH);
        dialog.pack();
    }

    private void prepareChooser(SipModel sipModel) {
        File directory = new File(sipModel.getPreferences().get(RECENT_DIR, System.getProperty("user.home")));
        chooser.setCurrentDirectory(directory);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().endsWith(".xml") || file.getName().endsWith(".xml.gz");
            }

            @Override
            public String getDescription() {
                return "XML or GZIP/XML Files";
            }
        });
        chooser.setMultiSelectionEnabled(false);
    }

    public boolean selectInputFile(File file) {
        if (!file.exists() || sipModel.getDataSetModel().isEmpty()) return false;
        String spec = sipModel.getDataSetModel().getDataSet().getSpec();
        boolean doImport = sipModel.getFeedback().confirm(
                "Verify your choice",
                String.format(
                        "<html>Import this file<br><br>" +
                                "<pre><strong>%s</strong></pre><br>" +
                                "into data set '<strong>%s</strong>'?<br>",
                        file.getAbsolutePath(),
                        spec // todo: could snag description and things from facts, if they were hardcoded
                )
        );
        if (doImport) {
            setEnabled(false);
            sipModel.importSource(file, new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                }
            });
            return true;
        }
        return false;
    }

    private class ChooseFileAction extends AbstractAction {
        private ChooseFileAction() {
            super("<html><center><br><h2>Read</h2>Import from XML dump file<br>from your computer's file system<br><br>");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            dialog.setVisible(false);
            int choiceMade = chooser.showOpenDialog(parent);
            if (choiceMade == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                sipModel.getPreferences().put(RECENT_DIR, file.getAbsolutePath());
                selectInputFile(file); // it's a boolean
            }
        }
    }

    private class HarvestAction extends AbstractAction {

        private HarvestAction() {
            super("<html><center><br><h2>Harvest</h2>Harvest from OAI-PMH server<br>for which you have the URL<br><br>");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            dialog.setVisible(false);
            if (sipModel.getDataSetModel().isEmpty()) return;
            Map<String, String> hints = sipModel.getDataSetModel().getDataSet().getHints();
            String url = hints.get(Storage.HARVEST_URL);
            String prefix = hints.get(Storage.HARVEST_PREFIX);
            String spec = hints.get(Storage.HARVEST_SPEC);
            JTextField harvestUrl = new JTextField(null == url ? "" : url, 45);
            JTextField harvestPrefix = new JTextField(null == prefix ? "" : prefix);
            JTextField harvestSpec = new JTextField(null == spec ? "" : spec);
            if (!sipModel.getFeedback().form("OAI-PMH server details", "Server", harvestUrl, "Prefix", harvestPrefix, "Spec", harvestSpec)) {
                return;
            }
            if (!StringUtils.isEmpty(harvestUrl.getText())) {
                try {
                    new URL(harvestUrl.getText());
                    hints.put(Storage.HARVEST_URL, harvestUrl.getText());
                    hints.put(Storage.HARVEST_PREFIX, harvestPrefix.getText());
                    hints.put(Storage.HARVEST_SPEC, harvestSpec.getText());
                    sipModel.getDataSetModel().getDataSet().setHints(hints);
                    performHarvest(harvestUrl.getText(), harvestPrefix.getText(), harvestSpec.getText());
                }
                catch (MalformedURLException e) {
                    sipModel.getFeedback().alert("Malformed URL: " + harvestUrl);
                }
                catch (StorageException e) {
                    sipModel.getFeedback().alert("Unable to save URL to hints");
                }
            }
        }

        private void performHarvest(final String harvestUrl, final String harvestPrefix, final String harvestSpec) {

            harvestPool.submit(new Harvestor(sipModel.getDataSetModel().getDataSet(), new Harvestor.Context() {

                @Override
                public File outputFile() {
                    try {
                        return sipModel.getDataSetModel().getDataSet().importedOutput();
                    }
                    catch (StorageException e) {
                        e.printStackTrace();
                        sipModel.getFeedback().alert(e.getMessage());
                    }
                    return null;
                }

                @Override
                public String harvestUrl() {
                    return harvestUrl;
                }

                @Override
                public String harvestPrefix() {
                    return harvestPrefix;
                }

                @Override
                public String harvestSpec() {
                    return harvestSpec;
                }
            }
            ));
        }
    }
}
