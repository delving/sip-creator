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

package eu.delving.sip.base;

import eu.delving.sip.ProgressListener;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Import a new file
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class ImportAction extends AbstractAction {
    private JDesktopPane parent;
    private SipModel sipModel;
    private final String RECENT_DIR = "recentImportDirectory";
    private JFileChooser chooser = new JFileChooser("XML Metadata Source File");

    public ImportAction(JDesktopPane parent, SipModel sipModel) {
        super("Import");
        this.parent = parent;
        this.sipModel = sipModel;
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

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        int choiceMade = chooser.showOpenDialog(parent);
        if (choiceMade == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            sipModel.getPreferences().put(RECENT_DIR, file.getAbsolutePath());
            selectInputFile(file); // it's a boolean
        }
    }

    public boolean selectInputFile(File file) {
        if (!file.exists()) {
            return false;
        }
        if (!sipModel.hasDataSet()) {
            return false;
        }
        String spec = sipModel.getDataSetModel().getDataSet().getSpec();
        int doImport = JOptionPane.showConfirmDialog(
                parent,
                String.format(
                        "<html>Are you sure you wish to import this file<br><br>" +
                                "<pre><strong>%s</strong></pre><br>" +
                                "as a Data Set called '<strong>%s</strong>'?<br><br>",
                        file.getAbsolutePath(),
                        spec // todo: could snag description and things from facts, if they were hardcoded
                ),
                "Verify your choice",
                JOptionPane.YES_NO_OPTION
        );
        if (doImport == JOptionPane.YES_OPTION) {
            setEnabled(false);
            ProgressListener listener = sipModel.getFeedback().progressListener("Importing", "Storing data for "+spec);
            listener.onFinished(new ProgressListener.End() {
                @Override
                public void finished(boolean success) {
                    setEnabled(true);
                }
            });
            sipModel.importSource(file, listener);
            return true;
        }
        return false;
    }

}
