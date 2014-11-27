package eu.delving.sip.frames;

import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.HomeDirectory;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageHelper;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class UploadListModel extends AbstractListModel<File> {

    public static final String SELECT_FILE = "Select file for uploading";
    private final NetworkClient networkClient;
    private final SipModel sipModel;
    private List<File> uploadFiles = new ArrayList<File>();
    private File selectedFile;

    public UploadListModel(SipModel sipModel, NetworkClient networkClient) {
        this.sipModel = sipModel;
        this.networkClient = networkClient;
    }

    @Override
    public int getSize() {
        return uploadFiles.size();
    }

    @Override
    public File getElementAt(int index) {
        return uploadFiles.get(index);
    }

    public void refresh() {
        File[] files = HomeDirectory.UP_DIR.listFiles();
        if (files == null) throw new RuntimeException();
        uploadFiles.clear();
        fireIntervalRemoved(this, 0, getSize());
        Collections.addAll(uploadFiles, files);
        Collections.sort(uploadFiles, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return (int)(b.lastModified() - a.lastModified());
            }
        });
        fireIntervalAdded(this, 0, getSize());
    }

    public ListCellRenderer<File> CELL_RENDERER = new ListCellRenderer<File>() {

        private DefaultListCellRenderer defaultListCellRenderer = new DefaultListCellRenderer();

        @Override
        public Component getListCellRendererComponent(JList list, File file, int index, boolean isSelected, boolean cellHasFocus) {
            String bold = "<html><b>" + file.getName();
            return defaultListCellRenderer.getListCellRendererComponent(list, bold, index, isSelected, cellHasFocus);
        }
    };

    public final RefreshAction REFRESH_ACTION = new RefreshAction();

    private class RefreshAction extends AbstractAction {

        private RefreshAction() {
            super("Refresh");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_FETCH_LIST);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            refresh();
        }
    }

    public final UploadAction UPLOAD_ACTION = new UploadAction();

    private class UploadAction extends AbstractAction {

        private UploadAction() {
            super(SELECT_FILE);
            putValue(Action.SMALL_ICON, SwingHelper.ICON_UPLOAD);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (selectedFile == null) {
                if (!uploadFiles.isEmpty()) {
                    selectedFile = uploadFiles.get(0);
                }
            }
            else {
                String datsetName = StorageHelper.datasetNameFromSipZip(selectedFile);
                setEnabled(false);
                try {
                    networkClient.uploadNarthex(
                            selectedFile,
                            sipModel.getPreferences().get(Storage.NARTHEX_URL, ""),
                            sipModel.getPreferences().get(Storage.NARTHEX_API_KEY, ""),
                            datsetName,
                            new Swing() {
                                @Override
                                public void run() {
                                    setEnabled(true);
                                }
                            }
                    );
                }
                catch (StorageException e) {
                    sipModel.getFeedback().alert("Unable to upload to Narthex", e);
                }
            }
        }
    }

    public final Selection UPLOAD_SELECTION = new Selection();

    private class Selection implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) return;
            JList list = (JList) e.getSource();
            int index = list.getSelectedIndex();
            if (index < 0) {
                UPLOAD_ACTION.putValue(Action.NAME, SELECT_FILE);
            }
            else {
                selectedFile = uploadFiles.get(index);
                UPLOAD_ACTION.putValue(Action.NAME, "Upload " + selectedFile.getName());
            }
        }
    }
}
