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

import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Upload what is necessary to the culture hub
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class UploadAction extends AbstractAction {
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;

    public UploadAction(SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Upload");
        putValue(Action.SMALL_ICON, SwingHelper.ICON_UPLOAD);
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        setEnabled(false);
        DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        final InvalidPrefixesFetcher fetcher = new InvalidPrefixesFetcher(dataSet);
        fetcher.swing = new Swing() {
            @Override
            public void run() {
                if (fetcher.invalidPrefixes.isEmpty()) {
                    try {
                        cultureHubClient.uploadFiles(sipModel.getDataSetModel().getDataSet(), new Swing() {
                            @Override
                            public void run() {
                                setEnabled(true);
                            }
                        });
                    }
                    catch (final StorageException e) {
                        sipModel.getFeedback().alert("Unable to complete uploading", e);
                        setEnabled(true);
                    }
                }
                else {
                    sipModel.getFeedback().alert(String.format("Upload not permitted until all mappings are validated. Still missing: %s.", fetcher.invalidPrefixes));
                    setEnabled(true);
                }
            }
        };
        sipModel.exec(fetcher);
    }

    private class InvalidPrefixesFetcher implements Work.DataSetWork {
        private DataSet dataSet;
        private Swing swing;
        private List<String> invalidPrefixes;

        private InvalidPrefixesFetcher(DataSet dataSet) {
            this.dataSet = dataSet;
        }

        @Override
        public void run() {
            try {
                invalidPrefixes = sipModel.getDataSetModel().getInvalidPrefixes();
                sipModel.exec(swing);
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to fetch invalid prefixes", e);
            }
        }

        @Override
        public Job getJob() {
            return Job.FIND_INVALID_PREFIXES;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }
    }

}
