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
    private final String NON_RANDOM_CODE = String.format(CODE_TEMPLATE, "bar");

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
    public void testHundredsOfInvocations() throws MappingException {
        nInvocations(900, false);
    }

    /**
     * This mimics many datasets, or datasets with their mapping-code changing all the time.
     * This should be slower, because the script is re-compiled all the time.
     *
     * Also, if we don't manage to remove the compiled script from the engine's classloader, we will eventually
     * hit the metaspace limit.
     */
    @Test
    public void testHundredsOfRandomInvocations() throws MappingException {
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
