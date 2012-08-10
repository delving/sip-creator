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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
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
            sipModel.exec(new DirectoryScanner(dataSet, file, TEST_DESTINATION));
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
        public static final String INDEX_FILE = "media-files.xml";
        public static final int ALLOWED_FAILURES = 3;
        private Hasher hasher = new Hasher();
        private File sourceDirectory, targetDirectory;
        private List<File> fileList = new ArrayList<File>();
        private ProgressListener progressListener;
        private DataSet dataSet;
        private MediaFiles mediaFiles = new MediaFiles();

        private DirectoryScanner(DataSet dataSet, File sourceDirectory, File targetDirectory) {
            this.dataSet = dataSet;
            this.sourceDirectory = sourceDirectory;
            this.targetDirectory = targetDirectory;
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
            gatherFilesFrom(sourceDirectory);
            progressListener.prepareFor(fileList.size());
            TEST_DESTINATION.mkdirs();
            int failures = 0;
            int walk = 0;
            for (File file : fileList) {
                progressListener.setProgress(walk++);
                if (!isMediaFile(file)) continue;
                try {
                    File destinationFile = new File(targetDirectory, "FILE_BEING_COPIED");
                    OutputStream outputStream = hasher.createDigestOutputStream(new FileOutputStream(destinationFile));
                    InputStream inputStream = new FileInputStream(file);
                    IOUtils.copy(inputStream, outputStream);
                    File hashedFile = new File(targetDirectory, String.format("%s.%s", hasher.getHashString(), getExtension(file)));
                    FileUtils.deleteQuietly(hashedFile);
                    if (!destinationFile.renameTo(hashedFile)) {
                        throw new IOException("Unable to rename " + destinationFile + " to " + hashedFile);
                    }
                    mediaFiles.add(file, hashedFile);
                }
                catch (IOException e) {
                    if (++failures == ALLOWED_FAILURES) {
                        sipModel.getFeedback().alert("Too many failures scanning directories for media", e);
                    }
                    else {
                        sipModel.getFeedback().alert(String.format(
                                "Problem %d/%d while scanning directories for media",
                                failures, ALLOWED_FAILURES), e
                        );
                    }
                }
            }
            mediaFiles.purge();
            try {
                File indexFile = new File(targetDirectory, INDEX_FILE);
                XStream stream = new XStream();
                stream.processAnnotations(MediaFiles.class);
                FileOutputStream out = new FileOutputStream(indexFile);
                stream.toXML(mediaFiles, out);
                out.close();
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to write " + INDEX_FILE + " in " + targetDirectory.getAbsolutePath(), e);
            }
        }

        private void gatherFilesFrom(File dir) {
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (file.isDirectory()) {
                    gatherFilesFrom(file);
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

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }
    }

    @XStreamAlias("media-files")
    public static class MediaFiles {
        @XStreamAsAttribute
        public Date date;
        @XStreamImplicit
        public List<MediaFile> mediaFiles;

        public MediaFiles() {
            this.date = new Date();
        }

        public void add(File originalFile, File hashedFile) throws IOException {
            if (mediaFiles == null) mediaFiles = new ArrayList<MediaFile>();
            mediaFiles.add(new MediaFile(originalFile.getAbsolutePath(), hashedFile.getName()));
        }

        public void purge() {
            Map<String, Counter> counts = new HashMap<String, Counter>();
            for (MediaFile mediaFile : mediaFiles) {
                for (String keyword : mediaFile.keywords) {
                    Counter counter = counts.get(keyword);
                    if (counter == null) counts.put(keyword, counter = new Counter(keyword));
                    counter.count++;
                }
            }
            for (Counter counter : counts.values()) {
                if (counter.count == mediaFiles.size()) {
                    for (MediaFile mediaFile : mediaFiles) {
                        mediaFile.keywords.remove(counter.keyword);
                    }
                }
            }
        }
    }

    private static class Counter {
        String keyword;
        int count;

        private Counter(String keyword) {
            this.keyword = keyword;
        }
    }

    @XStreamAlias("file")
    public static class MediaFile {
        @XStreamAsAttribute
        public String name;

        public String path;

        public Set<String> keywords;

        public MediaFile() {
        }

        public MediaFile(String path, String name) {
            this.path = path;
            this.name = name;
            this.keywords = new HashSet<String>(23);
            Collections.addAll(keywords, path.split("[\\/]"));
        }

    }

    private static String getExtension(File file) {
        int lastDot = file.getName().lastIndexOf('.');
        if (lastDot < 1) return "";
        return file.getName().substring(lastDot + 1).toLowerCase();
    }
}
