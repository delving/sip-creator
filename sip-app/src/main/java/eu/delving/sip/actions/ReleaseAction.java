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

package eu.delving.sip.actions;

import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Delete local copy and unlock on the hub
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReleaseAction extends AbstractAction {
    private JDesktopPane parent;
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;

    public ReleaseAction(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Release this data set for others to access");
        putValue(Action.SMALL_ICON, SwingHelper.RELEASE_ICON);
        putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        this.parent = parent;
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        final DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        boolean unlock = sipModel.getFeedback().confirm(
                "Release",
                String.format("<html>Are you sure that you want to delete your local copy of<br>" +
                        "this dataset %s, and unlock it so that someone else can access it?",
                        dataSet.getSpec()
                )
        );
        if (unlock) unlockDataSet(dataSet);
    }

    private void unlockDataSet(final DataSet dataSet) {
        cultureHubClient.unlockDataSet(dataSet, new CultureHubClient.UnlockListener() {
            @Override
            public void unlockComplete(boolean successful) {
                if (successful) {
                    sipModel.getFeedback().say(String.format("Unlocked %s and removed it locally", dataSet));
                    try {
                        Exec.run(new Swing() {
                            @Override
                            public void run() {
                                sipModel.seekReset(); // release the file handle
                            }
                        });
                        sipModel.getDataSetModel().clearDataSet();
                        dataSet.remove();
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
