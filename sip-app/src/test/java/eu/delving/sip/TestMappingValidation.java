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
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.FilterTreeModel;
import eu.delving.sip.model.SourceTreeNode;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.MetadataParser;
import eu.delving.stats.Stats;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.util.Arrays;

import static eu.delving.sip.files.DataSetState.MAPPING;
import static eu.delving.sip.files.DataSetState.SOURCED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Make sure the storage is working
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@Ignore
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

    @Test
    public void testEDM() throws Exception {
        mock.prepareDataset(
                "edm",
                "/delving-sip-source/input",
                "/delving-sip-source/input/@id"
        );
        mock.validator();
    }

    @Test
    public void testEad() throws Exception {
        mock.prepareDataset(
                "ead",
                "/delving-sip-source/input",
                "/delving-sip-source/input/@id"
        );
        String xml = runFullCycle(4);
        assertTrue("missing otherfindaid", xml.contains("otherfindaid"));
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
    public void testAbm() throws Exception {
        mock.prepareDataset(
                "abm",
                "/metadata/record/metadata/abm:record",
                "/metadata/record/metadata/abm:record/dc:identifier"
        );
        runFullCycle(42);
    }

    private String runFullCycle(int expectedRecords) throws Exception {

        int fileCount = 3;

        assertEquals(String.valueOf(Arrays.asList(mock.files())), fileCount++, mock.fileCount());

        assertEquals(SOURCED, state());
        performAnalysis();
        assertEquals(fileCount, mock.fileCount());
        Stats stats = dataSet().getStats();
        // todo: use expectedRecords!
        SourceTreeNode tree = SourceTreeNode.create(stats.fieldValueMap, dataSet().getDataSetFacts());
        assertEquals(Tag.element(Storage.SOURCE_ROOT_TAG), tree.getTag());
        assertEquals(MAPPING, state());

//        System.out.println(mock.mapping());
//        System.out.println();

        MetadataParser parser = mock.parser();
        parser.setProgressListener(PROGRESS_LISTENER);

        MetadataRecord record = parser.nextRecord();

//        System.out.println(XmlNodePrinter.toXml(record.getRootNode()));

        Node node = mock.runMapping(record);
        Source source = new DOMSource(node);
        try {
            mock.validator().validate(source);
        }
        catch (SAXException e) {
            fail(e.getMessage());
        }

        return new XmlSerializer().toXml(node, true);
    }

    private void performAnalysis() {
        AnalysisParser analysisParser = new AnalysisParser(mock.model(), 100, new AnalysisParser.Listener() {
            @Override
            public void success(Stats stats) {
                try {
                    Path recordContainer = null;
                    switch (state()) {
                        case SOURCED:
                            recordContainer = Storage.RECORD_CONTAINER;
                            break;
                        default:
                            Assert.fail("Unexpected state " + state());
                    }
                    dataSet().setStats(stats);
                    sourceTree = SourceTreeNode.create(stats.fieldValueMap, dataSet().getDataSetFacts());
                    new FilterTreeModel(sourceTree);
                    int recordCount = sourceTree.setRecordContainer(recordContainer);
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
