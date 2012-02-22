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

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.xml.MetadataParser;
import groovy.util.Node;
import org.apache.commons.io.FileUtils;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * Create a data set for testing
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Mockery {
    private static final String ORG = "organization";
    private static final String SPEC = "test";
    private static final String DIR = String.format("%s_%s", SPEC, ORG);
    private File root;
    private File dataSetDir;
    private Storage storage;
    private DataSetModel dataSetModel = new DataSetModel();
    private RecMapping recMapping;
    private Map<String, String> hints = new TreeMap<String, String>();

    public Mockery() throws StorageException, MetadataException {
        File target = new File("sip-creator/target");
        if (!target.exists()) throw new RuntimeException("target directory not found: " + target);
        root = new File(target, "storage");
        if (root.exists()) delete(root);
        if (!root.mkdirs()) throw new RuntimeException("Unable to create directory " + root.getAbsolutePath());
        storage = new StorageImpl(root);
        storage.createDataSet(SPEC, ORG);
        dataSetDir = new File(root, DIR);
        hints.put(Storage.RECORD_ROOT_PATH, "/bunch-of-chunks/chunk");
        hints.put(Storage.UNIQUE_ELEMENT_PATH, "/bunch-of-chunks/chunk/identi-fire");
    }

    public void preloadDataset() throws IOException, MetadataException, StorageException {
        File sourceDir = new File(getClass().getResource("/dataset").getFile());
        if (!sourceDir.isDirectory()) throw new RuntimeException();
        FileUtils.copyDirectory(sourceDir, dataSetDir);
        dataSetModel.setDataSet(storage.getDataSets().get(DIR));
        recMapping = RecMapping.create("lido", dataSetModel);
    }

    public Storage storage() {
        return storage;
    }

    public DataSetModel model() {
        return dataSetModel;
    }

    public RecMapping mapping() {
        return recMapping;
    }

    public NodeMapping map(String fromString, String toString) {
        Path from = Path.create(fromString);
        Path to = Path.create(toString).defaultPrefix("lido");
        RecDefNode node = recMapping.getRecDefTree().getRecDefNode(to);
        if (node == null) throw new RuntimeException("No node found for "+to);
        NodeMapping mapping = new NodeMapping().setInputPath(from);
        node.addNodeMapping(mapping);
        return mapping;
    }

    public MetadataParser parser() throws StorageException, XMLStreamException {
        return new MetadataParser(
                dataSetModel.getDataSet().openSourceInputStream(),
                Integer.parseInt(hints.get(Storage.RECORD_COUNT))
        );
    }

    public String runMapping(MetadataRecord record) throws MappingException {
        GroovyCodeResource resource = new GroovyCodeResource(getClass().getClassLoader());
        MappingRunner mappingRunner = new MappingRunner(resource, recMapping, null);
        System.out.println(mappingRunner.getCode());
        Node node = mappingRunner.runMapping(record);
        StringWriter writer = new StringWriter();
        XmlNodePrinter printer = new XmlNodePrinter(writer);
        printer.print(node);
        return String.format(
                "<?xml version =\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<lido:lidoWrap xmlns:lido=\"http://www.lido-schema.org\">\n" +
                        "%s</lido:lidoWrap>\n",
                writer.toString()
        );
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
        if (file.isDirectory()) for (File sub : file.listFiles()) delete(sub);
        if (!file.delete()) throw new RuntimeException(String.format("Unable to delete %s", file.getAbsolutePath()));
    }

}
