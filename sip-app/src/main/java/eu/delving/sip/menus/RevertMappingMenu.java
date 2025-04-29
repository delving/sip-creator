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
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.MappingSaveTimer;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * The menu for choosing to revert to a previous mapping, with the list appearing in chronological order.
 *
 */

public class RevertMappingMenu extends JMenu implements MappingSaveTimer.ListReceiver {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private SipModel sipModel;

    public RevertMappingMenu(SipModel sipModel) {
        super("Revert");
        this.sipModel = sipModel;
    }

    @Override
    public void mappingFileList(List<File> mappingFiles) {
        removeAll();
        for (File file : mappingFiles) add(new RevertAction(file));
    }

    private class RevertAction extends AbstractAction {
        private final File file;

        private RevertAction(File file) {
            this.file = file;
            putValue(Action.NAME, lastModifiedString());
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            // todo: make sure there is a dataset and a prefix
            boolean revert = sipModel.getFeedback().confirm(
                    "Revert",
                    String.format("Are you sure you want to revert to %s", lastModifiedString())
            );
            if (revert) {
                sipModel.exec(new MappingReverter(file, sipModel.getDataSetModel().getDataSet(), sipModel.getMappingModel().getPrefix()));
            }
        }

        private String lastModifiedString() {
            return DATE_FORMAT.format(new Date(file.lastModified()));
        }
    }

    private class MappingReverter implements Work.DataSetPrefixWork {
        private File file;
        private DataSet dataSet;
        private String prefix;

        private MappingReverter(File file, DataSet dataSet, String prefix) {
            this.file = file;
            this.dataSet = dataSet;
            this.prefix = prefix;
        }

        @Override
        public void run() {
            try {
                RecMapping previousMapping = sipModel.getDataSetModel().getDataSet().revertRecMapping(file, sipModel.getDataSetModel());
                sipModel.getMappingModel().setRecMapping(previousMapping);

                // Trigger a reload of the dataset in order to refresh source tree mappings
                sipModel.setDataSet(sipModel.getDataSetModel().getDataSet(), () -> {});
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to revert mapping", e);
            }
        }

        @Override
        public Job getJob() {
            return Job.REVERT_MAPPING;
        }

        @Override
        public String getPrefix() {
            return prefix;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }
    }
}
