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
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.prefs.Preferences;

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
        Preferences prefs = sipModel.getPreferences();
        String narthexUrl = prefs.get(Storage.NARTHEX_URL, "");
        String narthexEmail = prefs.get(Storage.NARTHEX_EMAIL, "");
        String narthexApiKey = prefs.get(Storage.NARTHEX_API_KEY, "");
        JTextField urlField = new JTextField(narthexUrl, 45);
        JTextField emailField = new JTextField(narthexEmail);
        JTextField apiKeyField = new JTextField(narthexApiKey);
        if (!sipModel.getFeedback().form(
                "Narthex details",
                "Server", urlField,
                "EMail", emailField,
                "API Key", apiKeyField
        )) return;
        if (!StringUtils.isEmpty(urlField.getText())) {
            try {
                new URL(urlField.getText());
                narthexUrl = urlField.getText().trim();
                narthexEmail = emailField.getText().trim();
                narthexApiKey = apiKeyField.getText().trim();
                prefs.put(Storage.NARTHEX_URL, narthexUrl);
                prefs.put(Storage.NARTHEX_EMAIL, narthexEmail);
                prefs.put(Storage.NARTHEX_API_KEY, narthexApiKey);
                initiateUpload(narthexUrl, narthexEmail, narthexApiKey);
            }
            catch (MalformedURLException e) {
                sipModel.getFeedback().alert("Malformed URL: " + urlField);
            }
        }

    }

    private void initiateUpload(String url, String email, String apiKey) {
        try {
            networkClient.uploadFiles(sipModel.getDataSetModel().getDataSet(), url, email, apiKey, new Swing() {
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
