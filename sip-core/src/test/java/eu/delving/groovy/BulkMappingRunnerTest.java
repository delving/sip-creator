/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.groovy;

import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import java.util.stream.IntStream;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BulkMappingRunnerTest {

    private static Logger LOG = LoggerFactory.getLogger(BulkMappingRunnerTest.class);

    private static final String CODE_TEMPLATE = "groovy.xml.DOMBuilder.newInstance().parseText('<foo><bar>%s</bar></foo>')";
    public static final String NON_RANDOM_CODE = String.format(CODE_TEMPLATE, "bar");

    @Mock
    private RecMapping recMapping;

    @Mock
    private RecDefTree recDefTree;

    @Mock
    private RecDef recDef;

    private MetadataRecord metadataRecord;


    @Before
    public void before() {
        when(recMapping.getRecDefTree()).thenReturn(recDefTree);
        when(recDefTree.getRecDef()).thenReturn(recDef);

        metadataRecord = mock(MetadataRecord.class);
        GroovyNode rootGroovyNode = mock(GroovyNode.class);
        when(metadataRecord.getRootNode()).thenReturn(rootGroovyNode);
    }

    @Test
    public void runMapping() throws Exception {
        nInvocations(1, false);
    }

    /**
     * This mimics the same dataset being processed many times, without that dataset's mapping-code changing.
     */
    @Test
    public void testManyInvocations() throws MappingException {
        int num = 100;
        long start = System.currentTimeMillis();
        nInvocations(num, false);
        long end = System.currentTimeMillis();
        LOG.debug("processed {} records in an estimated time of {} ms", num, (end - start));

    }

    /**
     * This mimics many datasets, or datasets with their mapping-code changing all the time.
     * This should be slower, because the script is re-compiled all the time.
     *
     * Also, if we don't manage to remove the compiled script from the engine's classloader, we will eventually
     * hit the metaspace limit.
     *
     * This test demonstrates what will happen if we get too many datasets or too many datasets that have
     * had their-mapping code updated too often.
     */
    @Test
    public void testManyRandomInvocations() throws MappingException {
        nInvocations(100, true);
    }

    private void nInvocations(int n, boolean randomizeCode) {
        IntStream.range(0, n).forEach( i -> {
            if (i % 100 == 0) {
                LOG.debug("Run: {}", i);
            }
            try {
                doExecuteMapping(randomizeCode);
            } catch (MappingException e) {
                throw new RuntimeException(e);
            }

        });
    }

    private void doExecuteMapping(boolean randomizeCode) throws MappingException {
        String code = randomizeCode ? String.format(CODE_TEMPLATE, longRandom()) : NON_RANDOM_CODE;
        BulkMappingRunner runner = new BulkMappingRunner(recMapping, code);
        Node result = runner.runMapping(metadataRecord);
        assertNotNull(result);
    }

    private String longRandom(){
        String single = "" + System.currentTimeMillis();
        // now just repeat that a lot of time to increase the size of the class that will be compiled
        StringBuilder builder = new StringBuilder(single);
        IntStream.range(0, 4000).forEach( i -> builder.append(single));
        return builder.toString();
    }
}
