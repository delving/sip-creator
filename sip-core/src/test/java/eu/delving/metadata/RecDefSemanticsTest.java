package eu.delving.metadata;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    // A property declared only inside a nested rangenode (an inline child elem
    // that is itself entity-like, i.e. has its own subelements) must still reach
    // the ontology -- attributed to the entity matching the NESTED elem's tag,
    // not the ancestor that contains it. Existing direct properties on both
    // entities must be unaffected (additive, not replacing).
    static final String NESTED_RANGENODE_RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="0.0.1" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                    <namespace prefix="skos" uri="http://www.w3.org/2004/02/skos/core#" schema="s"/>
                    <namespace prefix="dc" uri="http://purl.org/dc/elements/1.1/" schema="s"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="tst:Object" label="Object">
                        <elem tag="dc:identifier"/>
                        <elem tag="dc:type">
                            <elem tag="skos:Concept">
                                <elem tag="skos:prefLabel"/>
                                <elem tag="skos:altLabel"/>
                            </elem>
                        </elem>
                    </elem>
                </root>
                <templates>
                    <elem tag="skos:Concept" label="Concept">
                        <elem tag="skos:notation"/>
                    </elem>
                </templates>
            </record-definition>
            """;

    @Test
    public void nestedRangenodePropertiesAttachToNestedEntityClass() {
        RecDefSemantics s = RecDefSemantics.from(RecDef.read(
            new ByteArrayInputStream(NESTED_RANGENODE_RECDEF.getBytes(StandardCharsets.UTF_8))));

        // The nested elem itself still stays a PropertyUse of the parent, unchanged.
        RecDefSemantics.Entity object = s.entities.get("tst:Object");
        assertEquals(2, object.properties.size());
        assertEquals("dc:identifier", object.properties.get(0).tag);
        assertEquals("dc:type", object.properties.get(1).tag);

        // Concept's own direct (template) property is unaffected...
        RecDefSemantics.Entity concept = s.entities.get("skos:Concept");
        List<String> conceptPropertyTags = concept.properties.stream().map(p -> p.tag).toList();
        assertTrue(conceptPropertyTags.contains("skos:notation"));
        // ...and the nested occurrence's own subelements are additively merged in.
        assertTrue(conceptPropertyTags.contains("skos:prefLabel"));
        assertTrue(conceptPropertyTags.contains("skos:altLabel"));
        assertEquals(3, concept.properties.size());
    }

    // Two entities that nest each other (A nests B, B nests A) must not recurse
    // forever; the visited-set on entity tag caps how deep re-entry goes.
    static final String CYCLIC_NESTING_RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="0.0.1" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="tst:A" label="A">
                        <elem tag="tst:hasB">
                            <elem tag="tst:B" label="B">
                                <elem tag="tst:bOwn"/>
                                <elem tag="tst:hasA">
                                    <elem tag="tst:A">
                                        <elem tag="tst:aOwn"/>
                                        <elem tag="tst:hasB">
                                            <elem tag="tst:B">
                                                <elem tag="tst:neverReached"/>
                                            </elem>
                                        </elem>
                                    </elem>
                                </elem>
                            </elem>
                        </elem>
                    </elem>
                </root>
                <templates>
                    <elem tag="tst:B" label="B"/>
                </templates>
            </record-definition>
            """;

    @Test
    public void cyclicNestingBetweenTwoEntitiesDoesNotLoop() {
        RecDefSemantics s = RecDefSemantics.from(RecDef.read(
            new ByteArrayInputStream(CYCLIC_NESTING_RECDEF.getBytes(StandardCharsets.UTF_8))));

        RecDefSemantics.Entity a = s.entities.get("tst:A");
        RecDefSemantics.Entity b = s.entities.get("tst:B");
        List<String> aTags = a.properties.stream().map(p -> p.tag).toList();
        List<String> bTags = b.properties.stream().map(p -> p.tag).toList();

        // direct shape unaffected
        assertTrue(aTags.contains("tst:hasB"));
        // one level of re-entry into B is followed and merged...
        assertTrue(bTags.contains("tst:bOwn"));
        // ...whose own nested A is, in turn, followed and merged once...
        assertTrue(aTags.contains("tst:aOwn"));
        // ...but the second-level re-entry into B (tst:B already visited on this
        // path) is not followed, so its property never gets merged anywhere.
        assertFalse(aTags.contains("tst:neverReached"));
        assertFalse(bTags.contains("tst:neverReached"));
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

    // Legacy recdefs in the field reference prefixes that are never declared in
    // <namespaces> (the shipped edm recdef has crm:P79F... properties without a
    // crm namespace). The mapping engine tolerates that, so the semantic model
    // must silently drop those elems instead of letting every generator throw
    // "Unknown prefix" and fail the whole artifact.
    @Test
    public void undeclaredPrefixElemsAreSkippedAndGeneratorsStillRun() {
        String recdefXml = """
                <?xml version="1.0"?>
                <record-definition prefix="tst" version="0.0.1" flat="false">
                    <namespaces>
                        <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                        <namespace prefix="dc" uri="http://purl.org/dc/elements/1.1/" schema="s"/>
                    </namespaces>
                    <root tag="rdf:RDF">
                        <elem tag="tst:Thing" label="Thing">
                            <elem tag="dc:title"/>
                            <elem tag="crm:P79F.beginning_is_qualified_by"/>
                        </elem>
                        <elem tag="ghost:Entity" label="Ghost">
                            <elem tag="dc:title"/>
                        </elem>
                    </root>
                </record-definition>
                """;
        RecDef recDef = RecDef.read(new java.io.ByteArrayInputStream(
            recdefXml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        RecDefSemantics s = RecDefSemantics.from(recDef);
        assertFalse(s.entities.containsKey("ghost:Entity"));
        RecDefSemantics.Entity thing = s.entities.get("tst:Thing");
        assertEquals(1, thing.properties.size());
        assertEquals("dc:title", thing.properties.get(0).tag);
        // all three generators must produce output without throwing
        assertFalse(RdfsGenerator.generate(recDef, "TURTLE").isEmpty());
        assertFalse(ShaclGenerator.generate(recDef).isEmpty());
        assertFalse(JsonLdContextGenerator.generateContext(recDef).isEmpty());
    }
}
