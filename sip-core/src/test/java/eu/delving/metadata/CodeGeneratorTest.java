/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.metadata;

import eu.delving.groovy.StandardMappingFunctions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGeneratorTest {

    @Test
    void generatedCodeExposesInternalRecordUriHelper() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/elem-groups-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/elem-groups-recdef.xml must exist");

        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "datahub");
        recMapping.setFact("spec", "brocade-cat-lh");

        String code = new CodeGenerator(recMapping).toRecordMappingCode();

        assertTrue(code.contains(
                "def internalRecordURI = { -> \"urn:${orgId}_${spec}_${_uniqueIdentifier.sanitizeURN()}/graph\" }"),
            "Generated mapping code should expose a stable internal record URI helper using URN-safe sanitization");
        assertTrue(code.contains("def internalRecordURN = internalRecordURI"),
            "Generated mapping code should expose a URN alias for the helper");
    }

    @Test
    void internalRecordUriHelperIsListedAsStandardFunction() {
        MappingFunction function = StandardMappingFunctions.asList().stream()
            .filter(candidate -> "internalRecordURI".equals(candidate.name))
            .findFirst()
            .orElse(null);

        assertNotNull(function, "internalRecordURI should be shown in the standard functions list");
        assertTrue(function.toString().equals("internalRecordURI()"),
            "internalRecordURI should be shown as a zero-argument function");
    }

    @Test
    void constantMappingCanCallInternalRecordUriHelper() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/elem-groups-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/elem-groups-recdef.xml must exist");

        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecDefNode attr = tree.getRecDefNode(Path.create("/test:root/test:concept/@test:id"));
        assertNotNull(attr, "Test recdef should contain /test:root/test:concept/@test:id");
        attr.addNodeMapping(NodeMapping.forConstant("internalRecordURI()"));

        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "datahub");
        recMapping.setFact("spec", "brocade-cat-lh");

        String code = new CodeGenerator(recMapping).toRecordMappingCode();

        assertTrue(code.contains("internalRecordURI()"),
            "Constant mapping should emit a helper call");
        assertTrue(!code.contains("'internalRecordURI()'"),
            "Constant mapping should not quote the helper call");
    }

    @Test
    void autoInjectsRdfAboutOnFirstPopulatedTopLevelResource() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/rdf-root-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/rdf-root-recdef.xml must exist");

        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecDefNode title = tree.getRecDefNode(
            Path.create("/test:RDF/lrm:F3_Manifestation/crm:P102_has_title"));
        assertNotNull(title, "Fixture must contain lrm:F3_Manifestation/crm:P102_has_title");
        title.addNodeMapping(NodeMapping.forConstant("Sample title"));

        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "datahub");
        recMapping.setFact("spec", "brocade-cat-lh");

        String code = new CodeGenerator(recMapping).toRecordMappingCode();

        assertTrue(code.contains("'rdf:about' : {"),
            "Generated code should open a builder entry for rdf:about");
        assertTrue(code.contains("internalRecordURI()"),
            "Auto-injected rdf:about value should be internalRecordURI()");
    }

    @Test
    void doesNotAutoInjectWhenUserSuppliesRdfAbout() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/rdf-root-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/rdf-root-recdef.xml must exist");

        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecDefNode title = tree.getRecDefNode(
            Path.create("/test:RDF/lrm:F3_Manifestation/crm:P102_has_title"));
        assertNotNull(title);
        title.addNodeMapping(NodeMapping.forConstant("Sample title"));

        RecDefNode about = tree.getRecDefNode(
            Path.create("/test:RDF/lrm:F3_Manifestation/@rdf:about"));
        assertNotNull(about, "Fixture must declare rdf:about on lrm:F3_Manifestation");
        about.addNodeMapping(NodeMapping.forConstant("http://example.org/user-supplied"));

        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "datahub");
        recMapping.setFact("spec", "brocade-cat-lh");

        String code = new CodeGenerator(recMapping).toRecordMappingCode();

        assertTrue(code.contains("http://example.org/user-supplied"),
            "Generated code should contain the user-authored rdf:about value");

        // The intent: auto-inject must not fire when the user has supplied rdf:about.
        // We can't easily assert exact spacing because of CodeOut indentation. Instead,
        // assert that the only internalRecordURI() reference in the generated code is the
        // closure-definition line itself (which is always emitted), not an extra builder
        // attribute entry.
        int helperReferences = countSubstring(code, "internalRecordURI()");
        assertTrue(helperReferences == 1,
            "internalRecordURI() should appear only once (in its closure definition), not as an auto-injected attribute. Found: " + helperReferences);
    }

    @Test
    void doesNotAutoInjectWhenRecDefHasNoMappings() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/rdf-root-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/rdf-root-recdef.xml must exist");

        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "datahub");
        recMapping.setFact("spec", "brocade-cat-lh");

        String code = new CodeGenerator(recMapping).toRecordMappingCode();

        // No populated top-level child means no resource is being emitted at all,
        // so the auto-inject path must stay off.
        assertTrue(!code.contains("'rdf:about' : {"),
            "Auto-inject must not fire when nothing under the root is populated");
    }

    private static int countSubstring(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
