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

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.metadata.*;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.xml.MetadataParser;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamException;
import javax.xml.validation.Validator;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Create a data set for testing
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Mockery {
    private static final String ORG = "organization";
    private File root;
    private File dataSetDir;
    private Storage storage;
    private DataSetModel dataSetModel = new DataSetModel();
    private RecMapping recMapping;
    private Map<String, String> hints = new TreeMap<String, String>();
    private String prefix;

    public Mockery() throws StorageException, MetadataException {
        File target = null, target1 = new File("sip-app/target"), target2 = new File("target");
        if (target1.exists()) target = target1;
        if (target2.exists()) target = target2;
        if (target == null) throw new RuntimeException("target directory not found: " + target);
        root = new File(target, "storage");
        if (root.exists()) delete(root);
        if (!root.mkdirs()) throw new RuntimeException("Unable to create directory " + root.getAbsolutePath());
        storage = new StorageImpl(root);
    }

    public void prepareDataset(String prefix, String recordRootPath, String uniqueElementPath) throws StorageException, IOException, MetadataException {
        this.prefix = prefix;
        storage.createDataSet(prefix, ORG);
        dataSetDir = new File(root, String.format("%s_%s", prefix, ORG));
        hints.clear();
        hints.put(Storage.RECORD_ROOT_PATH, recordRootPath);
        hints.put(Storage.UNIQUE_ELEMENT_PATH, uniqueElementPath);
        File factsSourceDir = new File(getClass().getResource(String.format("/test/%s/dataset", prefix)).getFile());
        if (!factsSourceDir.isDirectory()) throw new RuntimeException();
        FileUtils.copyDirectory(factsSourceDir, dataSetDir);
        File factDefinitionList = new File(getClass().getResource("/definitions/global/fact-definition-list.xml").getFile());
        FileUtils.copyFileToDirectory(factDefinitionList, dataSetDir);
        File definitionSourceDir = new File(getClass().getResource(String.format("/definitions/%s", prefix)).getFile());
        if (!definitionSourceDir.isDirectory()) throw new RuntimeException();
        FileUtils.copyDirectory(definitionSourceDir, dataSetDir);
        dataSetModel.setDataSet(storage.getDataSets().get(dataSetDir.getName()));
        recMapping = RecMapping.create(prefix, dataSetModel);
    }

    public Storage storage() {
        return storage;
    }

    public DataSetModel model() {
        return dataSetModel;
    }

    public Validator validator() throws StorageException {
        return dataSetModel.getDataSet().newValidator(prefix);
    }

    public void createMapping() throws IOException, MetadataException {
        recMapping = RecMapping.read(mappingInputStream(), dataSetModel);
    }

    public String mapping() throws UnsupportedEncodingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        RecMapping.write(os, recMapping);
        return os.toString("UTF-8");
    }

    public NodeMapping map(String fromPath, String toPath) {
        List<Path> inputPaths = new ArrayList<Path>();
        for (String s : fromPath.split(" ")) inputPaths.add(Path.create(s));
        Path outputPath = Path.create(toPath).withDefaultPrefix(prefix);
        RecDefNode node = recMapping.getRecDefTree().getRecDefNode(outputPath);
        if (node == null) {
            throw new RuntimeException("No node found for " + outputPath);
        }
        NodeMapping mapping = new NodeMapping().setInputPaths(inputPaths);
        node.addNodeMapping(mapping);
        return mapping;
    }

    public MetadataParser parser() throws StorageException, XMLStreamException {
        return new MetadataParser(
                dataSetModel.getDataSet().openSourceInputStream(),
                Integer.parseInt(hints.get(Storage.RECORD_COUNT))
        );
    }

    public Node runMapping(MetadataRecord record) throws MappingException {
        GroovyCodeResource resource = new GroovyCodeResource(getClass().getClassLoader());
        MappingRunner mappingRunner = new MappingRunner(resource, recMapping, null);
        System.out.println(mappingRunner.getCode());
        return mappingRunner.runMapping(record);
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
        return getClass().getResource(String.format("/test/%s/example-input.xml", prefix));
    }

    private InputStream mappingInputStream() throws IOException {
        return getClass().getResource(String.format("/test/%s/mapping_%s.xml", prefix, prefix)).openStream();
    }

    public File sampleInputFile() throws IOException {
        return new File(sampleResource().getFile());
    }

    private void delete(File file) {
        if (file.isDirectory()) for (File sub : file.listFiles()) delete(sub);
        if (!file.delete()) throw new RuntimeException(String.format("Unable to delete %s", file.getAbsolutePath()));
    }

}
