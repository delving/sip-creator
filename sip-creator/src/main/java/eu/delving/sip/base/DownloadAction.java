/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fetch a data set from the culture hub
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DownloadAction extends AbstractAction implements CultureHubClient.ListReceiveListener {
    private final static Logger LOG = Logger.getRootLogger();
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;
    private JDialog dialog;
    private FetchAction fetch = new FetchAction();
    private Cancel cancel = new Cancel();
    private DataSetListModel listModel = new DataSetListModel();
    private JList list = new JList(listModel);

    public DownloadAction(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Download another data set");
        putValue(Action.SMALL_ICON, new ImageIcon(getClass().getResource("/download-icon.png")));
        putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
        dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(parent), "Download", false);
        createDialog(dialog.getContentPane());
        list.setCellRenderer(new DataSetCellRenderer(sipModel.getStorage().getUsername()));
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fetch.setEnabled(false);
        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                Object entryObject = list.getSelectedValue();
                if (entryObject != null && entryObject instanceof Entry) {
                    Entry entry = (Entry) list.getSelectedValue();
                    fetch.setEnabled(entry != null && entry.isDownloadable());
                }
                else {
                    fetch.setEnabled(false);
                }
            }
        });
        dialog.setSize(500, 600);
        Dimension world = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setLocation((world.width - dialog.getSize().width) / 2, (world.height - dialog.getSize().height) / 2);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        dialog.setVisible(true);
        listModel.setMessage("Loading list of data sets... just a moment");
        cultureHubClient.fetchDataSetList(this);
    }

    @Override
    public void listReceived(final List<CultureHubClient.DataSetEntry> entries) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                if (entries.isEmpty()) {
                    listModel.setMessage("No data sets available yet. Create them on the Culture Hub first.");
                }
                else {
                    listModel.setEntries(entries);
                }
            }
        });
    }

    @Override
    public void failed(Exception e) {
        LOG.warn("Fetching list failed", e);
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                listModel.setMessage("Failed to load list");
                dialog.setVisible(false);
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
        final JButton cancelButton = new JButton(cancel);
        JButton fetchButton = new JButton(fetch);
        p.add(cancelButton);
        p.add(fetchButton);
        fetchButton.registerKeyboardAction(
                fetchButton.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false)),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false),
                JComponent.WHEN_FOCUSED
        );
        fetchButton.registerKeyboardAction(
                fetchButton.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true)),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true),
                JComponent.WHEN_FOCUSED
        );
        dialog.getRootPane().setDefaultButton(fetchButton);
        return p;
    }

    private class FetchAction extends AbstractAction {

        private FetchAction() {
            super("Fetch data set");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Entry entry = (Entry) list.getSelectedValue();
            if (entry == null) return;
            setEnabled(false);
            String initialMessage = String.format("<html><h3>Culture hub is preparing '%s' for download.</h3>.", entry.getSpec());
            String message = String.format("<html><h3>Downloading the data of '%s' from the culture hub</h3>.", entry.getSpec());
            ProgressListener listener = sipModel.getFeedback().progressListener("Download", initialMessage, message);
            listener.onFinished(new ProgressListener.End() {
                @Override
                public void finished(boolean success) {
                    setEnabled(true);
                    dialog.setVisible(false);
                }
            });
            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(entry.getSpec(), entry.getOrganization());
                cultureHubClient.downloadDataSet(dataSet, listener);
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create data set called " + entry.getSpec(), e);
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

    private class Entry implements Comparable<Entry> {
        CultureHubClient.DataSetEntry dataSetEntry;
        DataSet dataSet;

        private Entry(CultureHubClient.DataSetEntry dataSetEntry, DataSet dataSet) {
            this.dataSetEntry = dataSetEntry;
            this.dataSet = dataSet;
        }

        public String getOrganization() {
            return dataSetEntry.orgId;
        }

        public String getSpec() {
            return dataSetEntry.spec;
        }

        public boolean isDownloadable() {
            return dataSet == null && dataSetEntry.lockedBy == null;
        }

        @Override
        public int compareTo(Entry entry) {
            return dataSetEntry.spec.compareTo(entry.dataSetEntry.spec);
        }

        public String toString() {
            return dataSetEntry.spec;
        }
    }

    private class DataSetListModel extends AbstractListModel {
        private String message;
        private List<Entry> entries = new ArrayList<Entry>();

        public void setMessage(String message) {
            int size = getSize();
            entries.clear();
            fireIntervalRemoved(this, 0, size);
            this.message = message;
            fireIntervalAdded(this, 0, getSize());
        }

        public void setEntries(List<CultureHubClient.DataSetEntry> entries) {
            message = null;
            Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets();
            int size = getSize();
            this.entries.clear();
            fireIntervalRemoved(this, 0, size);
            if (entries != null) {
                for (CultureHubClient.DataSetEntry incoming : entries) {
                    this.entries.add(new Entry(incoming, dataSets.get(incoming.spec)));
                }
            }
            Collections.sort(this.entries);
            fireIntervalAdded(this, 0, getSize());
        }

        @Override
        public int getSize() {
            return message != null ? 1 : entries.size();
        }

        @Override
        public Object getElementAt(int i) {
            return message != null ? message : entries.get(i);
        }

        public boolean hasEntries() {
            return !entries.isEmpty();
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
