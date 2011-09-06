package eu.delving.sip;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.MetadataModelImpl;
import eu.delving.metadata.Path;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import eu.delving.sip.files.FileStoreImpl;
import eu.delving.sip.files.Statistics;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Create a file store for testing
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MockFileStoreFactory {
    public static final String SPEC = "spek";
    private String metadataPrefix = "abm";
    private File root;
    private File specDirectory;
    private FileStore fileStore;
    private FileStore.DataSetStore dataSetStore;

    public MockFileStoreFactory() throws FileStoreException {
        File target = new File("sip-core/target");
        if (!target.exists()) {
            target = new File("target");
            if (!target.exists()) {
                throw new RuntimeException("Target directory not found");
            }
        }
        root = new File(target, "file-store");
        if (root.exists()) {
            delete(root);
        }
        if (!root.mkdirs()) {
            throw new RuntimeException("Unable to create directory " + root.getAbsolutePath());
        }
        specDirectory = new File(root, SPEC);
        fileStore = new FileStoreImpl(root);
        dataSetStore = fileStore.createDataSetStore(SPEC);
    }

    public FileStore getFileStore() {
        return fileStore;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public FileStore.DataSetStore store() {
        return dataSetStore;
    }

    public File [] files() {
        return specDirectory.listFiles();
    }

    public File [] directories() {
        return root.listFiles();
    }

    public Map<String,String> hints() {
        Map<String,String> hints = new TreeMap<String,String>();
        hints.put(FileStore.RECORD_ROOT_PATH, "/adlibXML/recordList/record");
        hints.put(FileStore.RECORD_COUNT, "0");
        hints.put(FileStore.UNIQUE_ELEMENT_PATH, "/adlibXML/recordList/record/priref");
        return hints;
    }

    public Statistics stats() {
        List<FieldStatistics> stats = new ArrayList<FieldStatistics>();
        FieldStatistics fieldStatistics = new FieldStatistics(new Path("/stat/path"));
        fieldStatistics.recordOccurrence();
        fieldStatistics.recordValue("booger");
        fieldStatistics.finish();
        stats.add(fieldStatistics);
        return new Statistics(stats, false);
    }

    public void delete() {
        delete(root);
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                delete(sub);
            }
        }
        if (!file.delete()) {
            throw new RuntimeException(String.format("Unable to delete %s", file.getAbsolutePath()));
        }
    }

    MetadataModel getMetadataModel() {
        try {
            MetadataModelImpl metadataModel = new MetadataModelImpl();
            metadataModel.setRecordDefinitionResources(Arrays.asList("/abm-record-definition.xml"));
            return metadataModel;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
