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
import eu.delving.sip.panels.HtmlPanel;
import org.antlr.stringtemplate.StringTemplate;

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

import static eu.delving.sip.base.SwingHelper.*;

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
    private ReleaseAction releaseAction = new ReleaseAction();
    private DataSetFrame.RefreshAction refreshAction = new RefreshAction();
    private HtmlPanel htmlPanel = new HtmlPanel("Data Set");

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
                releaseAction.checkEnabled();
                fillHtmlPanel();
            }
        });
        listModel.setStateCheckDelay(500);
        getAction().putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        editAction.checkEnabled();
        downloadAction.checkEnabled();
        releaseAction.checkEnabled();
    }

    @Override
    protected void buildContent(Container content) {
        content.add(SwingHelper.scrollV("Data Sets", dataSetList), BorderLayout.CENTER);
        content.add(createEast(), BorderLayout.EAST);
        fillHtmlPanel();
    }

    private JPanel createEast() {
        JPanel bp = new JPanel(new GridLayout(0, 1, 10, 10));
        bp.add(new JButton(refreshAction));
        bp.add(new JButton(editAction));
        bp.add(new JButton(downloadAction));
        bp.add(new JButton(releaseAction));
        bp.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel p = new JPanel(new BorderLayout(10, 10));
        htmlPanel.setPreferredSize(new Dimension(500, 500));
        p.add(htmlPanel, BorderLayout.CENTER);
        p.add(bp, BorderLayout.SOUTH);
        return p;
    }

    public void fireRefresh() {
        refreshAction.actionPerformed(null);
    }

    private void fillHtmlPanel() {
        StringTemplate template = SwingHelper.getTemplate("data-set");
        template.setAttribute("entry", getSelectedEntry());
        htmlPanel.setHtml(template.toString());
    }

    private class RefreshAction extends AbstractAction {

        private RefreshAction() {
            super("Refresh the list of data sets from the culture hub");
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
            super("Select a mapping of this data set for editing");
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
                JRadioButton b = new JRadioButton(schemaVersion.getPrefix() + " mapping");
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
            super("Download from the culture hub for editing locally");
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

    public class ReleaseAction extends AbstractAction {

        public ReleaseAction() {
            super("Release your ownership of this data set");
        }

        public void checkEnabled() {
            Entry entry = getSelectedEntry();
            this.setEnabled(entry != null && entry.getState() == State.OWNED_BY_YOU);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Entry entry = getSelectedEntry();
            if (entry == null) return;
            boolean unlock = sipModel.getFeedback().confirm(
                    "Release",
                    String.format("<html>Are you sure that you want to delete your local copy of<br>" +
                            "this data set %s, and unlock it so that someone else can access it?",
                            entry.getSpec()
                    )
            );
            if (unlock) unlockDataSet(entry.getDataSet());
        }

        private void unlockDataSet(final DataSet dataSet) {
            cultureHubClient.unlockDataSet(dataSet, new CultureHubClient.UnlockListener() {
                @Override
                public void unlockComplete(boolean successful) {
                    if (successful) {
                        try {
                            sipModel.exec(new Swing() {
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


    private Entry getSelectedEntry() {
        Object selectedObject = dataSetList.getSelectedValue();
        return selectedObject != null && selectedObject instanceof Entry ? (Entry) selectedObject : null;
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

        public CultureHubClient.DataSetEntry getDataSetEntry() {
            return dataSetEntry;
        }

        public DataSet getDataSet() {
            return dataSet;
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
                    return State.OWNED_BY_YOU;
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
            Icon icon;
            String string;
            if (value instanceof String) {
                string = (String) value;
                icon = null;
            }
            else {
                Entry entry = (Entry) value;
                State state = entry.getState();
                icon = state.icon;
                string = String.format("%s - %s", entry.getSpec(), state.string);
                int substitution = string.indexOf("%s");
                if (substitution > 0) {
                    string = String.format(string, entry.dataSetEntry.lockedBy.email);
                }
            }
            JLabel label = (JLabel) super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
            label.setIcon(icon);
            return label;
        }
    }

    private enum State {
        OWNED_BY_YOU(true, false, "you are owner", DATASET_LOCKED_ICON),
        AVAILABLE(false, true, "can be downloaded", DATASET_DOWNLOAD_ICON),
        UNAVAILABLE(false, false, "owner is %s", DATASET_UNAVAILABLE_ICON),
        BUSY(false, false, "busy locally", DATASET_BUSY_ICON),
        ORPHAN_TAKEN(true, false, "owner is %s but present locally (unusual), cannot be downloaded", DATASET_HUH_ICON),
        ORPHAN_LONELY(true, false, "only present locally (unusual)", DATASET_HUH_ICON),
        ORPHAN_UPDATE(false, true, "you are owner, but not present locally (unusual), can be downloaded", DATASET_HUH_ICON),
        ORPHAN_ARCHIVE(true, true, "not locked but present locally (unusual), can archive and download", DATASET_HUH_ICON);

        private final String string;
        private final Icon icon;
        private final boolean selectable;
        private final boolean downloadable;

        private State(boolean selectable, boolean downloadable, String string, Icon icon) {
            this.selectable = selectable;
            this.downloadable = downloadable;
            this.string = string;
            this.icon = icon;
        }

        public String toString() {
            return String.format("%s: %s", super.toString(), string);
        }
    }
}
