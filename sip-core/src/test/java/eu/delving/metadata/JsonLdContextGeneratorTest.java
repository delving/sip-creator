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
}
