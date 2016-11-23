package eu.delving.groovy;

import com.google.common.collect.ImmutableMap;
import eu.delving.groovy.MappingRunner.MappingRun;
import eu.delving.metadata.EditPath;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test aimed at isolating and reproducing a memory-leak. Generated Groovy-classes are loaded into the JVM's metaspace.
 * They are however, never unloaded and so the new incarnation of the good-old PermGenError occurs.
 */
@RunWith(MockitoJUnitRunner.class)
public class MappingRunnerTest {

    private static Logger LOG = LoggerFactory.getLogger(MappingRunnerTest.class);

    @Mock
    private RecMapping recMapping;

    @Mock
    private RecDefTree recDefTree;

    @Mock
    private RecDef recDef;

    @Mock
    private EditPath editPath;

    private static final String CODE = "groovy.xml.DOMBuilder.newInstance().parseText('<foo></foo>')";

    private static final Map<String, String> FACTS = ImmutableMap.of("foo", "bar");

    private GroovyCodeResource groovyCodeResource;

    private MetadataRecord metadataRecord;

    @Before
    public void before() {
        when(recMapping.getRecDefTree()).thenReturn(recDefTree);
        when(recDefTree.getRecDef()).thenReturn(recDef);

        groovyCodeResource = new GroovyCodeResource(this.getClass().getClassLoader());
        metadataRecord = mock(MetadataRecord.class);
        GroovyNode rootGroovyNode = mock(GroovyNode.class);
        when(metadataRecord.getRootNode()).thenReturn(rootGroovyNode);

    }

    @Test
    public void testRunSingleMapping() throws Exception {

        final MappingRun mappingRun = MappingRun.MappingRunBuilder.aMappingRun()
            .withCode(CODE)
            .withFacts(FACTS)
            .withGroovyCodeResource(groovyCodeResource)
            .withMetadataRecord(metadataRecord)
            .withRecDef(recDef)
            .withValueOptLookup(recMapping.getRecDefTree().getRecDef().valueOptLookup)
            .withRecDefTree(recMapping.getRecDefTree())
            .build();

        Node node = doRun(mappingRun);
        assertNotNull(node);

    }

    @Test
    public void testRunThousands() throws Exception{
        for (int i = 0; i < 2000; i++) {
            final MappingRun mappingRun = MappingRun.MappingRunBuilder.aMappingRun()
                .withCode(CODE)
                .withFacts(FACTS)
                .withGroovyCodeResource(new GroovyCodeResource(this.getClass().getClassLoader()))
                .withMetadataRecord(metadataRecord)
                .withRecDef(recDef)
                .withValueOptLookup(recMapping.getRecDefTree().getRecDef().valueOptLookup)
                .withRecDefTree(recMapping.getRecDefTree())
                .build();

            Node node = doRun(mappingRun);
            assertNotNull(node);
            if (i % 100 == 0) {
                LOG.debug("Performed {} mappings", i);
            }

        }
    }

    private Node doRun(final MappingRun mappingRun) throws MappingException {
        return MappingRunner.runMapping(mappingRun);
    }

}
