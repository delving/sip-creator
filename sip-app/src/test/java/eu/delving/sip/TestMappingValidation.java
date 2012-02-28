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
import eu.delving.groovy.XmlSerializer;
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
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.swing.tree.DefaultTreeModel;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;

import static eu.delving.sip.files.DataSetState.*;
import static org.junit.Assert.*;

/**
 * Make sure the storage is working
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMappingValidation {
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

//    @Test
//    public void testLido() throws Exception {
//        mock.prepareDataset(
//                "lido",
//                "/bunch-of-chunks/chunk",
//                "/bunch-of-chunks/chunk/identi-fire"
//        );
//        runFullCycle(2);
//    }

    @Test
    public void testEse() throws Exception {
        mock.prepareDataset(
                "ese",
                "/Medialab/Record",
                "/Medialab/Record/OBS_GUID"
        );
        runFullCycle(3);
    }

//    @Test
//    public void testIcn() throws Exception {
//        mock.prepareDataset(
//                "icn",
//                "/Medialab/Record",
//                "/Medialab/Record/OBS_GUID"
//        );
//        runFullCycle(3);
//    }


    private void runFullCycle(int expectedRecords) throws Exception {
        assertEquals(4, mock.fileCount());
        dataSet().externalToImported(mock.sampleInputFile(), null);
        assertEquals(5, mock.fileCount());
        assertEquals(IMPORTED, dataSet().getState());

        performAnalysis();
        assertEquals(6, mock.fileCount());
        assertEquals(ANALYZED_IMPORT, dataSet().getState());

        assertEquals(String.valueOf(expectedRecords), mock.hints().get(Storage.RECORD_COUNT));
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

        mock.createMapping();
        System.out.println(mock.mapping());
        System.out.println();

        MetadataParser parser = mock.parser();
        MetadataRecord record = parser.nextRecord();
        Node node = mock.runMapping(record);
        System.out.println(XmlSerializer.toXml(node));

        Source source = new DOMSource(node);
        try {
            mock.validator().validate(source);
        }
        catch (SAXException e) {
            fail(e.getMessage());
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
