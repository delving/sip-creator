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
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import eu.delving.sip.xml.AnalysisParser;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static eu.delving.sip.files.FileStore.StoreState.EMPTY;
import static eu.delving.sip.files.FileStore.StoreState.IMPORTED_PENDING_ANALYZE;
import static eu.delving.sip.files.FileStore.StoreState.IMPORTED_PENDING_CONVERT;
import static eu.delving.sip.files.FileStore.StoreState.SOURCED_PENDING_ANALYZE;
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
        assertEquals("Should be EMPTY", EMPTY, mock.store().getState());
        assertEquals("Should be no files", 0, mock.files().length);
        mock.store().externalToImported(MockFileStoreInput.sampleFile(), null);
        assertEquals("Should be one file", 1, mock.directories().length);
        assertEquals("Should be IMPORTED_FRESH", IMPORTED_PENDING_ANALYZE, mock.store().getState());
        assertEquals("Should be one spec", 1, fileStore.getDataSetStores().size());
        assertEquals("Should be one file", 1, mock.files().length);
        log.info("Created " + mock.files()[0].getAbsolutePath());
        InputStream inputStream = MockFileStoreInput.sampleInputStream();
        InputStream storedStream = mock.store().importedInput();
        int input = 0, stored;
        while (input != -1) {
            input = inputStream.read();
            stored = storedStream.read();
            assertEquals("Stream discrepancy", input, stored);
        }
        mock.store().remove();
        assertEquals("Should be zero files", 0, mock.directories().length);
    }

    @Test
    public void convert() throws IOException, FileStoreException {
        mock.store().externalToImported(MockFileStoreInput.sampleFile(), null);
        mock.store().setHints(mock.hints());
        assertEquals("Should be imported and hints", 2, mock.files().length);
        assertEquals(IMPORTED_PENDING_ANALYZE, mock.store().getState());
        analyze();
        assertEquals("Should be imported, hints, and stats", 3, mock.files().length);
        assertFalse("Zero variables!", variables.isEmpty());
        int recordCount = Integer.parseInt(mock.store().getHints().get(FileStore.RECORD_COUNT));
        assertTrue("Zero records!", recordCount > 0);
        assertEquals(IMPORTED_PENDING_CONVERT, mock.store().getState());
        mock.store().importedToSource(null);
        assertEquals("Should be imported, hints, and source", 3, mock.files().length);
        assertEquals(SOURCED_PENDING_ANALYZE, mock.store().getState());
        analyze();
        assertEquals("Should be imported, hints, source, stats", 4, mock.files().length);
// todo: this still fails:
//        int newRecordCount = Integer.parseInt(mock.store().getHints().get(FileStore.RECORD_COUNT));
//        assertEquals("Record counts different", recordCount, newRecordCount);
        assertFalse("Zero variables!", variables.isEmpty());
    }

    @Test
    public void manipulateMapping() throws IOException, FileStoreException, MetadataException {
        mock.store().externalToImported(MockFileStoreInput.sampleFile(), null);
        assertEquals("Spec should be the same", MockFileStoreFactory.SPEC, mock.store().getSpec());
        RecordMapping recordMapping = mock.store().getRecordMapping(mock.getMetadataPrefix());
        assertEquals("Prefixes should be the same", mock.getMetadataPrefix(), recordMapping.getPrefix());
        log.info("Mapping created with prefix " + recordMapping.getPrefix());
        MappingModel mappingModel = new MappingModel();
        mappingModel.setRecordMapping(recordMapping);
        mappingModel.setFact("/some/path", "value");
        mock.store().setRecordMapping(recordMapping);
        assertEquals("Should be two files", 2, mock.files().length);
        recordMapping = mock.store().getRecordMapping(mock.getMetadataPrefix());
        assertEquals("Should have held fact", "value", recordMapping.getFact("/some/path"));
    }

    @Test
    public void manipulateStatistics() throws IOException, FileStoreException {
        mock.store().externalToImported(MockFileStoreInput.sampleFile(), null);
        Assert.assertNull("No stats should be here", mock.store().getStatistics());
        assertEquals("Should be one files", 1, mock.files().length);
        mock.store().setStatistics(mock.stats());
        assertEquals("Should be two files ", 2, mock.files().length);
        assertEquals("Should be one stat", 1, mock.store().getStatistics().size());
    }

    private void analyze() {
        AnalysisParser parser = new AnalysisParser(mock.store(), new AnalysisParser.Listener() {
            @Override
            public void success(final List<FieldStatistics> list) {
                log.info("stats are in!");
                try {
                    mock.store().setStatistics(list);
                    analysisTree = AnalysisTree.create(list);
                    analysisTreeModel = new DefaultTreeModel(analysisTree.getRoot());
                    Path recordRoot = null;
                    switch (mock.store().getState()) {
                        case IMPORTED_PENDING_CONVERT:
                            recordRoot = recordRoot();
                            break;
                        case SOURCED_UNMAPPED:
                            recordRoot = new Path("/delving-sip-source/input");
                            break;
                        default:
                            Assert.fail("strange state " + mock.store().getState());
                    }
                    int recordCount = AnalysisTree.setRecordRoot(analysisTreeModel, recordRoot);
                    Map<String,String> hints = mock.store().getHints();
                    hints.put(FileStore.RECORD_COUNT, String.valueOf(recordCount));
                    mock.store().setHints(hints);
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
        Map<String,String> hints = mock.store().getHints();
        return new Path(hints.get(FileStore.RECORD_ROOT_PATH));
    }
}
