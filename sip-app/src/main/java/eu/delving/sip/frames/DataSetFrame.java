/*
 * Copyright 2012 Delving BV
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

package eu.delving.sip.frames;

import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Show the datasets both local and on the server, so all info about their status is unambiguous.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DataSetFrame extends FrameBase {
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 12);
    private DataSetListModel listModel = new DataSetListModel();
    private JList dataSetList;
    private CultureHubClient cultureHubClient;
    private EditAction editAction = new EditAction();
    private DownloadAction downloadAction = new DownloadAction();

    public DataSetFrame(JDesktopPane desktop, final SipModel sipModel, CultureHubClient cultureHubClient) {
        super(Which.DATA_SET, desktop, sipModel, "Data Sets");
        this.cultureHubClient = cultureHubClient;
        this.dataSetList = new JList(listModel);
        this.dataSetList.setFont(MONOSPACED);
        this.dataSetList.setCellRenderer(new DataSetCellRenderer());
        this.dataSetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.dataSetList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                downloadAction.checkEnabled();
                editAction.checkEnabled();
            }
        });
        int busyCheckDelay = 500;
        listModel.setStateCheckDelay(busyCheckDelay);
        getAction().putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_H, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
    }

    @Override
    protected void buildContent(Container content) {
        content.add(SwingHelper.scrollV("Data Sets", dataSetList), BorderLayout.CENTER);
        content.add(new JButton(downloadAction), BorderLayout.SOUTH);
    }

    private class EditAction extends AbstractAction {

        private EditAction() {
            super("Select for editing");
        }

        public void checkEnabled() {
            Entry entry = getSelectedEntry();
            this.setEnabled(entry != null && entry.getState().selectable);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Entry entry = getSelectedEntry();
            if (entry == null) return;
            setEnabled(false);
            String prefix = "prefix"; // todo: pop up to ask for prefix
            sipModel.setDataSetPrefix(entry.dataSet, prefix, new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                }
            });

            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(entry.getSpec(), entry.getOrganization());
                cultureHubClient.downloadDataSet(dataSet, new Swing() {
                    @Override
                    public void run() {
                        setEnabled(true);
                    }
                });
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create data set called " + entry.getSpec(), e);
            }
        }
    }

    private class DownloadAction extends AbstractAction {

        private DownloadAction() {
            super("Download selected data set");
        }

        public void checkEnabled() {
            Entry entry = getSelectedEntry();
            this.setEnabled(entry != null && entry.getState().downloadable);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Entry entry = getSelectedEntry();
            if (entry == null) return;
            setEnabled(false);
            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(entry.getSpec(), entry.getOrganization());
                cultureHubClient.downloadDataSet(dataSet, new Swing() {
                    @Override
                    public void run() {
                        setEnabled(true);
                    }
                });
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create data set called " + entry.getSpec(), e);
            }
        }

    }

    private Entry getSelectedEntry() {
        Entry entry = (Entry) dataSetList.getSelectedValue();
        if (entry == null) return null;
        if (!entry.getState().downloadable) return null;
        return entry;
    }

    private class Entry implements Comparable<Entry> {
        private CultureHubClient.DataSetEntry dataSetEntry;
        private DataSet dataSet;
        private String prefix;
        private State previousState;

        private Entry(CultureHubClient.DataSetEntry dataSetEntry, DataSet dataSet, String prefix) {
            this.dataSetEntry = dataSetEntry;
            this.dataSet = dataSet;
            this.prefix = prefix;
            this.previousState = getState();
        }

        public String getOrganization() {
            return dataSetEntry.orgId;
        }

        public String getSpec() {
            return dataSetEntry.spec;
        }

        public State getState() {
            if (isBusy()) {
                return State.BUSY;
            }
            else if (dataSetEntry != null && dataSet != null) {
                if (dataSetEntry.lockedBy == null) {
                    return State.ORPHAN_ARCHIVE;
                }
                else if (isLockedByUser()) {
                    return State.LOCKED;
                }
                else { // locked by somebody else
                    return State.ORPHAN_TAKEN;
                }
            }
            else if (dataSetEntry != null) { // dataSet is null
                if (dataSetEntry.lockedBy == null) {
                    return State.AVAILABLE;
                }
                else if (isLockedByUser()) {
                    return State.ORPHAN_UPDATE;
                }
                else { // locked by somebody else
                    return State.UNAVAILABLE;
                }
            }
            else { // dataSetEntry is null
                return State.ORPHAN_LONELY;
            }
        }

        @Override
        public int compareTo(Entry entry) {
            return dataSetEntry.spec.compareTo(entry.dataSetEntry.spec);
        }

        public String toString() {
            return dataSetEntry.spec;
        }

        private boolean isBusy() {
            return sipModel.getWorkModel().isDataSetBusy(getSpec());
        }

        private boolean isLockedByUser() {
            boolean absent = dataSetEntry == null || dataSetEntry.lockedBy == null;
            return !absent && sipModel.getStorage().getUsername().equals(dataSetEntry.lockedBy.username);
        }

        public boolean hasStateChanged() {
            State newState = getState();
            if (newState == previousState) return false;
            previousState = newState;
            return true;
        }
    }

    private class DataSetListModel extends AbstractListModel {
        private String message;
        private List<Entry> entries = new ArrayList<Entry>();

        public void setStateCheckDelay(int delay) { // call only once
            Timer disableTimer = new Timer(delay, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    listModel.checkState();
                }
            });
            disableTimer.setRepeats(true);
            disableTimer.start();
        }

        public void setMessage(String message) {
            int size = getSize();
            entries.clear();
            fireIntervalRemoved(this, 0, size);
            this.message = message;
            fireIntervalAdded(this, 0, getSize());
        }

        public void setEntries(java.util.List<CultureHubClient.DataSetEntry> entries) {
            message = null;
            Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets();
            int size = getSize();
            this.entries.clear();
            fireIntervalRemoved(this, 0, size);
            if (entries != null) {
                for (CultureHubClient.DataSetEntry incoming : entries) {
                    String key = incoming.spec + "_" + incoming.orgId;
                    this.entries.add(new Entry(incoming, dataSets.get(key), "prefix")); // todo: PREFIX!!!
                }
            }
            Collections.sort(this.entries);
            fireIntervalAdded(this, 0, getSize());
        }

        public void checkState() {
            int index = 0;
            for (Entry entry : entries) {
                if (entry.hasStateChanged()) fireContentsChanged(this, index, index);
                index++;
            }
        }

        @Override
        public int getSize() {
            return message != null ? 1 : entries.size();
        }

        @Override
        public Object getElementAt(int i) {
            return message != null ? message : entries.get(i);
        }
    }

    private class DataSetCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Color foreground;
            String string;
            if (value instanceof String) {
                string = (String) value;
                foreground = Color.BLACK;
            }
            else {
                Entry entry = (Entry) value;
                State state = entry.getState();
                foreground = state.color;
                string = state.toString();
                if (string.indexOf("%s") > 0) {
                    string = String.format(string, entry.dataSetEntry.lockedBy.email);
                }
            }
            JComponent component = (JComponent) super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
            component.setForeground(foreground);
            return component;
        }
    }

    private enum State {
        LOCKED(true, false, "locked by you", new Color(0, 180, 0)),
        AVAILABLE(false, true, "can be downloaded", new Color(0, 120, 0)),
        UNAVAILABLE(false, false, "locked by %s", new Color(250, 30, 0)),
        BUSY(false, false, "busy locally", new Color(60, 90, 240)),
        ORPHAN_TAKEN(true, false, "locked by %s but present locally (unusual), cannot be downloaded", new Color(230, 60, 220)),
        ORPHAN_LONELY(true, false, "only present locally (unusual)", new Color(230, 60, 220)),
        ORPHAN_UPDATE(false, true, "owned by you, but not present locally (unusual), can be downloaded", new Color(230, 60, 220)),
        ORPHAN_ARCHIVE(true, true, "not locked but present locally (unusual), can archive and download", new Color(230, 60, 220));

        private final String string;
        private final Color color;
        private final boolean selectable;
        private final boolean downloadable;

        private State(boolean selectable, boolean downloadable, String string, Color color) {
            this.selectable = selectable;
            this.downloadable = downloadable;
            this.string = string;
            this.color = color;
        }

        public String toString() {
            return String.format("%s: %s", super.toString(), string);
        }
    }
}
