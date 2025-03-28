/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.frames;

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.HomeDirectory;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageHelper;
import eu.delving.sip.model.SipModel;
import org.joda.time.DateTime;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static eu.delving.sip.base.NetworkClient.SipEntry;
import static eu.delving.sip.base.SwingHelper.ICON_DOWNLOAD;
import static eu.delving.sip.files.StorageHelper.datasetNameFromSipZip;
import static javax.swing.ListSelectionModel.*;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;
import static org.apache.commons.io.FileUtils.deleteQuietly;

/**
 * Show the datasets both local and on the server, so all info about their
 * status is unambiguous.
 */

public class RemoteDataSetFrame extends FrameBase {
    private final UploadModel uploadModel;
    private final WorkItemModel workItemModel;
    private final DownloadModel downloadModel;
    private final JList<DownloadItem> downloadList;
    private final JList<WorkItem> workItemList;
    private final JList<UploadItem> uploadList;
    private final NetworkClient networkClient;
    private final JTextField filterField = new JTextField(16);
    private final JButton workItemSortModeButton = new JButton();
    private NetworkClient.SipZips sipZips;
    private DownloadItem selectedDownload;
    private List<WorkItem> selectedWorkItems;
    private UploadItem selectedUpload;

    public RemoteDataSetFrame(final SipModel sipModel, NetworkClient networkClient) {
        super(Which.DATA_SET, sipModel, "Data Sets");
        this.networkClient = networkClient;
        this.downloadModel = new DownloadModel();
        this.downloadList = new JList<DownloadItem>(downloadModel);
        this.downloadList.setSelectionMode(SINGLE_SELECTION);
        this.downloadList.addListSelectionListener(DOWNLOAD_LISTENER);
        this.downloadList.setCellRenderer(DOWNLOAD_CELL_RENDERER);
        this.workItemModel = new WorkItemModel();
        this.workItemList = new JList<WorkItem>(workItemModel);
        this.workItemList.setSelectionMode(MULTIPLE_INTERVAL_SELECTION);
        this.workItemList.setCellRenderer(WORK_CELL_RENDERER);
        this.workItemList.addListSelectionListener(WORK_ITEM_LISTENER);
        this.workItemList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1 && OPEN_WORK_ITEM_ACTION.isEnabled()) {
                    if (workItemList.getSelectedIndex() != workItemList.locationToIndex(e.getPoint()))
                        return;
                    OPEN_WORK_ITEM_ACTION.actionPerformed(null);
                }
            }
        });

        this.uploadModel = new UploadModel();
        this.uploadList = new JList<UploadItem>(uploadModel);
        this.uploadList.setSelectionMode(SINGLE_SELECTION);
        this.uploadList.addListSelectionListener(UPLOAD_LISTENER);
        this.uploadList.setCellRenderer(UPLOAD_CELL_RENDERER);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                change();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                change();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                change();
            }

            private void change() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        String pattern = filterField.getText().trim();
                        downloadModel.setFilter(pattern);
                        workItemModel.setFilter(pattern);
                        uploadModel.setFilter(pattern);
                    }
                });
            }
        });

        workItemSortModeButton.addActionListener(e -> {
            workItemModel.toggleSortMode();
            updateWorkItemSortModeButton();
        });
        updateWorkItemSortModeButton();
    }

    private void updateWorkItemSortModeButton() {
        if (workItemModel.sortMode == SortMode.DATE)
            workItemSortModeButton.setText("Sort by name");
        else
            workItemSortModeButton.setText("Sort by date");
    }

    @Override
    protected void onOpen(boolean opened) {
        if (opened)
            Swing.Exec.later(new Swing() {
                @Override
                public void run() {
                    downloadList.requestFocus();
                }
            });
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createCenter(), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    private JPanel createCenter() {
        JPanel p = new JPanel(new GridLayout(1, 0, 10, 10));
        p.add(createDownloadPanel());
        p.add(createWorkPanel());
        p.add(createUploadPanel());
        return p;
    }

    private JPanel createSouth() {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JPanel c = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JPanel l = new JPanel(new FlowLayout(FlowLayout.LEFT));
        l.add(createFilterPanel());
        c.add(workItemSortModeButton);
        r.add(new JButton(REFRESH_ACTION));
        JPanel p = new JPanel(new GridLayout(1, 0, 10, 10));
        p.add(l);
        p.add(c);
        p.add(r);
        return p;
    }

    private JComponent createDownloadPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(SwingHelper.scrollV("Datasets Available from Narthex", downloadList), BorderLayout.CENTER);
        p.add(createDownloadSouth(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createDownloadSouth() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Actions"));
        p.add(button(DOWNLOAD_ACTION), BorderLayout.CENTER);
        return p;
    }

    private JComponent createWorkPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(SwingHelper.scrollV("Local Datasets", workItemList), BorderLayout.CENTER);
        p.add(createWorkSouth(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createWorkSouth() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Actions"));
        p.add(button(OPEN_WORK_ITEM_ACTION), BorderLayout.CENTER);
        p.add(button(DELETE_WORK_ITEM_ACTION), BorderLayout.EAST);
        return p;
    }

    private JComponent createUploadPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(SwingHelper.scrollV("SIP Files for upload", uploadList));
        p.add(createUploadSouth(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createUploadSouth() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Actions"));
        p.add(button(UPLOAD_ACTION), BorderLayout.CENTER);
        p.add(button(DELETE_UPLOAD_ACTION), BorderLayout.EAST);
        return p;
    }

    private JButton button(Action action) {
        JButton button = new JButton(action);
        button.setHorizontalAlignment(JButton.LEFT);
        return button;
    }

    private JPanel createFilterPanel() {
        JLabel label = new JLabel("Filter: ", JLabel.RIGHT);
        label.setLabelFor(filterField);
        JPanel p = new JPanel(new BorderLayout());
        p.add(label, BorderLayout.WEST);
        p.add(filterField, BorderLayout.CENTER);
        return p;
    }

    @Override
    public void refresh() {
        Swing.Exec.later(new Swing() {
            @Override
            public void run() {
                REFRESH_ACTION.fetchList();
            }
        });
    }

    class DownloadItem implements Comparable<DownloadItem> {
        public final String remote;
        public final DateTime dateTime;
        public final boolean local;

        DownloadItem(String remote, boolean local) {
            this.remote = remote;
            this.dateTime = StorageHelper.dateTimeFromSipZip(remote);
            this.local = local;
        }

        @Override
        public int compareTo(DownloadItem other) {
            return other.dateTime.compareTo(dateTime);
        }

        public String toString() {
            return remote;
        }
    }

    public ListCellRenderer<DownloadItem> DOWNLOAD_CELL_RENDERER = new ListCellRenderer<DownloadItem>() {
        private DefaultListCellRenderer defaultListCellRenderer = new DefaultListCellRenderer();

        @Override
        public Component getListCellRendererComponent(JList list, DownloadItem downloadItem, int index,
                boolean isSelected, boolean cellHasFocus) {
            String html = String.format(
                    "<html>" + (downloadItem.local ? "%s" : "<b>%s</b>"),
                    downloadItem.toString());
            return defaultListCellRenderer.getListCellRendererComponent(list, html, index, isSelected, cellHasFocus);
        }
    };

    class WorkItem {
        private final DataSet dataset;
        public final DateTime dateTime;

        WorkItem(DataSet dataset) {
            this.dataset = dataset;
            this.dateTime = StorageHelper.dateTimeFromSipZip(dataset.getSipFile());
        }
    }

    public ListCellRenderer<WorkItem> WORK_CELL_RENDERER = new ListCellRenderer<WorkItem>() {
        private DefaultListCellRenderer defaultListCellRenderer = new DefaultListCellRenderer();

        @Override
        public Component getListCellRendererComponent(JList list, WorkItem workItem, int index, boolean isSelected,
                boolean cellHasFocus) {
            String html = String.format(
                    "<html><b>%s</b> %s",
                    workItem.dataset.getSipFile().getName(), workItem.dataset.getState());
            return defaultListCellRenderer.getListCellRendererComponent(list, html, index, isSelected, cellHasFocus);
        }
    };

    class UploadItem implements Comparable<UploadItem> {
        public final File file;
        public final DateTime date;
        public SipEntry sipEntry;

        public UploadItem(File file) {
            this.file = file;
            this.date = StorageHelper.dateTimeFromSipZip(file);
        }

        public String getDatasetName() {
            return datasetNameFromSipZip(file);
        }

        @Override
        public int compareTo(UploadItem other) {
            return other.date.compareTo(date);
        }
    }

    public ListCellRenderer<UploadItem> UPLOAD_CELL_RENDERER = new ListCellRenderer<UploadItem>() {
        private DefaultListCellRenderer defaultListCellRenderer = new DefaultListCellRenderer();

        @Override
        public Component getListCellRendererComponent(JList list, UploadItem uploadItem, int index, boolean isSelected,
                boolean cellHasFocus) {
            String html;
            if (uploadItem.sipEntry != null) {
                html = String.format(
                        "<html><b>%s</b>",
                        uploadItem.file.getName());
            } else {
                html = String.format(
                        "<html>%s",
                        uploadItem.file.getName());
            }
            return defaultListCellRenderer.getListCellRendererComponent(list, html, index, isSelected, cellHasFocus);
        }
    };

    class DownloadModel extends AbstractListModel<DownloadItem> {
        private List<DownloadItem> downloadItems = new ArrayList<DownloadItem>();
        private String filter = "";
        private List<DownloadItem> filteredDownloadItems = downloadItems;

        @Override
        public int getSize() {
            return filteredDownloadItems.size();
        }

        @Override
        public DownloadItem getElementAt(int rowIndex) {
            return filteredDownloadItems.get(rowIndex);
        }

        public void setFilter(String filter) {
            this.filter = filter;
            //activateFilter();
            refreshDownloads();
        }

        public void activateFilter() {
            if (filter.isEmpty()) {
                filteredDownloadItems = downloadItems;
                return;
            }
            downloadItems = new ArrayList<>();
            for (DownloadItem downloadItem : downloadItems) {
                if (downloadItem.toString().contains(filter)) {
                    filteredDownloadItems.add(downloadItem);
                }
            }
        }

        public void refreshDownloads() {
            // get a set of the local work datasets
            Map<String, DataSet> datasetMap = sipModel.getStorage().getDataSets();
            // get a set of all the remote file names
            Set<String> remoteFiles = new HashSet<String>();
            if (sipZips != null && sipZips.available != null)
                for (SipEntry entry : sipZips.available)
                    remoteFiles.add(entry.file);
            // clear
            fireIntervalRemoved(this, 0, getSize());
            Map<String, DownloadItem> downloadItemMap = new TreeMap<String, DownloadItem>();
            for (String remote : remoteFiles) {
                DownloadItem item = downloadItemMap.get(remote);
                if (item == null)
                    downloadItemMap.put(remote, new DownloadItem(remote, datasetMap.containsKey(remote)));
            }
            downloadItems.clear();
            downloadItems.addAll(downloadItemMap.values());
            activateFilter();
            fireIntervalAdded(this, 0, getSize());
        }
    }

    enum SortMode {
        NAME,
        DATE
    }

    class WorkItemModel extends AbstractListModel<WorkItem> {
        private List<WorkItem> workItems = new ArrayList<WorkItem>();
        private String filter = "";
        private List<WorkItem> filteredWorkItems = workItems;
        private SortMode sortMode = SortMode.DATE;

        public void toggleSortMode() {
            sortMode = sortMode == SortMode.DATE ? SortMode.NAME : SortMode.DATE;
            setFilter(this.filter);
        }

        @Override
        public int getSize() {
            return filteredWorkItems.size();
        }

        @Override
        public WorkItem getElementAt(int rowIndex) {
            return filteredWorkItems.get(rowIndex);
        }

        public void setFilter(String filter) {
            this.filter = filter;
            //activateFilter();
            refreshWorkItems();
        }

        public void activateFilter() {
            if (filter.isEmpty()) {
                filteredWorkItems = workItems;
                return;
            }
            filteredWorkItems = new ArrayList<>();
            for (WorkItem workItem : workItems) {
                if (workItem.dataset.getSpec().contains(filter)) {
                    filteredWorkItems.add(workItem);
                }
            }
        }

        public void refreshWorkItems() {
            fireIntervalRemoved(this, 0, getSize());
            workItems.clear();
            for (Map.Entry<String, DataSet> entry : sipModel.getStorage().getDataSets().entrySet()) {
                DataSet dataSet = entry.getValue();
                try {
                    if (!dataSet.getSchemaVersion().getPrefix().equals("unknown")) {
                        workItems.add(new WorkItem(dataSet));
                    }
                } catch (IllegalArgumentException e) {
                    // Something wrong with the schema - ignore this dataset
                    sipModel.getFeedback().alert(String.format("Error reading dataset at %s", dataSet.getSipFile()), e);
                }
            }

            Collections.sort(workItems, (o1, o2) -> {
                if (sortMode == SortMode.DATE)
                    return o2.dateTime.compareTo(o1.dateTime);
                return o1.dataset.getSipFile().getName().compareTo(o1.dataset.getSipFile().getName());
            });
            activateFilter();
            fireIntervalAdded(this, 0, getSize());
        }

        public void removeWorkItem(WorkItem selectedWorkItem) {
            workItems.remove(selectedWorkItem);
            activateFilter();
        }
    }

    class UploadModel extends AbstractListModel<UploadItem> {
        private List<UploadItem> uploadItems = new ArrayList<UploadItem>();
        private String filter = "";
        private List<UploadItem> filteredUploadItems = uploadItems;

        @Override
        public int getSize() {
            return filteredUploadItems.size();
        }

        @Override
        public UploadItem getElementAt(int rowIndex) {
            return filteredUploadItems.get(rowIndex);
        }

        public void setFilter(String filter) {
            this.filter = filter;
            //activateFilter();
            refreshUploads();
        }

        public void activateFilter() {
            if (filter.isEmpty()) {
                filteredUploadItems = uploadItems;
                return;
            }
            filteredUploadItems = new ArrayList<>();
            for (UploadItem uploadItem : uploadItems) {
                if (uploadItem.getDatasetName().contains(filter)) {
                    filteredUploadItems.add(uploadItem);
                }
            }
        }

        public void refreshUploads() {
            Map<String, SipEntry> uploadedMap = new TreeMap<String, SipEntry>();
            if (sipZips != null && sipZips.uploaded != null) {
                for (SipEntry entry : sipZips.uploaded)
                    uploadedMap.put(entry.file, entry);
            }
            File[] uploadFiles = HomeDirectory.UP_DIR.listFiles();
            if (uploadFiles == null)
                throw new RuntimeException();
            fireIntervalRemoved(this, 0, getSize());
            uploadItems.clear();
            for (File uploadFile : uploadFiles) {
                if (uploadFile.getPath().endsWith(".sip.zip")) {
                    UploadItem item = new UploadItem(uploadFile);
                    item.sipEntry = uploadedMap.get(item.file.getName());
                    uploadItems.add(item);
                }
            }
            Collections.sort(uploadItems);
            activateFilter();
            fireIntervalAdded(this, 0, getSize());
        }
    }

    public final RefreshAction REFRESH_ACTION = new RefreshAction();

    public class RefreshAction extends AbstractAction {

        private RefreshAction() {
            super("Refresh list from Narthex");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_FETCH_LIST);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            networkClient.narthexCredentials.ask();
            fetchList();
        }

        public void fetchList() {
            if (!networkClient.narthexCredentials.areSet())
                return;
            networkClient.fetchNarthexSipList(new NetworkClient.NarthexListListener() {
                @Override
                public void listReceived(NetworkClient.SipZips sipZips) {
                    RemoteDataSetFrame.this.sipZips = sipZips;
                    downloadModel.refreshDownloads();
                    workItemModel.refreshWorkItems();
                    uploadModel.refreshUploads();
                    setEnabled(true);
                }

                @Override
                public void failed(Exception e) {
                    sipModel.getFeedback().alert("Unable to fetch Narthex sip-zip list", e);
                    downloadModel.refreshDownloads();
                    workItemModel.refreshWorkItems();
                    uploadModel.refreshUploads();
                    setEnabled(true);
                }
            });
        }
    }

    public final DownloadAction DOWNLOAD_ACTION = new DownloadAction();

    public class DownloadAction extends AbstractAction {

        private DownloadAction() {
            super("Download from Narthex");
            putValue(Action.SMALL_ICON, ICON_DOWNLOAD);
            setEnabled(false);
        }

        public void checkEnabled() {
            this.setEnabled(selectedDownload != null && !selectedDownload.local);
        }

        @Override
        public void actionPerformed(final ActionEvent actionEvent) {
            if (selectedDownload == null)
                return;
            setEnabled(false);
            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(selectedDownload.toString());
                networkClient.downloadNarthexDataset(
                        selectedDownload.toString(),
                        dataSet,
                        new Swing() {
                            @Override
                            public void run() {
                                setEnabled(true);
                                downloadModel.refreshDownloads();
                                workItemModel.refreshWorkItems();
                            }
                        });
            } catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create data set called " + selectedDownload.toString(), e);
            }
        }
    }

    public final DownloadListener DOWNLOAD_LISTENER = new DownloadListener();

    private class DownloadListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
                return;
            int downloadIndex = downloadList.getSelectedIndex();
            if (downloadIndex < 0) {
                DOWNLOAD_ACTION.putValue(Action.NAME, "Download");
            } else {
                selectedDownload = downloadModel.getElementAt(downloadIndex);
                DOWNLOAD_ACTION.putValue(Action.NAME, "Download " + selectedDownload.remote);
            }
            DOWNLOAD_ACTION.checkEnabled();
        }
    }

    public final OpenWorkItemAction OPEN_WORK_ITEM_ACTION = new OpenWorkItemAction();

    public class OpenWorkItemAction extends AbstractAction {

        private OpenWorkItemAction() {
            super("Open");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EDIT);
            setEnabled(false);
        }

        public void checkEnabled() {
            this.setEnabled(selectedWorkItems != null);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (selectedWorkItems == null || selectedWorkItems.isEmpty())
                return;
            sipModel.setDataSet(selectedWorkItems.get(0).dataset, new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                    sipModel.getViewSelector().selectView(AllFrames.View.QUICK_MAPPING);
                }
            });
        }
    }

    public final DeleteWorkItemAction DELETE_WORK_ITEM_ACTION = new DeleteWorkItemAction();

    public class DeleteWorkItemAction extends AbstractAction {

        private DeleteWorkItemAction() {
            super("Delete");
            setEnabled(false);
        }

        public void checkEnabled() {
            this.setEnabled(selectedWorkItems != null);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (selectedWorkItems == null)
                return;
            try {
                for (WorkItem selectedWorkItem : selectedWorkItems) {
                    selectedWorkItem.dataset.remove();
                    workItemModel.removeWorkItem(selectedWorkItem);
                }

                workItemList.clearSelection();
                workItemModel.refreshWorkItems();
                downloadModel.refreshDownloads();
            } catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to delete", e);
            }
        }
    }

    public final WorkItemListener WORK_ITEM_LISTENER = new WorkItemListener();

    private class WorkItemListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
                return;
            int[] workItemIndices = workItemList.getSelectedIndices();
            if (workItemIndices.length == 0) {
                selectedWorkItems = null;
                OPEN_WORK_ITEM_ACTION.putValue(Action.NAME, "Open");
            } else {
                selectedWorkItems = new ArrayList(workItemIndices.length);
                for (int selectedIndex : workItemIndices) {
                    selectedWorkItems.add(workItemModel.getElementAt(selectedIndex));
                }
                OPEN_WORK_ITEM_ACTION.putValue(Action.NAME,
                        "Open " + selectedWorkItems.get(0).dataset.getSipFile().getName());
            }
            OPEN_WORK_ITEM_ACTION.checkEnabled();
            DELETE_WORK_ITEM_ACTION.checkEnabled();
        }
    }

    public final UploadAction UPLOAD_ACTION = new UploadAction();

    private class UploadAction extends AbstractAction {

        private UploadAction() {
            super("Upload");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_UPLOAD);
            setEnabled(false);
        }

        public void checkEnabled() {
            this.setEnabled(selectedUpload != null && selectedUpload.sipEntry == null);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (selectedUpload == null)
                return;
            setEnabled(false);
            try {
                networkClient.uploadNarthex(selectedUpload.file, selectedUpload.getDatasetName(), new Swing() {
                    @Override
                    public void run() {
                        sipModel.getFeedback().alert("Uploaded " + selectedUpload.file.getName() + " to Narthex");
                        setEnabled(true);
                        uploadModel.refreshUploads();
                    }
                });
            } catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to upload to Narthex", e);
            }
        }
    }

    public final DeleteUploadAction DELETE_UPLOAD_ACTION = new DeleteUploadAction();

    private class DeleteUploadAction extends AbstractAction {

        private DeleteUploadAction() {
            super("Delete");
            setEnabled(false);
        }

        public void checkEnabled() {
            this.setEnabled(selectedUpload != null);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (selectedUpload == null)
                return;
            deleteQuietly(selectedUpload.file);
            uploadModel.refreshUploads();
        }
    }

    public final UploadListener UPLOAD_LISTENER = new UploadListener();

    private class UploadListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
                return;
            JList list = (JList) e.getSource();
            int uploadIndex = list.getSelectedIndex();
            if (uploadIndex < 0) {
                UPLOAD_ACTION.putValue(Action.NAME, "Upload");
            } else {
                selectedUpload = uploadModel.getElementAt(uploadIndex);
                UPLOAD_ACTION.putValue(Action.NAME, "Upload " + selectedUpload.file.getName());
            }
            UPLOAD_ACTION.checkEnabled();
            DELETE_UPLOAD_ACTION.checkEnabled();
        }
    }

}
