/*
 * Copyright 2010 DELVING BV
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

import eu.delving.sip.ProgressListener;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * The transformation from input record to output
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class OutputFrame extends FrameBase {
    private JCheckBox discardInvalidBox = new JCheckBox("Discard Invalid Records");
    private JCheckBox storeNormalizedBox = new JCheckBox("Store Normalized XML");
    //    private JLabel normalizeMessageLabel = new JLabel("?", JLabel.CENTER);
    private JFileChooser chooser = new JFileChooser("Normalized Data Output Directory");

    public OutputFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Output", false);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createOutputPanel(), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    private JComponent createSouth() {
        JButton validate = new JButton("Validate all records");
        JCheckBox discardInvalid = new JCheckBox("Discard invalid records");
        JButton upload = new JButton("Upload Dataset and mapping");
        upload.setEnabled(false);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(validate);
        p.add(discardInvalid);
        p.add(upload);
        return p;
    }

    // todo: view invalid records popup

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output Record"));
        JTextArea area = new JTextArea(sipModel.getRecordCompileModel().getOutputDocument());
        area.setEditable(false);
        p.add(scroll(area));
        return p;
    }

    private Action normalizeAction = new AbstractAction("Normalize") {
        private final String NORM_DIR = "normalizeDirectory";

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (storeNormalizedBox.isSelected()) {
                File normalizeDirectory = new File(sipModel.getPreferences().get(NORM_DIR, System.getProperty("user.home")));
                chooser.setSelectedFile(normalizeDirectory); // todo: this doesn't work for some reason
                chooser.setCurrentDirectory(normalizeDirectory.getParentFile());
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isDirectory();
                    }

                    @Override
                    public String getDescription() {
                        return "Directories";
                    }
                });
                chooser.setMultiSelectionEnabled(false);
                int choiceMade = chooser.showOpenDialog(OutputFrame.this);
                if (choiceMade == JFileChooser.APPROVE_OPTION) {
                    normalizeDirectory = chooser.getSelectedFile();
                    sipModel.getPreferences().put(NORM_DIR, normalizeDirectory.getAbsolutePath());
                    normalizeTo(normalizeDirectory);
                }
            }
            else {
                normalizeTo(null);
            }
        }

        private void normalizeTo(File normalizeDirectory) {
            String message;
            if (normalizeDirectory != null) {
                message = String.format(
                        "<html><h3>Transforming the raw data of '%s' into '%s' format</h3><br>" +
                                "Writing to %s ",
                        sipModel.getDataSetStore().getSpec(),
                        sipModel.getMappingModel().getRecordMapping().getPrefix(),
                        normalizeDirectory
                );
            }
            else {
                message = String.format(
                        "<html><h3>Transforming the raw data of '%s' into '%s' format</h3>",
                        sipModel.getDataSetStore().getSpec(),
                        sipModel.getMappingModel().getRecordMapping().getPrefix()
                );
            }
            ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(OutputFrame.this),
                    "<html><h2>Normalizing</h2>",
                    message,
                    0, 100
            );
            sipModel.normalize(normalizeDirectory, discardInvalidBox.isSelected(), new ProgressListener.Adapter(progressMonitor) {
                @Override
                public void swingFinished(boolean success) {
                    setEnabled(true);
                }
            });
        }
    };

}
