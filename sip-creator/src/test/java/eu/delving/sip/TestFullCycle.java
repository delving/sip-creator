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

import eu.delving.groovy.MetadataRecord;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.StatsTree;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.MetadataParser;
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
import java.io.StringReader;

import static eu.delving.sip.files.DataSetState.*;
import static org.junit.Assert.*;

/**
 * Make sure the storage is working
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestFullCycle {
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
        Statistics statistics = dataSet().getLatestStatistics();
        assertTrue(statistics.isSourceFormat());
        StatsTree tree = statistics.createAnalysisTree();
        assertEquals(Tag.element(Storage.RECORD_TAG), tree.getRoot().getTag());
        assertEquals(ANALYZED_SOURCE, dataSet().getState());

        buildMapping();
        System.out.println(mock.mapping());
        System.out.println();

        MetadataParser parser = mock.parser();
        MetadataRecord record = parser.nextRecord();
        assertNotNull(parser.nextRecord());
        assertNull(parser.nextRecord());

        String lido = mock.runMapping(record);

        System.out.println(lido);

        Validator validator = dataSet().getValidator("lido");
        Source source = new StreamSource(new StringReader(lido));
        try {
            validator.validate(source);
        }
        catch (SAXException e) {
            fail(e.getMessage());
        }
    }

    private void buildMapping() {
        mock.map(
                "/input/identi-fire",
                "/lido/lidoRecID"
        );
        mock.map(
                "/input/identi-fire/@type",
                "/lido/lidoRecID/@type"
        );
        mock.map(
                "/input/story",
                "/lido/descriptiveMetadata"
        );
        mock.map(
                "/input/story/@xml:lang",
                "/lido/descriptiveMetadata/@xml:lang"
        );
//        mock.map(
//                "/input/story/objectClassificationWrap",
//                "/lido/descriptiveMetadata/objectClassificationWrap"
//        );
//        mock.map(
//                "/input/story/objectClassificationWrap/objectWorkTypeWrap",
//                "/lido/descriptiveMetadata/objectClassificationWrap/objectWorkTypeWrap"
//        );
        mock.map(
                "/input/story/objectClassificationWrap/objectWorkTypeWrap/objectWorkType",
                "/lido/descriptiveMetadata/objectClassificationWrap/objectWorkTypeWrap/objectWorkType"
        );
//        mock.map(
//                "/input/story/objectIdentificationWrap",
//                "/lido/descriptiveMetadata/objectIdentificationWrap"
//        );
//        mock.map(
//                "/input/story/objectIdentificationWrap/titleWrap",
//                "/lido/descriptiveMetadata/objectIdentificationWrap/titleWrap"
//        );
//        mock.map(
//                "/input/story/objectIdentificationWrap/titleWrap/titleSet",
//                "/lido/descriptiveMetadata/objectIdentificationWrap/titleWrap/titleSet"
//        );
        mock.map(
                "/input/story/objectIdentificationWrap/titleWrap/titleSet/appellationValue",
                "/lido/descriptiveMetadata/objectIdentificationWrap/titleWrap/titleSet/appellationValue"
        );
        mock.map(
                "/input/sticker",
                "/lido/administrativeMetadata"
        );
        mock.map(
                "/input/sticker/@xml:lang",
                "/lido/administrativeMetadata/@xml:lang"
        );
        mock.map(
                "/input/sticker/recordWrap",
                "/lido/administrativeMetadata/recordWrap"
        );
        mock.map(
                "/input/sticker/recordWrap/recordID",
                "/lido/administrativeMetadata/recordWrap/recordID"
        );
        mock.map(
                "/input/sticker/recordWrap/recordID/@type",
                "/lido/administrativeMetadata/recordWrap/recordID/@type"
        );
//        mock.map(
//                "/input/sticker/recordWrap/recordSource",
//                "/lido/administrativeMetadata/recordWrap/recordSource"
//        );
//        mock.map(
//                "/input/sticker/recordWrap/recordSource/legalBodyName",
//                "/lido/administrativeMetadata/recordWrap/recordSource/legalBodyName"
//        );
        mock.map(
                "/input/sticker/recordWrap/recordSource/legalBodyName/appellationValue",
                "/lido/administrativeMetadata/recordWrap/recordSource/legalBodyName/appellationValue"
        );
//        mock.map(
//                "/input/sticker/recordWrap/recordType",
//                "/lido/administrativeMetadata/recordWrap/recordType"
//        );
        mock.map(
                "/input/sticker/recordWrap/recordType/term",
                "/lido/administrativeMetadata/recordWrap/recordType/term"
        );
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
        return mock.model().getDataSet();
    }
}
