package eu.delving.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class JsonLdContextGeneratorTest {

    private RecDef recDef() {
        return RecDef.read(new ByteArrayInputStream(
            RecDefSemanticsTest.RECDEF.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void contextHasPrefixesAndTypedTerms() {
        JsonObject ctx = JsonParser.parseString(JsonLdContextGenerator.generateContext(recDef()))
            .getAsJsonObject().getAsJsonObject("@context");
        assertEquals("http://www.cidoc-crm.org/cidoc-crm/", ctx.get("crm").getAsString());
        JsonObject p1 = ctx.getAsJsonObject("P1_is_identified_by");
        assertEquals("http://www.cidoc-crm.org/cidoc-crm/P1_is_identified_by", p1.get("@id").getAsString());
        assertEquals("@id", p1.get("@type").getAsString());
        JsonObject date = ctx.getAsJsonObject("date");
        assertEquals("http://www.w3.org/2001/XMLSchema#date", date.get("@type").getAsString());
    }

    @Test
    public void frameTargetsRootEntitiesOnly() {
        JsonObject frame = JsonParser.parseString(JsonLdContextGenerator.generateFrame(recDef()))
            .getAsJsonObject();
        assertTrue(frame.has("@context"));
        var types = frame.getAsJsonArray("@type");
        assertEquals(1, types.size()); // E22 from root; E41 is template-only
        assertEquals("http://www.cidoc-crm.org/cidoc-crm/E22_Human-Made_Object",
            types.get(0).getAsString());
    }

    // dc:identifier is used only with uriCheck="true" (no xsdDataType, no target) in
    // the fixture -- the term must still come out resource-typed via the uriCheck path.
    @Test
    public void uriCheckAlonePropertyGetsIdType() {
        JsonObject ctx = JsonParser.parseString(JsonLdContextGenerator.generateContext(recDef()))
            .getAsJsonObject().getAsJsonObject("@context");
        JsonObject identifier = ctx.getAsJsonObject("identifier");
        assertEquals("http://purl.org/dc/elements/1.1/identifier", identifier.get("@id").getAsString());
        assertEquals("@id", identifier.get("@type").getAsString());
    }

    // Same property tag used bare under one entity (first in document order) and with
    // a target under another entity declared later: uses must be accumulated across
    // ALL entities, not decided by whichever use is encountered first -- otherwise the
    // bare first occurrence wins and the term is wrongly emitted as a plain string.
    private static final String MIXED_USE_RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="0.0.1" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                    <namespace prefix="crm" uri="http://www.cidoc-crm.org/cidoc-crm/" schema="s"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="crm:E21_Person">
                        <elem tag="crm:P2_has_type"/>
                    </elem>
                    <elem tag="crm:E22_Human-Made_Object">
                        <elem tag="crm:P2_has_type" target="crm:E55_Type"/>
                    </elem>
                </root>
                <templates>
                    <elem tag="crm:E55_Type" label="Type"/>
                </templates>
            </record-definition>
            """;

    @Test
    public void mixedUsePropertyAccumulatesAcrossEntitiesToIdType() {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(
            MIXED_USE_RECDEF.getBytes(StandardCharsets.UTF_8)));
        JsonObject ctx = JsonParser.parseString(JsonLdContextGenerator.generateContext(recDef))
            .getAsJsonObject().getAsJsonObject("@context");
        JsonObject hasType = ctx.getAsJsonObject("P2_has_type");
        assertEquals("http://www.cidoc-crm.org/cidoc-crm/P2_has_type", hasType.get("@id").getAsString());
        assertEquals("@id", hasType.get("@type").getAsString());
    }

    // rdf:type declared as a plain property elem (the legacy xsd_type="@id" attribute
    // is invisible to sip-core) must not claim the "type" term -- it is RDF/XML syntax,
    // not schema vocabulary the recdef is minting.
    private static final String RDF_TYPE_PROPERTY_RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="0.0.1" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                    <namespace prefix="crm" uri="http://www.cidoc-crm.org/cidoc-crm/" schema="s"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="crm:E22_Human-Made_Object">
                        <elem tag="rdf:type" target="crm:E55_Type"/>
                    </elem>
                </root>
                <templates>
                    <elem tag="crm:E55_Type" label="Type"/>
                </templates>
            </record-definition>
            """;

    @Test
    public void rdfTypePropertyDoesNotClaimTypeTerm() {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(
            RDF_TYPE_PROPERTY_RECDEF.getBytes(StandardCharsets.UTF_8)));
        JsonObject ctx = JsonParser.parseString(JsonLdContextGenerator.generateContext(recDef))
            .getAsJsonObject().getAsJsonObject("@context");
        assertFalse(ctx.has("type"));
    }

    // Collision ordering pin: properties claim their shared local name before entities
    // do. Here dc:title (a property) and crm:title (an entity) share the local name
    // "title" but resolve to different URIs -- the property must win the term, and
    // crm:title must stay addressable only via its full/prefixed URI.
    private static final String COLLISION_RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="0.0.1" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                    <namespace prefix="crm" uri="http://www.cidoc-crm.org/cidoc-crm/" schema="s"/>
                    <namespace prefix="dc" uri="http://purl.org/dc/elements/1.1/" schema="s"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="crm:E22_Human-Made_Object">
                        <elem tag="dc:title"/>
                    </elem>
                    <elem tag="crm:title"/>
                </root>
            </record-definition>
            """;

    @Test
    public void propertyTermBeatsEntityTermOnSharedLocalName() {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(
            COLLISION_RECDEF.getBytes(StandardCharsets.UTF_8)));
        JsonObject ctx = JsonParser.parseString(JsonLdContextGenerator.generateContext(recDef))
            .getAsJsonObject().getAsJsonObject("@context");
        // "title" resolves to the dc:title property's URI, not crm:title's -- properties
        // claim shared local names before entities do.
        assertEquals("http://purl.org/dc/elements/1.1/title", ctx.get("title").getAsString());
    }
}
