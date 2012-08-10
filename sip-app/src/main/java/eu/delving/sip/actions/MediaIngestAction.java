/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip.actions;

import eu.delving.metadata.Hasher;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;
import java.util.List;

import static eu.delving.sip.files.DataSetState.ABSENT;

/**
 * Import media to be matched with a dataset
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MediaIngestAction extends AbstractAction {
    private static final String[] MEDIA_FILE_EXTENSIONS = {"tif", "tiff", "jpg", "jpeg", "gif", "png"};
    private static final Set<String> EXTENSIONS = new HashSet<String>(Arrays.asList(MEDIA_FILE_EXTENSIONS));
    private static final File TEST_DESTINATION = new File("/tmp/media");
    private JDesktopPane parent;
    private SipModel sipModel;
    private final String RECENT_DIR = "recentMediaDirectory";
    private JFileChooser chooser = new JFileChooser("Media directory");

    public MediaIngestAction(JDesktopPane parent, SipModel sipModel) {
        super("Import new media into this data set");
        putValue(Action.SMALL_ICON, SwingHelper.IMPORT_ICON); // todo: import media icon?
        putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        this.parent = parent;
        this.sipModel = sipModel;
        setEnabled(false);
        prepareChooser();
        sipModel.getDataSetModel().addListener(new DataSetModel.SwingListener() {
            @Override
            public void stateChanged(DataSetModel model, DataSetState state) {
                setEnabled(state != ABSENT);
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        int choiceMade = chooser.showOpenDialog(parent);
        if (choiceMade == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            sipModel.getPreferences().put(RECENT_DIR, file.getAbsolutePath());
            selectDirectory(file); // it's a boolean
        }
    }

    private boolean selectDirectory(File file) {
        if (!file.exists() || !file.isDirectory() || sipModel.getDataSetModel().isEmpty()) return false;
        DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        String spec = dataSet.getSpec();
        boolean doImport = sipModel.getFeedback().confirm(
                "Verify your choice",
                String.format(
                        "<html>Import all this directory contains<br><br>" +
                                "<pre><strong>%s</strong></pre><br>" +
                                "into data set '<strong>%s</strong>'?<br>",
                        file.getAbsolutePath(),
                        spec
                )
        );
        if (doImport) {
            setEnabled(false);
            sipModel.exec(new DirectoryScanner(dataSet, file));
            return true;
        }
        return false;
    }

    private void prepareChooser() {
        File directory = new File(sipModel.getPreferences().get(RECENT_DIR, System.getProperty("user.home")));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(directory.getParentFile());
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Directories";
            }
        });
        chooser.setMultiSelectionEnabled(false);
    }


    private class DirectoryScanner implements Work.LongTermWork, Work.DataSetWork {
        private Hasher hasher = new Hasher();
        private File root;
        private List<File> fileList = new ArrayList<File>();
        private ProgressListener progressListener;
        private DataSet dataSet;

        private DirectoryScanner(DataSet dataSet, File root) {
            this.dataSet = dataSet;
            this.root = root;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public Job getJob() {
            return Job.IMPORT_MEDIA;
        }

        @Override
        public void run() {
            gather(root);
            progressListener.prepareFor(fileList.size());
            TEST_DESTINATION.mkdirs();
            int walk = 0;
            for (File file : fileList) {
                progressListener.setProgress(walk++);
                if (!isMediaFile(file)) continue;
                try {
                    File destinationFile = new File(TEST_DESTINATION, "MEDIA_IMPORT");
                    OutputStream outputStream = hasher.createDigestOutputStream(new FileOutputStream(destinationFile));
                    InputStream inputStream = new FileInputStream(file);
                    IOUtils.copy(inputStream, outputStream);
                    File hashedFile = new File(TEST_DESTINATION, String.format("%s.%s", hasher.getHashString(), getExtension(file)));
                    FileUtils.deleteQuietly(hashedFile);
                    destinationFile.renameTo(hashedFile);
                }
                catch (IOException e) {
                    sipModel.getFeedback().alert("Problem while scanning directories for media", e);
                }
            }
        }

        private void gather(File dir) {
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (file.isDirectory()) {
                    gather(file);
                }
                else {
                    fileList.add(file);
                }
            }
        }

        private boolean isMediaFile(File file) {
            String extension = getExtension(file);
            if (!EXTENSIONS.contains(extension)) return false;
            // more to check
            return true;
        }

        private String getExtension(File file) {
            int lastDot = file.getName().lastIndexOf('.');
            if (lastDot < 1) return "";
            return file.getName().substring(lastDot + 1).toLowerCase();
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }
    }
}
