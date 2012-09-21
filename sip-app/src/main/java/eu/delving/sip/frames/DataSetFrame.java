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

import eu.delving.schema.SchemaVersion;
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
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 16);
    private DataSetListModel listModel = new DataSetListModel();
    private JList dataSetList;
    private CultureHubClient cultureHubClient;
    private EditAction editAction = new EditAction();
    private DownloadAction downloadAction = new DownloadAction();
    private DataSetFrame.RefreshAction refreshAction = new RefreshAction();

    public DataSetFrame(final SipModel sipModel, CultureHubClient cultureHubClient) {
        super(Which.DATA_SET, sipModel, "Data Sets");
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
        listModel.setStateCheckDelay(500);
        getAction().putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_H, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        editAction.checkEnabled();
        downloadAction.checkEnabled();
    }

    @Override
    protected void buildContent(Container content) {
        content.add(new JButton(refreshAction), BorderLayout.NORTH);
        content.add(SwingHelper.scrollV("Data Sets", dataSetList), BorderLayout.CENTER);
        JPanel bp = new JPanel(new GridLayout(0, 1));
        bp.add(new JButton(editAction));
        bp.add(new JButton(downloadAction));
        content.add(bp, BorderLayout.SOUTH);
    }

    public void fireRefresh() {
        refreshAction.actionPerformed(null);
    }

    private class RefreshAction extends AbstractAction {

        private RefreshAction() {
            super("Refresh this list");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            listModel.setMessage("Loading");
            cultureHubClient.fetchDataSetList(new CultureHubClient.ListReceiveListener() {
                @Override
                public void listReceived(List<CultureHubClient.DataSetEntry> entries) {
                    listModel.setEntries(entries);
                    setEnabled(true);
                }

                @Override
                public void failed(Exception e) {
                    sipModel.getFeedback().alert("Unable to fetch data set list", e);
                    setEnabled(true);
                }
            });
        }
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
            List<SchemaVersion> schemaVersions = entry.getSchemaVersions();
            if (schemaVersions == null || schemaVersions.isEmpty()) return;
            setEnabled(false);
            String prefix;
            if (schemaVersions.size() == 1) {
                prefix = schemaVersions.get(0).getPrefix();
            }
            else {
                prefix = askForPrefix(schemaVersions);
                if (prefix == null) return;
            }
            sipModel.setDataSetPrefix(entry.dataSet, prefix, new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                    sipModel.getViewSelector().selectView(AllFrames.View.QUICK_MAPPING);
                }
            });
        }

        private String askForPrefix(List<SchemaVersion> schemaVersions) {
            JPanel buttonPanel = new JPanel(new GridLayout(0, 1));
            ButtonGroup buttonGroup = new ButtonGroup();
            for (SchemaVersion schemaVersion : schemaVersions) {
                JRadioButton b = new JRadioButton(schemaVersion.getPrefix());
                if (buttonGroup.getButtonCount() == 0) b.setSelected(true);
                b.setActionCommand(schemaVersion.getPrefix());
                buttonGroup.add(b);
                buttonPanel.add(b);
            }
            return sipModel.getFeedback().form("Choose Schema", buttonPanel) ? buttonGroup.getSelection().getActionCommand() : null;
        }
    }

    private class DownloadAction extends AbstractAction {

        private DownloadAction() {
            super("Download");
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
        return (Entry) dataSetList.getSelectedValue();
    }

    private class Entry implements Comparable<Entry> {
        private CultureHubClient.DataSetEntry dataSetEntry;
        private DataSet dataSet;
        private State previousState;

        private Entry(CultureHubClient.DataSetEntry dataSetEntry, DataSet dataSet) {
            this.dataSetEntry = dataSetEntry;
            this.dataSet = dataSet;
            this.previousState = getState();
        }

        public String getOrganization() {
            return dataSetEntry != null ? dataSetEntry.orgId : dataSet.getOrganization();
        }

        public String getSpec() {
            return dataSetEntry != null ? dataSetEntry.spec : dataSet.getSpec();
        }

        public List<SchemaVersion> getSchemaVersions() {
            return dataSet != null ? dataSet.getSchemaVersions() : null;
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
            return getSpec().compareTo(entry.getSpec());
        }

        public boolean hasStateChanged() {
            State newState = getState();
            if (newState == previousState) return false;
            previousState = newState;
            return true;
        }

        public String toString() {
            return String.format("DataSet(%s)", getSpec());
        }

        private boolean isBusy() {
            return sipModel.getWorkModel().isDataSetBusy(getSpec());
        }

        private boolean isLockedByUser() {
            boolean absent = dataSetEntry == null || dataSetEntry.lockedBy == null;
            return !absent && sipModel.getStorage().getUsername().equals(dataSetEntry.lockedBy.username);
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

        public void setEntries(List<CultureHubClient.DataSetEntry> entries) {
            message = null;
            List<Entry> freshEntries = new ArrayList<Entry>();
            Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets();
            for (CultureHubClient.DataSetEntry incoming : entries) {
                DataSet dataSet = dataSets.get(incoming.getDirectoryName());
                freshEntries.add(new Entry(incoming, dataSet));
                if (dataSet != null) dataSets.remove(incoming.getDirectoryName()); // remove used ones
            }
            for (DataSet dataSet : dataSets.values()) { // remaining ones
                freshEntries.add(new Entry(null, dataSet));
            }
            Collections.sort(freshEntries);
            int size = getSize();
            this.entries = new ArrayList<Entry>();
            fireIntervalRemoved(this, 0, size);
            this.entries = freshEntries;
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
                string = String.format("%s - %s", entry.getSpec(), state.string);
                int substitution = string.indexOf("%s");
                if (substitution > 0) {
                    string = String.format(string, entry.dataSetEntry.lockedBy.email);
                }
            }
            JComponent component = (JComponent) super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
            component.setForeground(isSelected ? Color.WHITE : foreground);
            return component;
        }
    }

    private enum State {
        LOCKED(true, false, "locked by you", new Color(0, 160, 0)),
        AVAILABLE(false, true, "can be downloaded", new Color(200, 0, 200)),
        UNAVAILABLE(false, false, "locked by %s", new Color(128, 128, 128)),
        BUSY(false, false, "busy locally", new Color(60, 90, 240)),
        ORPHAN_TAKEN(true, false, "locked by %s but present locally (unusual), cannot be downloaded", Color.RED),
        ORPHAN_LONELY(true, false, "only present locally (unusual)", Color.RED),
        ORPHAN_UPDATE(false, true, "owned by you, but not present locally (unusual), can be downloaded", Color.RED),
        ORPHAN_ARCHIVE(true, true, "not locked but present locally (unusual), can archive and download", Color.RED);

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
