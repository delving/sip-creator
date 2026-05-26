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
}
