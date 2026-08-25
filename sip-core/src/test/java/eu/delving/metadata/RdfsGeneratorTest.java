package eu.delving.metadata;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class RdfsGeneratorTest {

    private Model generateModel() {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(
            RecDefSemanticsTest.RECDEF.getBytes(StandardCharsets.UTF_8)));
        String rdfXml = RdfsGenerator.generate(recDef, "RDF/XML-ABBREV");
        Model model = ModelFactory.createDefaultModel();
        model.read(new StringReader(rdfXml), null, "RDF/XML");
        return model;
    }

    @Test
    public void classesWithLabelsAndHierarchy() {
        Model m = generateModel();
        Resource e22 = m.getResource("http://www.cidoc-crm.org/cidoc-crm/E22_Human-Made_Object");
        assertTrue(m.contains(e22, RDF.type, OWL.Class));
        assertTrue(m.contains(e22, RDFS.subClassOf,
            m.getResource("http://www.cidoc-crm.org/cidoc-crm/E19_Physical_Object")));
        assertTrue(m.contains(e22, OWL.equivalentClass,
            m.getResource("http://example.org/tst#HumanMadeObject")));
        assertEquals("Human Made Object",
            e22.getProperty(RDFS.label, "en").getString());
    }

    @Test
    public void objectAndDatatypePropertiesWithDomainRange() {
        Model m = generateModel();
        Resource p1 = m.getResource("http://www.cidoc-crm.org/cidoc-crm/P1_is_identified_by");
        assertTrue(m.contains(p1, RDF.type, OWL.ObjectProperty));
        assertTrue(m.contains(p1, RDFS.domain,
            m.getResource("http://www.cidoc-crm.org/cidoc-crm/E22_Human-Made_Object")));
        assertTrue(m.contains(p1, RDFS.range,
            m.getResource("http://www.cidoc-crm.org/cidoc-crm/E41_Appellation")));
        Resource date = m.getResource("http://purl.org/dc/elements/1.1/date");
        assertTrue(m.contains(date, RDF.type, OWL.DatatypeProperty));
        assertTrue(m.contains(date, RDFS.range,
            m.getResource("http://www.w3.org/2001/XMLSchema#date")));
    }

    @Test
    public void ontologyHeader() {
        Model m = generateModel();
        Resource ont = m.getResource("http://example.org/tst#");
        assertTrue(m.contains(ont, RDF.type, OWL.Ontology));
        assertTrue(m.contains(ont, OWL.imports,
            m.getResource("http://www.cidoc-crm.org/cidoc-crm/")));
    }

    @Test
    public void turtleOutputParses() {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(
            RecDefSemanticsTest.RECDEF.getBytes(StandardCharsets.UTF_8)));
        String ttl = RdfsGenerator.generate(recDef, "TURTLE");
        Model model = ModelFactory.createDefaultModel();
        model.read(new StringReader(ttl), null, "TURTLE");
        assertFalse(model.isEmpty());
    }
}
