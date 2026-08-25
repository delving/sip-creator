package eu.delving.metadata;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class RecDefSemanticAttrsTest {

    private static final String RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="0.0.1" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="http://example.org/tst.xsd"/>
                    <namespace prefix="crm" uri="http://www.cidoc-crm.org/cidoc-crm/" schema="http://example.org/crm.xsd"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="crm:E22_Human-Made_Object" label="HumanMadeObject"
                          subclassof="crm:E19_Physical_Object"
                          equivalentClass="tst:HumanMadeObject">
                        <doc>
                            <para name="Label" lang="en">Human Made Object</para>
                            <para name="Definition" lang="nl">Doelbewust gemaakt fysiek object.</para>
                        </doc>
                        <elem tag="crm:P1_is_identified_by" subPropertyOf="crm:P1i"/>
                    </elem>
                </root>
            </record-definition>
            """;

    @Test
    public void semanticAttributesAreParsed() {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(RECDEF.getBytes(StandardCharsets.UTF_8)));
        RecDef.Elem entity = recDef.root.subelements.get(0);
        assertEquals("crm:E19_Physical_Object", entity.subclassof);
        assertEquals("tst:HumanMadeObject", entity.equivalentClass);
        RecDef.Elem property = entity.subelements.get(0);
        assertEquals("crm:P1i", property.subPropertyOf);
    }

    @Test
    public void docParaLanguageIsParsed() {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(RECDEF.getBytes(StandardCharsets.UTF_8)));
        RecDef.Elem entity = recDef.root.subelements.get(0);
        assertNotNull(entity.doc);
        RecDef.DocParagraph labelPara = entity.doc.paraList.get(0);
        assertEquals("Label", labelPara.name);
        assertEquals("en", labelPara.lang);
        assertEquals("Human Made Object", labelPara.content.trim());
    }
}
