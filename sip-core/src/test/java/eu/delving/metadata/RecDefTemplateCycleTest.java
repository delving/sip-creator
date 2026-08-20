/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.metadata;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Templates may legitimately reference each other in a cycle (a shared elem-group can give
 * every entity a has-type -> E55_Type link, including the E55_Type template itself). Elem.resolve
 * therefore expands a template once per branch and skips it when it is already an ancestor on
 * the current path. The depth backstop remains for cycles the ancestor-skip cannot see, such
 * as elem-group self-injection, and must fail catchably instead of with StackOverflowError.
 */
class RecDefTemplateCycleTest {

    @Test
    void selfReferencingTemplateExpandsOncePerBranch() throws Exception {
        RecDefTree tree = RecDefTree.create(readRecDef("/recdef/cyclic-template-direct-recdef.xml"));
        assertNotNull(tree.getRecDefNode(Path.create("/test:root/test:thing/test:loop")),
                "The template must be expanded under its referrer");
        assertNotNull(tree.getRecDefNode(Path.create("/test:root/test:thing/test:loop/test:value")),
                "The expanded template must keep its own children");
        assertNull(tree.getRecDefNode(Path.create("/test:root/test:thing/test:loop/test:loop")),
                "A template already an ancestor on the path must not be expanded again");
    }

    @Test
    void mutuallyReferencingTemplatesExpandOncePerBranch() throws Exception {
        RecDefTree tree = RecDefTree.create(readRecDef("/recdef/cyclic-template-indirect-recdef.xml"));
        assertNotNull(tree.getRecDefNode(Path.create("/test:root/test:thing/test:ping/test:pong")),
                "The first cycle round must be expanded");
        assertNull(tree.getRecDefNode(Path.create("/test:root/test:thing/test:ping/test:pong/test:ping")),
                "The cycle must stop where the template is already an ancestor");
    }

    @Test
    void elemGroupCycleHitsDepthBackstopCatchably() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> readRecDef("/recdef/cyclic-elem-group-recdef.xml"),
                "A cyclic elem-group must fail with an exception, not StackOverflowError");
        assertTrue(e.getMessage().contains("cyclic"),
                "Error should point at a cyclic reference, was: " + e.getMessage());
    }

    @Test
    void acyclicTemplatesStillResolve() throws Exception {
        RecDefTree tree = RecDefTree.create(readRecDef("/recdef/elem-groups-recdef.xml"));
        assertNotNull(tree.getRecDefNode(Path.create("/test:root/test:thing/test:has-id/test:identifier")),
                "Legitimate template injection must keep working");
    }

    private RecDef readRecDef(String resource) {
        InputStream stream = getClass().getResourceAsStream(resource);
        assertNotNull(stream, "Test fixture must exist: " + resource);
        return RecDef.read(stream);
    }
}
