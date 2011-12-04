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

package eu.delving.sip;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.MetadataModelImpl;
import eu.delving.metadata.Path;
import eu.delving.sip.files.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Create a data set for testing
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MockDataSetFactory {
    public static final String ORG = "orgy";
    public static final String SPEC = "spek";
    private String metadataPrefix = "abm";
    private File root;
    private File specDirectory;
    private Storage storage;
    private DataSet dataSet;

    public MockDataSetFactory() throws StorageException {
        File target = new File("sip-core/target");
        if (!target.exists()) {
            target = new File("target");
            if (!target.exists()) {
                throw new RuntimeException("Target directory not found");
            }
        }
        root = new File(target, "dataSet");
        if (root.exists()) {
            delete(root);
        }
        if (!root.mkdirs()) {
            throw new RuntimeException("Unable to create directory " + root.getAbsolutePath());
        }
        specDirectory = new File(root, SPEC + "_" + ORG);
        storage = new StorageImpl(root);
        dataSet = storage.createDataSet(SPEC, ORG);
    }

    public Storage getStorage() {
        return storage;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public DataSet dataSet() {
        return dataSet;
    }

    public File[] files() {
        return specDirectory.listFiles();
    }

    public File[] directories() {
        return root.listFiles();
    }

    public Map<String, String> hints(int recordCount) {
        Map<String, String> hints = new TreeMap<String, String>();
        hints.put(Storage.RECORD_ROOT_PATH, "/adlibXML/recordList/record");
        hints.put(Storage.RECORD_COUNT, String.valueOf(recordCount));
        hints.put(Storage.UNIQUE_ELEMENT_PATH, "/adlibXML/recordList/record/priref");
        return hints;
    }

    public Statistics stats() {
        List<FieldStatistics> stats = new ArrayList<FieldStatistics>();
        FieldStatistics fieldStatistics = new FieldStatistics(new Path("/stat/path"));
        fieldStatistics.recordOccurrence();
        fieldStatistics.recordValue("booger");
        fieldStatistics.finish();
        stats.add(fieldStatistics);
        Map<String,String> namespaces = new TreeMap<String, String>();
        // todo: add some
        return new Statistics(namespaces, stats, false);
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

    public MetadataModel loadMetadataModel() {
        try {
            MetadataModelImpl metadataModel = new MetadataModelImpl();
            metadataModel.setFactDefinitionsFile(new File(getClass().getResource("/fact-definition-list.xml").getFile()));
            metadataModel.setRecordDefinitionFiles(new File(getClass().getResource("/abm-record-definition.xml").getFile()));
            return metadataModel;
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }
}
