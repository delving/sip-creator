/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.metadata;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecMappingValidateMappingsTest {

    @Test
    void missingFlagFollowsTheSourceTreeOnEveryValidation() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/elem-groups-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/elem-groups-recdef.xml must exist");
        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecDefNode node = tree.getRecDefNode(Path.create("/test:root/test:concept/skos:prefLabel"));
        assertNotNull(node);
        NodeMapping nodeMapping = new NodeMapping().setInputPath(Path.create("/input/foo")).setOutputPath(node.getPath());
        node.addNodeMapping(nodeMapping);
        RecMapping recMapping = RecMapping.create(tree);

        recMapping.validateMappings(nm -> false); // source path gone
        assertTrue(nodeMapping.inputPathMissing, "mapping should be flagged when its input path is absent");
        assertTrue(node.inputPathMissing, "target node should be flagged too");

        recMapping.validateMappings(nm -> true); // source path back, or mapping repointed
        assertFalse(nodeMapping.inputPathMissing, "flag must clear once the input path resolves again");
        assertFalse(node.inputPathMissing, "node flag must clear too");
    }
}
