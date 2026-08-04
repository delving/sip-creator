/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.sip.frames;

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecDefTree;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.CreateModel;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.MappingCompileModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The frame content is built lazily on first open, so the CreateModel listener that pushes the
 * selected node mapping into the field compile model may not exist yet when the user selects a
 * field and then opens the Code Tweaking view - leaving the Groovy code box empty (issue #3474).
 * Opening the frame must therefore re-sync from the current CreateModel state.
 */
class FieldMappingFrameTest {

    private SipModel sipModel;
    private MappingCompileModel fieldCompileModel;
    private CreateModel createModel;
    private NodeMapping nodeMapping;

    @BeforeEach
    void setUp() throws Exception {
        sipModel = mock(SipModel.class);
        fieldCompileModel = mock(MappingCompileModel.class);
        when(fieldCompileModel.getCodeDocument()).thenReturn(new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_GROOVY));
        when(fieldCompileModel.getDocDocument()).thenReturn(new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_XML));
        when(fieldCompileModel.getOutputDocument()).thenReturn(new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_XML));
        when(sipModel.getFieldCompileModel()).thenReturn(fieldCompileModel);

        createModel = mock(CreateModel.class);
        when(sipModel.getCreateModel()).thenReturn(createModel);
        when(sipModel.getMappingModel()).thenReturn(mock(MappingModel.class));
        when(sipModel.getFeedback()).thenReturn(mock(Feedback.class));
        doAnswer(invocation -> {
            ((Work) invocation.getArgument(0)).run();
            return null;
        }).when(sipModel).exec(any(Work.class));

        InputStream stream = getClass().getResourceAsStream("/recdef/elem-groups-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/elem-groups-recdef.xml must exist");
        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecDefNode attr = tree.getRecDefNode(Path.create("/test:root/test:concept/@test:id"));
        assertNotNull(attr, "Fixture should contain /test:root/test:concept/@test:id");
        nodeMapping = NodeMapping.forConstant("someConstant");
        attr.addNodeMapping(nodeMapping);
    }

    @Test
    void openingFrameSyncsCurrentNodeMappingIntoCompileModel() {
        when(createModel.hasNodeMapping()).thenReturn(true);
        when(createModel.getNodeMapping()).thenReturn(nodeMapping);

        FieldMappingFrame frame = new FieldMappingFrame(sipModel);
        frame.init();
        frame.onOpen(true);

        verify(fieldCompileModel).setNodeMapping(nodeMapping);
    }

    @Test
    void openingFrameWithoutSelectionSyncsEmptyState() {
        when(createModel.hasNodeMapping()).thenReturn(false);

        FieldMappingFrame frame = new FieldMappingFrame(sipModel);
        frame.init();
        frame.onOpen(true);

        verify(fieldCompileModel).setNodeMapping(null);
    }

    @Test
    void closingFrameDoesNotPushNodeMapping() {
        when(createModel.hasNodeMapping()).thenReturn(true);
        when(createModel.getNodeMapping()).thenReturn(nodeMapping);

        FieldMappingFrame frame = new FieldMappingFrame(sipModel);
        frame.init();
        frame.onOpen(false);

        verify(fieldCompileModel, never()).setNodeMapping(any());
    }
}
