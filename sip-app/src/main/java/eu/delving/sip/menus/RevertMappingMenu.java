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

import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.MappingSaveTimer;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * The menu for choosing to revert to a previous mapping.
 *
 * @author Gerald de Jong, Beautiful Code BV, <gerald@delving.eu>
 */

public class RevertMappingMenu extends JMenu implements MappingSaveTimer.ListReceiver {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private SipModel sipModel;

    public RevertMappingMenu(SipModel sipModel) {
        super("Revert to previous version");
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
            boolean revert = sipModel.getFeedback().confirm(
                    "Revert",
                    String.format("Are you sure you want to revert to %s", lastModifiedString())
            );
            if (revert) {
                sipModel.exec(new Work() {
                    @Override
                    public void run() {
                        try {
                            RecMapping previousMapping = sipModel.getDataSetModel().getDataSet().revertRecMapping(file, sipModel.getDataSetModel());
                            sipModel.getMappingModel().setRecMapping(previousMapping);
                        }
                        catch (StorageException e) {
                            sipModel.getFeedback().alert("Unable to revert mapping", e);
                        }
                    }
                });
            }
        }

        private String lastModifiedString() {
            return DATE_FORMAT.format(new Date(file.lastModified()));
        }
    }
}
