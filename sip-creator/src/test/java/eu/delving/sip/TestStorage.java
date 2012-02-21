/*
 * Copyright 2011 DELVING BV
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

import eu.delving.metadata.MetadataException;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.StatsTree;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.xml.AnalysisParser;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.swing.tree.DefaultTreeModel;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;

import static eu.delving.sip.files.DataSetState.*;
import static org.junit.Assert.*;

/**
 * Make sure the storage is working
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestStorage {
    private Logger log = Logger.getLogger(getClass());
    private Mockery mock;
    private StatsTree statsTree;

    private DefaultTreeModel analysisTreeModel;

    @Before
    public void createStorage() throws StorageException, IOException, MetadataException {
        mock = new Mockery();
    }

    @After
    public void deleteStorage() {
        mock.delete();
    }

    @Test
    public void experiment() throws Exception {
        assertEquals(0, mock.fileCount());
        mock.preloadDataset();
        assertEquals(4, mock.fileCount());
        dataSet().externalToImported(mock.sampleInputFile(), null);
        assertEquals(5, mock.fileCount());
        assertEquals(IMPORTED, dataSet().getState());
        performAnalysis();
        assertEquals(6, mock.fileCount());
        assertEquals(ANALYZED_IMPORT, dataSet().getState());
        assertEquals("2", mock.hints().get(Storage.RECORD_COUNT));
        dataSet().setHints(mock.hints());
        assertEquals(7, mock.fileCount());
        assertEquals(DELIMITED, dataSet().getState());
        assertFalse(dataSet().getLatestStatistics().isSourceFormat());
        dataSet().importedToSource(null);
        assertEquals(8, mock.fileCount());
        assertEquals(SOURCED, dataSet().getState());
        performAnalysis();
        assertEquals(9, mock.fileCount());
        assertEquals(ANALYZED_SOURCE, dataSet().getState());
        Statistics statistics = dataSet().getLatestStatistics();
        assertTrue(statistics.isSourceFormat());
        StatsTree tree = statistics.createAnalysisTree();
        assertEquals(Tag.element(Storage.RECORD_TAG), tree.getRoot().getTag());

        // todo: just trying out some validation
        Validator validator = dataSet().getValidator("lido");
        assertNotNull(validator);
        Source source = new StreamSource(mock.sampleInputStream());
        try {
            validator.validate(source);
        }
        catch (SAXException e) {
            System.out.println(e.getMessage());
        }
    }

// todo =========================================
//    @Test
//    public void mapping() throws IOException, StorageException, InterruptedException {
//        RecMapping recMapping = dataSet().getRecMapping(mock.getMetadataPrefix(), mock.getRecDefModel());
//        assertEquals("Prefixes should be the same", mock.getMetadataPrefix(), recMapping.getPrefix());
//        MappingModel mappingModel = new MappingModel();
//        mappingModel.setRecMapping(recMapping);
//        mappingModel.setFact("/some/path", "value");
//        dataSet().setRecMapping(recMapping);
//        showUploadFiles("start");
//        recMapping = dataSet().getRecMapping(mock.getMetadataPrefix(), mock.getRecDefModel());
//        assertEquals("Should have held fact", "value", recMapping.getFact("/some/path"));
//        assertEquals(MAPPING, dataSet().getState());
//        assertEquals(6, mock.files().length); // mapping file added
//
//        assertEquals("Should be hints and source", 2, dataSet().getUploadFiles().size());
//        dataSet().setValidation(mock.getMetadataPrefix(), new BitSet(), recordCount);
//        assertEquals(7, mock.files().length);
//        for (int walk = 0; walk < Storage.FileType.MAPPING.getHistorySize() + 5; walk++) {
//            dataSet().getUploadFiles();
////            showUploadFiles("iteration before " + walk);
//            mappingModel.setFact("/some/path", "value" + walk);
//            dataSet().setRecMapping(mappingModel.getRecMapping());
//            dataSet().getUploadFiles();
////            showUploadFiles("iteration after " + walk);
//            if (walk < Storage.FileType.MAPPING.getHistorySize() - 1) {
//                assertEquals(8 + walk, mock.files().length);
//            }
//            else {
//                assertEquals(6 + Storage.FileType.MAPPING.getHistorySize(), mock.files().length);
//            }
//        }
//        showUploadFiles("after many settings of record mappings");
//        assertEquals(6 + Storage.FileType.MAPPING.getHistorySize(), mock.files().length);
//        assertEquals(4, dataSet().getUploadFiles().size());
//        showUploadFiles("finally");
//    }

    private void showUploadFiles(String message) throws StorageException {
        log.info("Upload: " + message);
        for (File u : dataSet().getUploadFiles()) {
            log.info("upload this: " + u.getName());
        }
        int index = 1;
        for (File f : mock.files()) {
            log.info((index++) + " " + f.getName());
        }
    }

    private void performAnalysis() {
        new AnalysisParser(dataSet(), new AnalysisParser.Listener() {
            @Override
            public void success(Statistics statistics) {
                try {
                    Path recordRoot = null;
                    switch (dataSet().getState()) {
                        case IMPORTED:
                            recordRoot = Path.create(mock.hints().get(Storage.RECORD_ROOT_PATH));
                            break;
                        case SOURCED:
                            recordRoot = Storage.RECORD_ROOT;
                            break;
                        default:
                            Assert.fail("Unexpected state " + dataSet().getState());
                    }
                    dataSet().setStatistics(statistics);
                    statsTree = statistics.createAnalysisTree();
                    analysisTreeModel = new DefaultTreeModel(statsTree.getRoot());
                    int recordCount = StatsTree.setRecordRoot(analysisTreeModel, recordRoot);
                    mock.hints().put(Storage.RECORD_COUNT, String.valueOf(recordCount));
                }
                catch (StorageException e) {
                    throw new RuntimeException("Coudn't save", e);
                }
            }

            @Override
            public void failure(String message, Exception exception) {
                throw new RuntimeException(message, exception);
            }

            @Override
            public boolean progress(long elementCount) {
                return true;
            }
        }).run();
    }

    private DataSet dataSet() {
        return mock.dataSet();
    }
}
