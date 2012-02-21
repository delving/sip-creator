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

import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * Create a data set for testing
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Mockery {
    public static final String ORG = "organization";
    public static final String SPEC = "test";
    private File root;
    private File dataSetDir;
    private Storage storage;
    private DataSet dataSet;
    private Map<String, String> hints = new TreeMap<String, String>();

    public Mockery() throws StorageException {
        File target = new File("sip-core/target");
        if (!target.exists()) throw new RuntimeException("target directory not found: "+target);
        root = new File(target, "storage");
        if (root.exists()) delete(root);
        if (!root.mkdirs()) throw new RuntimeException("Unable to create directory " + root.getAbsolutePath());
        storage = new StorageImpl(root);
        dataSet = storage.createDataSet(SPEC, ORG);
        dataSetDir = new File(root, SPEC + "_" + ORG);
        hints.put(Storage.RECORD_ROOT_PATH, "/bunch-of-chunks/chunk");
        hints.put(Storage.UNIQUE_ELEMENT_PATH, "/bunch-of-chunks/chunk/identi-fire");
    }

    public void preloadDataset() throws IOException {
        File sourceDir = new File(getClass().getResource("/dataset").getFile());
        if (!sourceDir.isDirectory()) throw new RuntimeException();
        FileUtils.copyDirectory(sourceDir, dataSetDir);
    }

    public Storage storage() {
        return storage;
    }

    public DataSet dataSet() {
        return dataSet;
    }

    public File[] files() {
        return dataSetDir.listFiles();
    }

    public int fileCount() {
        return files().length;
    }

    public void delete() {
        delete(root);
    }

    public Map<String, String> hints() {
        return hints;
    }
    
    private URL sampleResource() {
        return getClass().getResource("/non-lido-example.xml");
    }

    public File sampleInputFile() throws IOException {
        return new File(sampleResource().getFile());
    }

    public InputStream sampleInputStream() throws IOException {
        return sampleResource().openStream();
    }

    private void delete(File file) {
        if (file.isDirectory()) for (File sub : file.listFiles())delete(sub);
        if (!file.delete()) throw new RuntimeException(String.format("Unable to delete %s", file.getAbsolutePath()));
    }

}
