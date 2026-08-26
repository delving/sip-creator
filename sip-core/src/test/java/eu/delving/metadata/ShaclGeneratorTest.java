package eu.delving.metadata;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class ShaclGeneratorTest {

    private String shapesTtl() {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(
            RecDefSemanticsTest.RECDEF.getBytes(StandardCharsets.UTF_8)));
        return ShaclGenerator.generate(recDef);
    }

    private ValidationReport validate(String dataTtl) {
        Model shapesModel = ModelFactory.createDefaultModel();
        shapesModel.read(new StringReader(shapesTtl()), null, "TURTLE");
        Model data = ModelFactory.createDefaultModel();
        data.read(new StringReader(dataTtl), null, "TURTLE");
        return ShaclValidator.get().validate(
            Shapes.parse(shapesModel.getGraph()), data.getGraph());
    }

    // Recdef under test: dc:date required + xsd:date; dc:identifier singular + anyURI/IRI;
    // crm:P1_is_identified_by -> class crm:E41_Appellation.

    @Test
    public void conformingRecordPasses() {
        ValidationReport report = validate("""
            @prefix crm: <http://www.cidoc-crm.org/cidoc-crm/> .
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            <http://x/obj> a crm:E22_Human-Made_Object ;
                dc:date "2020-01-01"^^xsd:date ;
                dc:identifier <http://x/id/1> ;
                crm:P1_is_identified_by <http://x/app> .
            <http://x/app> a crm:E41_Appellation .
            """);
        assertTrue(report.conforms(), report.toString());
    }

    @Test
    public void missingRequiredDateFails() {
        ValidationReport report = validate("""
            @prefix crm: <http://www.cidoc-crm.org/cidoc-crm/> .
            <http://x/obj> a crm:E22_Human-Made_Object .
            """);
        assertFalse(report.conforms());
    }

    @Test
    public void doubleSingularIdentifierFails() {
        ValidationReport report = validate("""
            @prefix crm: <http://www.cidoc-crm.org/cidoc-crm/> .
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            <http://x/obj> a crm:E22_Human-Made_Object ;
                dc:date "2020-01-01"^^xsd:date ;
                dc:identifier <http://x/id/1> , <http://x/id/2> .
            """);
        assertFalse(report.conforms());
    }

    @Test
    public void undeclaredPropertyIsAllowedBecauseNotClosed() {
        ValidationReport report = validate("""
            @prefix crm: <http://www.cidoc-crm.org/cidoc-crm/> .
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix dcterms: <http://purl.org/dc/terms/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            <http://x/obj> a crm:E22_Human-Made_Object ;
                dc:date "2020-01-01"^^xsd:date ;
                dcterms:extent "10 cm" .
            """);
        assertTrue(report.conforms(), report.toString());
    }

    // Decision (26-08 meeting): a target property gets sh:class only, never
    // sh:nodeKind sh:IRI -- its value may legitimately be an inline typed
    // blank node (rdf:parseType="Resource" style), not just an IRI reference.
    // sh:nodeKind sh:IRI is now emitted ONLY for uriCheck properties.

    @Test
    public void targetPropertyAsInlineBlankNodeConforms() {
        ValidationReport report = validate("""
            @prefix crm: <http://www.cidoc-crm.org/cidoc-crm/> .
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            <http://x/obj> a crm:E22_Human-Made_Object ;
                dc:date "2020-01-01"^^xsd:date ;
                dc:identifier <http://x/id/1> ;
                crm:P1_is_identified_by [ a crm:E41_Appellation ] .
            """);
        assertTrue(report.conforms(), report.toString());
    }

    @Test
    public void targetPropertyAsPlainLiteralFailsClass() {
        ValidationReport report = validate("""
            @prefix crm: <http://www.cidoc-crm.org/cidoc-crm/> .
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            <http://x/obj> a crm:E22_Human-Made_Object ;
                dc:date "2020-01-01"^^xsd:date ;
                dc:identifier <http://x/id/1> ;
                crm:P1_is_identified_by "not an appellation" .
            """);
        assertFalse(report.conforms());
    }
}
