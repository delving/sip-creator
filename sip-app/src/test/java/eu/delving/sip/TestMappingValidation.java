/*
 * Copyright 2011, 2012 Delving BV
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
import eu.delving.sip.base.Work;
import eu.delving.sip.files.*;
import eu.delving.sip.model.FilterTreeModel;
import eu.delving.sip.model.SourceTreeNode;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.MetadataParser;
import eu.delving.sip.xml.SourceConverter;
import eu.delving.stats.Stats;
import org.apache.log4j.Logger;
import org.junit.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.util.Arrays;

import static eu.delving.sip.files.DataSetState.*;
import static org.junit.Assert.*;

/**
 * Make sure the storage is working
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMappingValidation {
    private static final MockProgressListener PROGRESS_LISTENER = new MockProgressListener();
    private Logger log = Logger.getLogger(getClass());
    private Mockery mock;
    private SourceTreeNode sourceTree;

    @Before
    public void createStorage() throws StorageException, IOException, MetadataException {
        mock = new Mockery();
    }

    @After
    public void deleteStorage() {
        mock.delete();
    }

    @Ignore
    @Test
    public void testEad() throws Exception {
        mock.prepareDataset(
                "ead",
                "/sample/eag",
                "/sample/eag/eagheader/eagid"
        );
        runFullCycle(1);
    }

    @Test
    public void testMods() throws Exception {
        mock.prepareDataset(
                "mods",
                "/example-dublin-core/copied",
                "/example-dublin-core/copied/dc:identifier"
        );
        runFullCycle(1);
    }

    @Ignore
    @Test
    public void testAbc() throws Exception {
        mock.prepareDataset(
                "abc",
                "/recordList/record",
                "/recordList/record/priref"
        );
        runFullCycle(4);
    }

    @Test
    public void testLido() throws Exception {
        mock.prepareDataset(
                "lido",
                "/bunch-of-chunks/chunk",
                "/bunch-of-chunks/chunk/identi-fire"
        );
        runFullCycle(2);
    }

    @Test
    public void testEse() throws Exception {
        mock.prepareDataset(
                "ese",
                "/harvest/OAI-PMH/ListRecords/record",
                "/harvest/OAI-PMH/ListRecords/record/header/identifier"
        );
        runFullCycle(6);
    }

//    @Ignore
    @Test
    public void testIcn() throws Exception {
        mock.prepareDataset(
                "icn",
                "/delving-sip-source/input",
                "/delving-sip-source/input/@id"
                );
        runFullCycle(15);
    }

    @Test
    public void testTib() throws Exception {
        mock.prepareDataset(
                "tib",
                "/recordList/record",
                "/recordList/record/priref"
        );
        runFullCycle(4);
    }

    @Test
    public void testAff() throws Exception {
        mock.prepareDataset(
                "aff",
                "/recordList/record",
                "/recordList/record/object_number"
        );
        runFullCycle(4);
    }

    @Test
    public void testAbm() throws Exception {
        mock.prepareDataset(
                "abm",
                "/metadata/record/metadata/abm:record",
                "/metadata/record/metadata/abm:record/dc:identifier"
        );
        runFullCycle(42);
    }

    private void runFullCycle(int expectedRecords) throws Exception {
        assertEquals(String.valueOf(Arrays.asList(mock.files())), 2, mock.fileCount());

        Work.LongTermWork importer = new FileImporter(mock.sampleInputFile(), dataSet(), null);
        importer.setProgressListener(PROGRESS_LISTENER);
        importer.run();

        assertEquals(3, mock.fileCount());
        assertEquals(IMPORTED, state());

        performAnalysis();
        assertEquals(4, mock.fileCount());
        assertEquals(ANALYZED_IMPORT, state());

        assertEquals(String.valueOf(expectedRecords), mock.hints().get(Storage.RECORD_COUNT));
        dataSet().setHints(mock.hints());
        assertEquals(5, mock.fileCount());
        assertEquals(DELIMITED, state());

        assertFalse(dataSet().getLatestStats().sourceFormat);
        Work.LongTermWork sourceImporter = new SourceConverter(dataSet(), null);
        sourceImporter.setProgressListener(PROGRESS_LISTENER);
        sourceImporter.run();
        assertEquals(6, mock.fileCount());
        assertEquals(SOURCED, state());

        performAnalysis();
        assertEquals(7, mock.fileCount());
        Stats stats = dataSet().getLatestStats();
        assertTrue(stats.sourceFormat);
        SourceTreeNode tree = SourceTreeNode.create(stats.fieldValueMap, dataSet().getDataSetFacts());
        assertEquals(Tag.element(Storage.ENVELOPE_TAG), tree.getTag());
        assertEquals(MAPPING, state());

//        System.out.println(mock.mapping());
//        System.out.println();

        MetadataParser parser = mock.parser();
        parser.setProgressListener(PROGRESS_LISTENER);

        MetadataRecord record = parser.nextRecord();

//        System.out.println(XmlNodePrinter.toXml(record.getRootNode()));

        Node node = mock.runMapping(record);

        System.out.println(new XmlSerializer().toXml(node, true));

        Source source = new DOMSource(node);
        try {
            mock.validator().validate(source);
        }
        catch (SAXException e) {
            fail(e.getMessage());
        }
    }

    private void performAnalysis() {
        AnalysisParser analysisParser = new AnalysisParser(mock.model(), 100, new AnalysisParser.Listener() {
            @Override
            public void success(Stats stats) {
                try {
                    Path recordRoot = null;
                    switch (state()) {
                        case IMPORTED:
                            recordRoot = Path.create(mock.hints().get(Storage.RECORD_ROOT_PATH));
                            break;
                        case SOURCED:
                            recordRoot = Storage.RECORD_ROOT;
                            break;
                        default:
                            Assert.fail("Unexpected state " + state());
                    }
                    dataSet().setStats(stats, stats.sourceFormat, null);
                    sourceTree = SourceTreeNode.create(stats.fieldValueMap, dataSet().getDataSetFacts());
                    new FilterTreeModel(sourceTree);
                    int recordCount = sourceTree.setRecordRoot(recordRoot);
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
        });
        analysisParser.setProgressListener(PROGRESS_LISTENER);
        analysisParser.run();
    }

    private DataSet dataSet() {
        return mock.model().getDataSet();
    }

    private DataSetState state() {
        return mock.model().getDataSetState();
    }

}
