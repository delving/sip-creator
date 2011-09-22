/*
 * Copyright 2010 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip;

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.Tag;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageBase;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.xml.AnalysisParser;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.swing.tree.DefaultTreeModel;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static eu.delving.sip.files.DataSetState.ANALYZED_SOURCE;
import static eu.delving.sip.files.DataSetState.DELIMITED;
import static eu.delving.sip.files.DataSetState.EMPTY;
import static eu.delving.sip.files.DataSetState.IMPORTED;
import static eu.delving.sip.files.DataSetState.MAPPING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Make sure the storage is working
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class TestStorage {
    private Logger log = Logger.getLogger(getClass());
    private MockDataSetFactory mock;
    private Storage storage;
    private AnalysisTree analysisTree;
    private DefaultTreeModel analysisTreeModel;
    private int recordCount;
    private List<AnalysisTree.Node> variables = new ArrayList<AnalysisTree.Node>();

    @Before
    public void createStorage() throws StorageException, IOException, MetadataException {
        mock = new MockDataSetFactory();
        storage = mock.getStorage();
    }

    @After
    public void deleteStorage() {
        mock.delete();
    }

    @Test
    public void createDelete() throws IOException, StorageException {
        assertEquals("Should be EMPTY", EMPTY, dataSet().getState());
        assertEquals("Should be no files", 0, mock.files().length);
        dataSet().externalToImported(MockInput.sampleFile(), null);
        assertEquals("Should be one file", 1, mock.directories().length);
        assertEquals("Should be IMPORTED", IMPORTED, dataSet().getState());
        assertEquals("Should be one spec", 1, storage.getDataSets().size());
        assertEquals("Should be one file", 1, mock.files().length);
        log.info("Created " + mock.files()[0].getAbsolutePath());
        InputStream inputStream = MockInput.sampleInputStream();
        InputStream storedStream = dataSet().importedInput();
        int input = 0, stored;
        while (input != -1) {
            input = inputStream.read();
            stored = storedStream.read();
            assertEquals("Stream discrepancy", input, stored);
        }
        dataSet().remove();
        assertEquals("Should be one file", 1, mock.directories().length);
    }

    @Test
    public void cycle() throws IOException, StorageException {
        dataSet().externalToImported(MockInput.sampleFile(), null);
        assertEquals(IMPORTED, dataSet().getState());
        analyze();
        assertEquals("Should be imported and stats", 2, mock.files().length);
        assertFalse("Zero variables!", variables.isEmpty());
        dataSet().setHints(mock.hints(recordCount));
        assertEquals("Should be imported, stats and hints", 3, mock.files().length);
        assertEquals(DELIMITED, dataSet().getState());
        Statistics statistics = dataSet().getLatestStatistics();
        assertFalse("Should be analysis format", statistics.isSourceFormat());
        int statsSize = statistics.size();
        int recordCount = Integer.parseInt(dataSet().getHints().get(Storage.RECORD_COUNT));
        assertTrue("Zero records!", recordCount > 0);
        dataSet().importedToSource(null);
        assertEquals("Should be imported, hints, stats, and source", 4, mock.files().length);
        statistics.convertToSourcePaths(StorageBase.getRecordRoot(dataSet().getHints()), StorageBase.getUniqueElement(dataSet().getHints()));
        dataSet().setStatistics(statistics);
        assertEquals("Should be imported, hints, 2 stats, and source", 5, mock.files().length);
        assertEquals(ANALYZED_SOURCE, dataSet().getState());
        statistics = dataSet().getLatestStatistics();
        assertTrue("Should be less items in analysis", statistics.size() < statsSize);
        assertTrue("Should be source format", statistics.isSourceFormat());
        AnalysisTree tree = statistics.createAnalysisTree();
        assertTrue("Should have a new form of path", tree.getRoot().getTag().equals(Tag.create(Storage.ENVELOPE_TAG)));
        RecordMapping recordMapping = dataSet().getRecordMapping(mock.getMetadataPrefix(), mock.loadMetadataModel());
        assertEquals("Prefixes should be the same", mock.getMetadataPrefix(), recordMapping.getPrefix());
        MappingModel mappingModel = new MappingModel();
        mappingModel.setRecordMapping(recordMapping);
        mappingModel.setFact("/some/path", "value");
        dataSet().setRecordMapping(recordMapping);
        assertEquals("Should be two files", 6, mock.files().length);
        recordMapping = dataSet().getRecordMapping(mock.getMetadataPrefix(), mock.loadMetadataModel());
        assertEquals("Should have held fact", "value", recordMapping.getFact("/some/path"));
        assertEquals(MAPPING, dataSet().getState());
        assertEquals("Should be hints and source", 2, dataSet().getUploadFiles().size());
        dataSet().setValidation(mock.getMetadataPrefix(), new BitSet(), recordCount);
        assertEquals("Should be four files", 4, dataSet().getUploadFiles().size());
        for (File u : dataSet().getUploadFiles()) {
            log.info("upload this: "+u.getName());
        }
    }

    @Test
    public void validation() throws StorageException, IOException {
        int bits = 100;
        BitSet valid = new BitSet(bits);
        for (int walk=0; walk<bits; walk++) {
            if ((walk % 25) > 0) {
                valid.set(walk);
            }
        }
        assertEquals("Cardinality unexpected", 96, valid.cardinality());
        dataSet().setValidation("bla", valid, bits);
        assertEquals("Should have held fact", 1, mock.files().length);
        DataInputStream dis = new DataInputStream(new FileInputStream(mock.files()[0]));
        int size = dis.readInt();
        BitSet invalid = new BitSet(bits);
        for (int walk=0; walk<size; walk++) {
            int index = dis.readInt();
            invalid.set(index);
        }
        invalid.xor(valid);
        assertEquals("Should not be any clear bits in the first "+bits, bits, invalid.nextClearBit(0));
    }

    private void analyze() {
        AnalysisParser parser = new AnalysisParser(dataSet(), new AnalysisParser.Listener() {
            @Override
            public void success(Statistics statistics) {
                try {
                    dataSet().setStatistics(statistics);
                    analysisTree = statistics.createAnalysisTree();
                    analysisTreeModel = new DefaultTreeModel(analysisTree.getRoot());
                    Path recordRoot = null;
                    switch (dataSet().getState()) {
                        case ANALYZED_IMPORT:
                            recordRoot = new Path("/adlibXML/recordList/record");
                            break;
                        case SOURCED: // todo: not analyzed?
                            recordRoot = Storage.RECORD_ROOT;
                            break;
                        default:
                            Assert.fail("strange state " + dataSet().getState());
                    }
                    recordCount = AnalysisTree.setRecordRoot(analysisTreeModel, recordRoot);
                    analysisTree.getVariables(variables);
                }
                catch (StorageException e) {
                    throw new RuntimeException("Coudn't save", e);
                }
            }

            @Override
            public void failure(Exception exception) {
                throw new RuntimeException("Should not have failed", exception);
            }

            @Override
            public void progress(long elementCount) {
            }
        });
        parser.run();
    }

    private DataSet dataSet() {
        return mock.dataSet();
    }
}
