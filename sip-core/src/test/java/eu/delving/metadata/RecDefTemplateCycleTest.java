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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Record definitions are external input: a template whose target (indirectly) contains itself
 * would make Elem.resolve inject deep copies of itself forever, killing the JVM with
 * StackOverflowError. The depth guard must turn that into a normal, catchable exception
 * that names the offending path.
 */
class RecDefTemplateCycleTest {

    @Test
    void directTemplateCycleFailsWithCatchableException() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> readRecDef("/recdef/cyclic-template-direct-recdef.xml"),
                "A self-referencing template must fail with an exception, not StackOverflowError");
        assertTrue(e.getMessage().contains("cyclic"),
                "Error should point at a cyclic template reference, was: " + e.getMessage());
        assertTrue(e.getMessage().contains("loop"),
                "Error path should show the offending template tag, was: " + e.getMessage());
    }

    @Test
    void indirectTemplateCycleFailsWithCatchableException() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> readRecDef("/recdef/cyclic-template-indirect-recdef.xml"),
                "Mutually referencing templates must fail with an exception, not StackOverflowError");
        assertTrue(e.getMessage().contains("cyclic"),
                "Error should point at a cyclic template reference, was: " + e.getMessage());
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
