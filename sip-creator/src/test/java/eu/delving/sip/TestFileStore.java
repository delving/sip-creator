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
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreBase;
import eu.delving.sip.files.FileStoreException;
import eu.delving.sip.files.Statistics;
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
import java.util.Map;

import static eu.delving.sip.files.FileStore.StoreState.EMPTY;
import static eu.delving.sip.files.FileStore.StoreState.IMPORTED_PENDING_ANALYZE;
import static eu.delving.sip.files.FileStore.StoreState.IMPORTED_PENDING_CONVERT;
import static eu.delving.sip.files.FileStore.StoreState.MAPPED;
import static eu.delving.sip.files.FileStore.StoreState.SOURCED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Make sure the file store is working
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class TestFileStore {
    private Logger log = Logger.getLogger(getClass());
    private MockFileStoreFactory mock;
    private FileStore fileStore;
    private AnalysisTree analysisTree;
    private DefaultTreeModel analysisTreeModel;
    private List<AnalysisTree.Node> variables = new ArrayList<AnalysisTree.Node>();

    @Before
    public void createStore() throws FileStoreException, IOException, MetadataException {
        mock = new MockFileStoreFactory();
        fileStore = mock.getFileStore();
    }

    @After
    public void deleteStore() {
        mock.delete();
    }

    @Test
    public void createDelete() throws IOException, FileStoreException {
        assertEquals("Should be EMPTY", EMPTY, store().getState());
        assertEquals("Should be no files", 0, mock.files().length);
        store().externalToImported(MockFileStoreInput.sampleFile(), null);
        assertEquals("Should be one file", 1, mock.directories().length);
        assertEquals("Should be IMPORTED_FRESH", IMPORTED_PENDING_ANALYZE, store().getState());
        assertEquals("Should be one spec", 1, fileStore.getDataSetStores().size());
        assertEquals("Should be one file", 1, mock.files().length);
        log.info("Created " + mock.files()[0].getAbsolutePath());
        InputStream inputStream = MockFileStoreInput.sampleInputStream();
        InputStream storedStream = store().importedInput();
        int input = 0, stored;
        while (input != -1) {
            input = inputStream.read();
            stored = storedStream.read();
            assertEquals("Stream discrepancy", input, stored);
        }
        store().remove();
        assertEquals("Should be zero files", 0, mock.directories().length);
    }

    @Test
    public void cycle() throws IOException, FileStoreException {
        store().externalToImported(MockFileStoreInput.sampleFile(), null);
        store().setHints(mock.hints());
        assertEquals("Should be imported and hints", 2, mock.files().length);
        assertEquals(IMPORTED_PENDING_ANALYZE, store().getState());
        analyze();
        assertEquals("Should be imported, hints, and stats", 3, mock.files().length);
        assertFalse("Zero variables!", variables.isEmpty());
        Statistics statistics = store().getLatestStatistics();
        assertFalse("Should be analysis format", statistics.isSourceFormat());
        int statsSize = statistics.size();
        int recordCount = Integer.parseInt(store().getHints().get(FileStore.RECORD_COUNT));
        assertTrue("Zero records!", recordCount > 0);
        assertEquals(IMPORTED_PENDING_CONVERT, store().getState());
        store().importedToSource(null);
        assertEquals("Should be imported, hints, stats, and source", 4, mock.files().length);
        statistics.convertToSourcePaths(FileStoreBase.getRecordRoot(mock.hints()));
        store().setStatistics(statistics);
        assertEquals("Should be imported, hints, 2 stats, and source", 5, mock.files().length);
        assertEquals(SOURCED, store().getState());
        statistics = store().getLatestStatistics();
        assertTrue("Should be less items in analysis", statistics.size() < statsSize);
        assertTrue("Should be source format", statistics.isSourceFormat());
        AnalysisTree tree = statistics.createAnalysisTree();
        assertTrue("Should have a new form of path", tree.getRoot().getTag().equals(Tag.create(FileStore.ENVELOPE_TAG)));
        RecordMapping recordMapping = store().getRecordMapping(mock.getMetadataPrefix());
        assertEquals("Prefixes should be the same", mock.getMetadataPrefix(), recordMapping.getPrefix());
        MappingModel mappingModel = new MappingModel();
        mappingModel.setRecordMapping(recordMapping);
        mappingModel.setFact("/some/path", "value");
        store().setRecordMapping(recordMapping);
        assertEquals("Should be two files", 6, mock.files().length);
        recordMapping = store().getRecordMapping(mock.getMetadataPrefix());
        assertEquals("Should have held fact", "value", recordMapping.getFact("/some/path"));
        assertEquals(MAPPED, store().getState());
        assertEquals("Should be hints and source", 2, store().getUploadFiles().size());
        store().setValidation(mock.getMetadataPrefix(), new BitSet(), recordCount);
        assertEquals("Should be four files", 4, store().getUploadFiles().size());
        for (File u : store().getUploadFiles()) {
            log.info("upload this: "+u.getName());
        }
    }

    @Test
    public void validation() throws FileStoreException, IOException {
        int bits = 100;
        BitSet valid = new BitSet(bits);
        for (int walk=0; walk<bits; walk++) {
            if ((walk % 25) > 0) {
                valid.set(walk);
            }
        }
        assertEquals("Cardinality unexpected", 96, valid.cardinality());
        store().setValidation("bla", valid, bits);
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
        AnalysisParser parser = new AnalysisParser(store(), new AnalysisParser.Listener() {
            @Override
            public void success(Statistics statistics) {
                try {
                    store().setStatistics(statistics);
                    analysisTree = statistics.createAnalysisTree();
                    analysisTreeModel = new DefaultTreeModel(analysisTree.getRoot());
                    Path recordRoot = null;
                    switch (store().getState()) {
                        case IMPORTED_PENDING_CONVERT:
                            recordRoot = recordRoot();
                            break;
                        case SOURCED:
                            recordRoot = FileStore.RECORD_ROOT;
                            break;
                        default:
                            Assert.fail("strange state " + store().getState());
                    }
                    int recordCount = AnalysisTree.setRecordRoot(analysisTreeModel, recordRoot);
                    Map<String,String> hints = store().getHints();
                    hints.put(FileStore.RECORD_COUNT, String.valueOf(recordCount));
                    store().setHints(hints);
                    analysisTree.getVariables(variables);
                }
                catch (FileStoreException e) {
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

    private Path recordRoot() {
        Map<String,String> hints = store().getHints();
        return new Path(hints.get(FileStore.RECORD_ROOT_PATH));
    }
    
    private FileStore.DataSetStore store() {
        return mock.store();
    }
}
