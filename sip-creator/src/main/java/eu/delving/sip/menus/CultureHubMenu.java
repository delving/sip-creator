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
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.ProgressAdapter;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDesktopPane;
import javax.swing.JMenu;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;

/**
 * The menu for interfacing with the culture hub
 *
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

public class CultureHubMenu extends JMenu implements CultureHubClient.ListReceiver {
    private static String FETCH = "Fetch Data Set List";
    private static String FETCHING = "Fetching.. Please wait";
    private JDesktopPane parent;
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;
    private JMenu dataSetMenu = new JMenu("Download Data Set");
    private FetchDataSetListAction fetchDataSetListAction = new FetchDataSetListAction();

    public CultureHubMenu(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Culture Hub");
        this.parent = parent;
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
        dataSetMenu.setEnabled(false);
        add(fetchDataSetListAction);
        addSeparator();
        add(dataSetMenu);
        addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent menuEvent) {
                if (dataSetMenu.getItemCount() == 0) {
                    fetchDataSetListAction.go();
                }
            }

            @Override
            public void menuDeselected(MenuEvent menuEvent) {
            }

            @Override
            public void menuCanceled(MenuEvent menuEvent) {
            }
        });
    }

    @Override
    public void listReceived(final List<CultureHubClient.DataSetEntry> entries) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                fetchDataSetListAction.wakeUp();
                Map<String, FileStore.DataSetStore> stores = sipModel.getFileStore().getDataSetStores();
                dataSetMenu.removeAll();
                if (entries != null) {
                    for (CultureHubClient.DataSetEntry entry : entries) {
                        dataSetMenu.add(new DownloadDatasetAction(entry, stores.get(entry.spec)));
                    }
                }
                dataSetMenu.setEnabled(true);
            }
        });
    }

    private class FetchDataSetListAction extends AbstractAction {

        private FetchDataSetListAction() {
            super(FETCH);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            go();
        }

        public void go() {
            putValue(Action.NAME, FETCHING);
            setEnabled(false);
            cultureHubClient.fetchDataSetList(CultureHubMenu.this);
        }

        public void wakeUp() {
            putValue(Action.NAME, FETCH);
            setEnabled(true);
        }
    }

    private class DownloadDatasetAction extends AbstractAction {
        private CultureHubClient.DataSetEntry entry;
        private FileStore.DataSetStore store;

        private DownloadDatasetAction(CultureHubClient.DataSetEntry entry, FileStore.DataSetStore store) {
            this.entry = entry;
            this.store = store;
            if (store == null) {
                String localUser = sipModel.getFileStore().getUsername();
                if (entry.lockedBy != null) {
                    if (localUser.equals(entry.lockedBy.username)) {
                        setName("Locked by yourself, downloaded elsewhere", false);
                    }
                    else {
                        setName(String.format("Owned by '%s' <%s>", entry.lockedBy.username, entry.lockedBy.email), false);
                    }
                }
                else {
                    setName("Download", true);
                }
            }
            else {
                setName("Already in workspace", false);
            }
        }

        private void setName(String message, boolean enabled) {
            putValue(Action.NAME, String.format("<html><font size=+1><b>%s</b> - </font><i>%s</i>", entry.spec, message));
            setEnabled(enabled);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            String message = String.format("<html><h3>Downloading the data of '%s' from the culture hub</h3>.", entry.spec);
            ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(parent),
                    "<html><h2>Download</h2>",
                    message,
                    0, 100
            );
            try {
                FileStore.DataSetStore store = sipModel.getFileStore().createDataSetStore(entry.spec);
                cultureHubClient.downloadDataSet(store, new ProgressAdapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(true);
                    }
                });
            }
            catch (FileStoreException e) {
                sipModel.getUserNotifier().tellUser("Unable to create file store called "+entry.spec, e);
            }
        }
    }
}
