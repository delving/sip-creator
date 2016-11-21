package eu.delving.groovy;

import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.EditPath;
import eu.delving.metadata.OptList;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    private GroovyCodeResource groovyCodeResource;

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

        RecDefNode rootNode = mock(RecDefNode.class);
        when(recMapping.getRecDefTree()).thenReturn(recDefTree);
        when(recDefTree.getRecDef()).thenReturn(recDef);
        when(recDefTree.getRoot()).thenReturn(rootNode);

        String code = new CodeGenerator(recMapping).withEditPath(editPath).withTrace(false).toRecordMappingCode();

        MappingRunner mappingRunner = new MappingRunner(groovyCodeResource, code, recMapping.getFacts(),
            recMapping.getRecDefTree().getRecDef().valueOptLookup, recDef);

        MetadataRecord metadataRecord = mock(MetadataRecord.class);
        Node node = mappingRunner.runMapping(metadataRecord);
        assertNotNull(node);

    }

}
