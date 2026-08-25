package eu.delving.metadata;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class RecDefSemanticsTest {

    static final String RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="0.0.1" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                    <namespace prefix="crm" uri="http://www.cidoc-crm.org/cidoc-crm/" schema="s"/>
                    <namespace prefix="dc" uri="http://purl.org/dc/elements/1.1/" schema="s"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="crm:E22_Human-Made_Object" label="HumanMadeObject"
                          subclassof="crm:E19_Physical_Object, Appellation" equivalentClass="tst:HumanMadeObject">
                        <doc>
                            <para name="Label" lang="en">Human Made Object</para>
                            <para name="Definition" lang="nl">Doelbewust gemaakt object.</para>
                        </doc>
                        <elem tag="crm:P1_is_identified_by" target="crm:E41_Appellation"/>
                        <elem tag="dc:date" xsdDataType="xsd:date" required="true"/>
                        <elem tag="dc:identifier" uriCheck="true" singular="true"/>
                    </elem>
                </root>
                <templates>
                    <elem tag="crm:E41_Appellation" label="Appellation">
                        <elem tag="dc:title"/>
                    </elem>
                    <elem tag="crm:E22_Human-Made_Object" label="ShadowedByRoot"/>
                </templates>
            </record-definition>
            """;

    private RecDefSemantics semantics() {
        return RecDefSemantics.from(RecDef.read(
            new ByteArrayInputStream(RECDEF.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void ontologyHeaderDerivedFromNamespaces() {
        RecDefSemantics s = semantics();
        assertEquals("http://example.org/tst#", s.ontologyUri);
        assertEquals("0.0.1", s.version);
        assertEquals(java.util.List.of(
            "http://www.cidoc-crm.org/cidoc-crm/",
            "http://purl.org/dc/elements/1.1/"), s.imports);
    }

    @Test
    public void rootDeclarationWinsOverTemplate() {
        RecDefSemantics s = semantics();
        RecDefSemantics.Entity e = s.entities.get("crm:E22_Human-Made_Object");
        assertEquals(3, e.properties.size()); // the root shape, not the empty template shadow
        assertEquals("crm:E19_Physical_Object", e.subClassOf.get(0));
        assertEquals("Appellation", e.subClassOf.get(1)); // raw label, unresolved at this layer
        assertEquals("tst:HumanMadeObject", e.equivalentClass);
        assertEquals("Human Made Object", e.labels.get("en"));
        assertEquals("Doelbewust gemaakt object.", e.definitions.get("nl"));
        assertTrue(e.fromRoot); // (re)declared under root, wins over the template shadow
        assertFalse(s.entities.get("crm:E41_Appellation").fromRoot); // template-only
    }

    @Test
    public void propertyAnnotationsResolve() {
        RecDefSemantics.Entity e = semantics().entities.get("crm:E22_Human-Made_Object");
        RecDefSemantics.PropertyUse p1 = e.properties.get(0);
        assertEquals("crm:E41_Appellation", p1.target);
        assertNull(p1.dataType);
        RecDefSemantics.PropertyUse date = e.properties.get(1);
        assertEquals("xsd:date", date.dataType);
        assertEquals("1", date.minOccurs);
        assertNull(date.maxOccurs);
        RecDefSemantics.PropertyUse id = e.properties.get(2);
        assertEquals("xsd:anyURI", id.dataType);
        assertEquals("1", id.maxOccurs);
        assertEquals("0", id.minOccurs);
    }

    @Test
    public void uriForResolvesCuriesAndRejectsUnknownPrefix() {
        RecDefSemantics s = semantics();
        assertEquals("http://www.cidoc-crm.org/cidoc-crm/E22_Human-Made_Object",
            s.uriFor("crm:E22_Human-Made_Object"));
        assertEquals("http://example.org/full", s.uriFor("http://example.org/full"));
        assertThrows(IllegalArgumentException.class, () -> s.uriFor("nope:X"));
    }

    // subclassof follows the recdef's own established convention of naming the
    // parent by its `label` attribute (e.g. subclassof="Appellation"), not a
    // curie -- unlike equivalentClass/subPropertyOf/target/datatype. uriForSubClassOf
    // must still accept a plain curie too, so an author who *does* spell out a
    // full curie isn't punished for it.
    @Test
    public void uriForSubClassOfResolvesLabelViaFallbackAndStillAcceptsCuries() {
        RecDefSemantics s = semantics();
        // curie-valued subclassof (crm:E19_Physical_Object isn't itself declared
        // as an entity anywhere in the fixture -- resolution must not require that)
        assertEquals("http://www.cidoc-crm.org/cidoc-crm/E19_Physical_Object",
            s.uriForSubClassOf("crm:E19_Physical_Object"));
        // label-valued subclassof: "Appellation" is the label= of the
        // crm:E41_Appellation template -- must resolve to that entity's tag URI.
        assertEquals("http://www.cidoc-crm.org/cidoc-crm/E41_Appellation",
            s.uriForSubClassOf("Appellation"));
        // a label with no matching entity anywhere is genuinely unresolvable.
        assertThrows(IllegalArgumentException.class, () -> s.uriForSubClassOf("NoSuchLabel"));
    }
}
