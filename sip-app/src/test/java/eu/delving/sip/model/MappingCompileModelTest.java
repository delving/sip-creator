/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.sip.model;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Swing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.metadata.StringUtil.documentToString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Selection of a node mapping updates the code document through Swing jobs queued on the EDT.
 * Selections are executed by WorkModel on an unbounded pool of SILENT workers, so two selections
 * can queue their EDT jobs interleaved. These tests replay such interleavings deterministically
 * by capturing the jobs each setNodeMapping call queues and running them in a chosen order.
 */
class MappingCompileModelTest {

    private List<Swing> capture;
    private MappingCompileModel model;
    private NodeMapping nodeMapping;

    @BeforeEach
    void setUp() throws Exception {
        capture = new ArrayList<>();
        SipModel sipModel = mock(SipModel.class);
        doAnswer(invocation -> {
            capture.add(invocation.getArgument(0));
            return null;
        }).when(sipModel).exec(any(Swing.class));

        model = new MappingCompileModel(sipModel, MappingCompileModel.Type.FIELD,
                new GroovyCodeResource(getClass().getClassLoader()));

        InputStream stream = getClass().getResourceAsStream("/recdef/elem-groups-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/elem-groups-recdef.xml must exist");
        RecDefTree tree = RecDefTree.create(RecDef.read(stream));

        RecDefNode attr = tree.getRecDefNode(Path.create("/test:root/test:concept/@test:id"));
        assertNotNull(attr, "Fixture should contain /test:root/test:concept/@test:id");
        nodeMapping = NodeMapping.forConstant("'someConstant'");
        attr.addNodeMapping(nodeMapping);

        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "org");
        recMapping.setFact("spec", "spec");

        MappingModel mappingModel = mock(MappingModel.class);
        when(mappingModel.getRecMapping()).thenReturn(recMapping);
        model.getMappingModelSetListener().recMappingSet(mappingModel);
        runAll(drain()); // flush the jobs queued by recMappingSet's setNodeMapping(null)
    }

    @Test
    void selectingNodeMappingPopulatesCodeDocument() {
        model.setNodeMapping(nodeMapping);
        runAll(drain());
        assertFalse(documentToString(model.getCodeDocument()).trim().isEmpty(),
                "Selecting a node mapping should populate the Groovy code document");
    }

    @Test
    void staleClearJobsCannotEraseCodeSetByLaterSelection() {
        // A stale deselection (B) starts queueing its EDT jobs, then the real selection (A)
        // queues all of its jobs, then B's remaining jobs land. This is the interleaving the
        // unbounded SILENT worker pool allows. Whatever the interleaving, the EDT must end up
        // showing the complete result of one selection - never a torn, empty document.
        model.setNodeMapping(null);
        List<Swing> staleDeselect = drain();
        model.setNodeMapping(nodeMapping);
        List<Swing> selection = drain();

        runAll(staleDeselect.subList(0, 1));
        runAll(selection);
        runAll(staleDeselect.subList(1, staleDeselect.size()));

        assertFalse(documentToString(model.getCodeDocument()).trim().isEmpty(),
                "Stale clear jobs from an earlier deselection must not erase the code of the selection that queued after them");
    }

    @Test
    void deselectionClearsCodeDocument() {
        model.setNodeMapping(nodeMapping);
        runAll(drain());
        model.setNodeMapping(null);
        runAll(drain());
        assertTrue(documentToString(model.getCodeDocument()).trim().isEmpty(),
                "Deselecting should clear the Groovy code document");
    }

    private List<Swing> drain() {
        List<Swing> jobs = new ArrayList<>(capture);
        capture.clear();
        return jobs;
    }

    private void runAll(List<Swing> jobs) {
        // simulated EDT: run in queue order; jobs queued by these jobs run afterwards
        for (Swing job : jobs) {
            job.run();
        }
        if (!capture.isEmpty()) {
            runAll(drain());
        }
    }
}
