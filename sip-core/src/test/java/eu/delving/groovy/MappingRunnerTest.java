package eu.delving.groovy;

import com.google.common.collect.ImmutableMap;
import eu.delving.groovy.MappingRunner.MappingRun;
import eu.delving.metadata.EditPath;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
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

    @Mock
    private RecMapping recMapping;

    @Mock
    private RecDefTree recDefTree;

    @Mock
    private RecDef recDef;

    @Mock
    private EditPath editPath;

    @Test
    public void runMapping() throws Exception {

        when(recMapping.getRecDefTree()).thenReturn(recDefTree);
        when(recDefTree.getRecDef()).thenReturn(recDef);

        final Map<String, String> facts = ImmutableMap.of("foo", "bar");

        String code = "groovy.xml.DOMBuilder.newInstance().parseText('<foo></foo>')";
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(this.getClass().getClassLoader());

        MetadataRecord metadataRecord = mock(MetadataRecord.class);
        GroovyNode rootGroovyNode = mock(GroovyNode.class);
        when(metadataRecord.getRootNode()).thenReturn(rootGroovyNode);

        final MappingRun mappingRun = MappingRun.MappingRunBuilder.aMappingRun()
            .withCode(code)
            .withFacts(facts)
            .withGroovyCodeResource(groovyCodeResource)
            .withMetadataRecord(metadataRecord)
            .withRecDef(recDef)
            .withValueOptLookup(recMapping.getRecDefTree().getRecDef().valueOptLookup)
            .build();

        Node node = MappingRunner.runMapping(mappingRun);
        assertNotNull(node);

    }

}
