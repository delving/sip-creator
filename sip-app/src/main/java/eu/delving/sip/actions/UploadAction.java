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

import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.Preferences;

import static eu.delving.sip.files.Storage.NARTHEX_API_KEY;
import static eu.delving.sip.files.Storage.NARTHEX_DATASET_NAME;
import static eu.delving.sip.files.Storage.NARTHEX_PREFIX;
import static eu.delving.sip.files.Storage.NARTHEX_URL;

/**
 * Upload what is necessary to the culture hub
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class UploadAction extends AbstractAction {
    private SipModel sipModel;
    private NetworkClient networkClient;

    public UploadAction(SipModel sipModel, NetworkClient networkClient) {
        super("Upload");
        putValue(Action.SMALL_ICON, SwingHelper.ICON_UPLOAD);
        this.sipModel = sipModel;
        this.networkClient = networkClient;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        setEnabled(false);
        if (sipModel.getDataSetModel().isEmpty()) return;
        Preferences preferences = sipModel.getPreferences();
        DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        Map<String, String> hints = dataSet.getHints();
        String narthexDatasetName = hints.get(NARTHEX_DATASET_NAME);
        if (narthexDatasetName == null) narthexDatasetName = "";
        String narthexPrefix = hints.get(NARTHEX_PREFIX);
        if (narthexPrefix == null) narthexPrefix = "";
        Map<String,String> fields = new TreeMap<String, String>();
        fields.put(NARTHEX_URL, preferences.get(NARTHEX_URL, ""));
        fields.put(NARTHEX_API_KEY, preferences.get(NARTHEX_API_KEY, ""));
        fields.put(NARTHEX_DATASET_NAME, narthexDatasetName);
        fields.put(NARTHEX_PREFIX, narthexPrefix);
        boolean narthexUploadInfo = sipModel.getFeedback().getNarthexCredentials(fields);
        if (narthexUploadInfo) {
            preferences.put(NARTHEX_URL, fields.get(NARTHEX_URL));
            preferences.put(NARTHEX_API_KEY, fields.get(NARTHEX_API_KEY));
            hints.put(NARTHEX_DATASET_NAME, fields.get(NARTHEX_DATASET_NAME));
            hints.put(NARTHEX_PREFIX, fields.get(NARTHEX_PREFIX));
            try {
                dataSet.setHints(hints);
                initiateUpload(
                        fields.get(NARTHEX_URL),
                        fields.get(NARTHEX_API_KEY),
                        fields.get(NARTHEX_DATASET_NAME),
                        fields.get(NARTHEX_PREFIX)
                );
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to set dataset hints", e);
            }
        }
    }

    private void initiateUpload(String url, String apiKey, String datasetName, String prefix) {
        try {
            networkClient.uploadNarthex(sipModel.getDataSetModel().getDataSet(), url, apiKey, datasetName, prefix, new Swing() {
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
}
