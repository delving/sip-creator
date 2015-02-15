/*
 * Copyright 2011, 2012 Delving BV
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
import eu.delving.metadata.CachedResourceResolver;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.util.FileSystemFetcher;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.xml.MetadataParser;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamException;
import javax.xml.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

    public Mockery() throws StorageException, MetadataException, IOException {
        root = new File(getTargetDirectory(), "storage");
        if (root.exists()) delete(root);
        if (!root.mkdirs()) throw new RuntimeException("Unable to create directory " + root.getAbsolutePath());
        SchemaRepository repo = new SchemaRepository(new FileSystemFetcher(true));
        storage = new StorageImpl(root, repo, new CachedResourceResolver());
    }

    public void prepareDataset(String prefix, String recordRootPath, String uniqueElementPath) throws StorageException, IOException, MetadataException {
        this.prefix = prefix;
        storage.createDataSet(prefix);
        dataSetDir = new File(root, String.format("%s_%s", prefix, ORG));
        hints.clear();
        File factsSourceDir = new File(getClass().getResource(String.format("/test/%s/dataset", prefix)).getFile());
        if (!factsSourceDir.isDirectory()) throw new RuntimeException();
        FileUtils.copyDirectory(factsSourceDir, dataSetDir);
        DataSet dataSet = storage.getDataSets().get(dataSetDir.getName());
        if (dataSet == null) throw new RuntimeException("No dataset called: "+dataSetDir);
        dataSetModel.setDataSet(dataSet, prefix);
        recMapping = dataSetModel.getMappingModel().getRecMapping();
    }

    public Storage storage() {
        return storage;
    }

    public DataSetModel model() {
        return dataSetModel;
    }

    public Validator validator() throws StorageException {
        return dataSetModel.getDataSet().newValidator();
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
                -1 // todo: this should be available from the stats model or something getRecordCount()
        );
    }

    public Node runMapping(MetadataRecord record) throws MappingException {
        GroovyCodeResource resource = new GroovyCodeResource(getClass().getClassLoader());
        MappingRunner mappingRunner = new MappingRunner(resource, recMapping, null, true);
//        System.out.println(mappingRunner.getCode());
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

    public File sampleInputFile() throws IOException {
        return new File(sampleResource().getFile());
    }

    private void delete(File file) {
        FileUtils.deleteQuietly(file);
    }

    public static File getTargetDirectory() {
        File target = new File("sip-app/target");
        if (target.exists()) return target;
        target = new File("target");
        if (target.exists()) return target;
        throw new RuntimeException("target directory not found");
    }
}
