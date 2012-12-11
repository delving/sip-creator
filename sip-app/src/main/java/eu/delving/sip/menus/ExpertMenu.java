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

import eu.delving.metadata.Hasher;
import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;
import eu.delving.sip.xml.SourceConverter;
import eu.delving.stats.Stats;
import org.apache.commons.io.FileUtils;

import javax.swing.AbstractAction;
import javax.swing.JDesktopPane;
import javax.swing.JMenu;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Special functions for experts, not to be spoken of in mixed company, or among people with potential heart
 * conditions.
 *
 * @author Gerald de Jong, Beautiful Code BV, <gerald@delving.eu>
 */

public class ExpertMenu extends JMenu {
    private SipModel sipModel;
    private AllFrames allFrames;

    public ExpertMenu(JDesktopPane desktop, final SipModel sipModel, CultureHubClient cultureHubClient, AllFrames allFrames) {
        super("Expert");
        this.sipModel = sipModel;
        this.allFrames = allFrames;
        add(new MaxUniqueValueLengthAction());
        add(new UniqueConverterAction());
        add(new WriteOutputAction());
        add(new ReloadMappingAction());
        add(new ToggleFrameArrangements());
//        add(new MediaImportAction(desktop, sipModel));
//        if (cultureHubClient != null) add(new UploadMediaAction(cultureHubClient));
        int anonRecords = Integer.parseInt(System.getProperty(SourceConverter.ANONYMOUS_RECORDS_PROPERTY, "0"));
        if (anonRecords > 0) add(new CreateSampleDataSetAction());
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

    private class UniqueConverterAction extends AbstractAction {

        private UniqueConverterAction() {
            super("Set Converter for the Unique value");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String answer = sipModel.getFeedback().ask(
                    String.format(
                            "Enter a regular expression (executed by String.replaceFirst) in the form of 'from%sto'. \n" +
                                    "At identifier conversion time all ':' are converted to '-', so keep that in mind \n" +
                                    "while writing regular expressions.'",
                            SourceConverter.CONVERTER_DELIMITER
                    ),
                    sipModel.getStatsModel().getUniqueValueConverter()
            );
            if (answer != null) {
                answer = answer.trim();
                sipModel.getStatsModel().setUniqueValueConverter(answer);
                sipModel.exec(new Work.DataSetWork() {
                    @Override
                    public void run() {
                        try {
                            if (sipModel.getDataSetModel().isEmpty()) return;
                            sipModel.getDataSetModel().getDataSet().deleteSource();
                        }
                        catch (StorageException e) {
                            sipModel.getFeedback().alert("Unable to delete source", e);
                        }
                    }

                    @Override
                    public Job getJob() {
                        return Job.DELETE_SOURCE;
                    }

                    @Override
                    public DataSet getDataSet() {
                        return sipModel.getDataSetModel().getDataSet();
                    }
                });
            }
        }
    }

    private class WriteOutputAction extends AbstractAction {

        private WriteOutputAction() {
            super("Write XML output of the validation");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String answer = sipModel.getFeedback().ask(
                    "Enter the directory where output is to be stored",
                    sipModel.getPreferences().get(FileProcessor.OUTPUT_FILE_PREF, "")
            );
            if (answer != null) {
                answer = answer.trim();
                File directory = new File(answer);
                if (!directory.exists()) {
                    failedAnswer(answer + " doesn't exist");
                }
                else if (!directory.isDirectory()) {
                    failedAnswer(answer + " is not a directory");
                }
                else {
                    sipModel.getPreferences().put(FileProcessor.OUTPUT_FILE_PREF, directory.getAbsolutePath());
                }
            }
        }

        private void failedAnswer(String message) {
            sipModel.getFeedback().alert(message);
            sipModel.getPreferences().put(FileProcessor.OUTPUT_FILE_PREF, "");
        }
    }

    private class UploadMediaAction extends AbstractAction {
        private CultureHubClient cultureHubClient;

        private UploadMediaAction(CultureHubClient cultureHubClient) {
            super("Upload media files");
            this.cultureHubClient = cultureHubClient;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            setEnabled(false);
            if (sipModel.getDataSetModel().isEmpty()) return;
            try {
                cultureHubClient.uploadMedia(sipModel.getDataSetModel().getDataSet(), new Swing() {
                    @Override
                    public void run() {
                        setEnabled(true);
                    }
                });
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to upload media", e);
            }
        }
    }

    private class CreateSampleDataSetAction extends AbstractAction {

        private CreateSampleDataSetAction() {
            super("Create a sample dataset for culture hub testing");
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            String answer = sipModel.getFeedback().ask(
                    "Enter the directory where the dataset is to be stored",
                    System.getProperty("user.home")
            );
            if (answer != null) {
                answer = answer.trim();
                File directory = new File(answer);
                if (!directory.exists()) {
                    sipModel.getFeedback().alert(answer + " doesn't exist");
                }
                else if (!directory.isDirectory()) {
                    sipModel.getFeedback().alert(answer + " is not a directory");
                }
                else if (!sipModel.getDataSetModel().isEmpty()) {
                    File outputDirectory = new File(directory, sipModel.getDataSetModel().getDataSet().getSpec());
                    FileUtils.deleteQuietly(outputDirectory);
                    outputDirectory.mkdirs();
                    try {
                        for (File file : sipModel.getDataSetModel().getDataSet().getUploadFiles()) {
                            File targetFile = new File(outputDirectory, Hasher.extractFileName(file));
                            FileUtils.copyFile(file, targetFile);
                        }
                        sipModel.getFeedback().alert("Look in "+outputDirectory);
                    }
                    catch (Exception e) {
                        sipModel.getFeedback().alert("Unable to copy upload files to " + outputDirectory, e);
                    }
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
                        sipModel.getFeedback().alert("Cannot refresh the mapping", e);
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



}
