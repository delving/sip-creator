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
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Download a data set from the culture hub
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DownloadAction extends AbstractAction implements CultureHubClient.ListReceiver {
    private Font font = new Font("Sans", Font.BOLD, 18);
    private JFrame parent;
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;
    private JDialog dialog;
    private Download download = new Download();
    private Cancel cancel = new Cancel();
    private DataSetListModel listModel = new DataSetListModel();
    private JList list = new JList(listModel);

    public DownloadAction(JFrame parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Download");
        this.parent = parent;
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
        dialog = new JDialog(parent, "Download", false);
        createDialog(dialog.getContentPane());
        list.setCellRenderer(new DataSetCellRenderer(sipModel.getStorage().getUsername()));
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                Entry entry = (Entry) list.getSelectedValue();
                download.setEnabled(entry != null && entry.isDownloadable());
            }
        });
        dialog.setSize(500, 200);
        Dimension world = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation((world.width - dialog.getSize().width) / 2, (world.height - dialog.getSize().height) / 2);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        dialog.setVisible(true);
        if (listModel.getSize() == 0) {
            listModel.setLoading();
            cultureHubClient.fetchDataSetList(this);
        }
    }

    @Override
    public void listReceived(final List<CultureHubClient.DataSetEntry> entries) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                listModel.setEntries(entries);
            }
        });
    }

    private void createDialog(Container content) {
        content.add(createListPanel(), BorderLayout.CENTER);
        content.add(createButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel createListPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Data sets"));
        p.add(FrameBase.scroll(list), BorderLayout.CENTER);
        return p;
    }

    private JPanel createButtonPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(new JButton(cancel));
        p.add(new JButton(download));
        return p;
    }

    private class Download extends AbstractAction {

        private Download() {
            super("Download");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            Entry entry = (Entry) list.getSelectedValue();
            String message = String.format("<html><h3>Downloading the data of '%s' from the culture hub</h3>.", entry.getSpec());
            ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(parent),
                    "<html><h2>Download</h2>",
                    message,
                    0, 100
            );
            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(entry.getSpec());
                cultureHubClient.downloadDataSet(dataSet, new ProgressAdapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(true);
                        dialog.setVisible(false);
                    }
                });
            }
            catch (StorageException e) {
                sipModel.getUserNotifier().tellUser("Unable to create data set called " + entry.getSpec(), e);
            }
        }
    }

    private class Cancel extends AbstractAction {

        private Cancel() {
            super("Cancel");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            dialog.setVisible(false);
        }
    }

    private class Entry {
        CultureHubClient.DataSetEntry dataSetEntry;
        DataSet dataSet;

        private Entry(CultureHubClient.DataSetEntry dataSetEntry, DataSet dataSet) {
            this.dataSetEntry = dataSetEntry;
            this.dataSet = dataSet;
        }

        public String getSpec() {
            return dataSetEntry.spec;
        }

        public boolean isDownloadable() {
            return dataSet == null && dataSetEntry.lockedBy == null;
        }
    }

    private class DataSetListModel extends AbstractListModel {
        private boolean loading;
        private List<Entry> entries = new ArrayList<Entry>();

        public void setLoading() {
            int size = getSize();
            entries.clear();
            fireIntervalRemoved(this, 0, size);
            loading = true;
            fireIntervalAdded(this, 0, getSize());
        }

        public void setEntries(List<CultureHubClient.DataSetEntry> entries) {
            loading = false;
            Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets();
            int size = getSize();
            this.entries.clear();
            fireIntervalRemoved(this, 0, size);
            if (entries != null) {
                for (CultureHubClient.DataSetEntry incoming : entries) {
                    this.entries.add(new Entry(incoming, dataSets.get(incoming.spec)));
                }
            }
            fireIntervalAdded(this, 0, getSize());
        }

        @Override
        public int getSize() {
            return loading ? 1 : entries.size();
        }

        @Override
        public Object getElementAt(int i) {
            return loading ? "Loading... please wait" : entries.get(i);
        }
    }

    private class DataSetCellRenderer extends DefaultListCellRenderer {
        private String localUser;

        private DataSetCellRenderer(String localUser) {
            this.localUser = localUser;
        }

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            String string;
            if (value instanceof String) {
                string = (String) value;
            }
            else {
                Entry entry = (Entry) value;
                String message;
                CultureHubClient.LockedBy lockedBy = entry.dataSetEntry.lockedBy;
                if (entry.dataSet != null) {
                    message = "Already in workspace";
                }
                else if (lockedBy != null) {
                    if (localUser.equals(lockedBy.username)) {
                        message = "Locked by yourself, downloaded elsewhere";
                    }
                    else {
                        message = String.format("Owned by '%s' <%s>", lockedBy.username, lockedBy.email);
                    }
                }
                else {
                    message = "Download";
                }
                string = String.format("<html><font size=+1><b>%s</b> - </font><i>%s</i>", entry.getSpec(), message);
            }
            return super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
        }
    }
}
