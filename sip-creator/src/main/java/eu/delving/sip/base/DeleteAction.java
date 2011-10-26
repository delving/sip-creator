/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JDesktopPane;
import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;

/**
 * Delete local copy and unlock on the hub
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DeleteAction extends AbstractAction {
    private JDesktopPane parent;
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;

    public DeleteAction(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Delete local copy and unlock this data set");
        putValue(Action.SMALL_ICON, new ImageIcon(getClass().getResource("/delete-icon.png")));
        this.parent = parent;
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        final DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        int answer = JOptionPane.showConfirmDialog(
                parent,
                String.format("<html>Are you sure that you want to delete your local copy of<br>" +
                        "this dataset %s, and unlock it so that someone else can access it?",
                        dataSet.getSpec()
                )
        );
        if (answer == JOptionPane.OK_OPTION) {
            unlockDataSet(dataSet);
        }
    }

    private void unlockDataSet(final DataSet dataSet) {
        cultureHubClient.unlockDataSet(dataSet, new CultureHubClient.UnlockListener() {
            @Override
            public void unlockComplete(boolean successful) {
                if (successful) {
                    sipModel.getFeedback().say(String.format("Unlocked %s and removed it locally", dataSet));
                    try {
                        dataSet.remove();
                        sipModel.getDataSetModel().setDataSet(null);
                    }
                    catch (StorageException e) {
                        sipModel.getFeedback().alert("Unable to remove data set", e);
                    }
                }
                else {
                    sipModel.getFeedback().alert("Unable to unlock the data set");
                }
            }
        });
    }

}
