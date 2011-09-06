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

package eu.delving.sip.menus;

import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.JDesktopPane;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;

/**
 * The menu for interfacing with the culture hub
 *
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

public class CultureHubMenu extends JMenu {
    private JDesktopPane parent;
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;
    private JMenu dataSetMenu = new JMenu("Download Data Set");

    public CultureHubMenu(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Culture Hub");
        this.parent = parent;
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
        dataSetMenu.setEnabled(false);
        add(new FetchDataSetListAction());
        addSeparator();
        add(dataSetMenu);
    }

    private class FetchDataSetListAction extends AbstractAction {

        private FetchDataSetListAction() {
            super("Fetch Data Set List");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            // todo: fetch the list
            // todo: refresh dataSetMenu with the values returned
            // todo:  ..MINUS the ones we already have!
            dataSetMenu.removeAll();
            for (int walk=0; walk<3; walk++) {
                dataSetMenu.add(new DownloadDatasetAction(String.valueOf((int)(Math.random()*10000))));
            }
        }
    }

    private class DownloadDatasetAction extends AbstractAction {
        private String spec;

        private DownloadDatasetAction(String spec) {
            super(String.format("Download \"%s\"", spec));
            this.spec = spec;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            String message = String.format("<html><h3>Downloading the data of '%s' from the culture hub</h3>just kidding!",spec);
            JOptionPane.showInternalMessageDialog(parent, message);
//            ProgressMonitor progressMonitor = new ProgressMonitor(
//                    SwingUtilities.getRoot(parent),
//                    "<html><h2>Normalizing</h2>",
//                    message,
//                    0, 100
//            );
//            try {
//                cultureHubClient.uploadFiles(store, new ProgressAdapter(progressMonitor) {
//                    @Override
//                    public void swingFinished(boolean success) {
//                        setEnabled(true);
//                    }
//                });
//            }
//            catch (FileStoreException e) {
//                JOptionPane.showInternalMessageDialog(parent, "<html>Problem uploading files<br>"+e.getMessage());
//            }
//            finally {
//                setEnabled(true);
//            }
        }
    }
}
