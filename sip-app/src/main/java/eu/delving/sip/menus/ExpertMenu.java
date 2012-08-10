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

import eu.delving.sip.actions.MediaIngestAction;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;
import eu.delving.sip.xml.SourceConverter;
import eu.delving.stats.Stats;

import javax.swing.*;
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

    public ExpertMenu(JDesktopPane desktop, final SipModel sipModel) {
        super("Expert");
        this.sipModel = sipModel;
        add(new MaxUniqueValueLengthAction());
        add(new UniqueConverterAction());
        add(new WriteOutputAction());
        add(new MediaIngestAction(desktop, sipModel));
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
}
