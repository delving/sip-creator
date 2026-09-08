/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.sip.model;

import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateModelTest {

    private RecMapping loadMapping() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/elem-groups-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/elem-groups-recdef.xml must exist");
        return RecMapping.create(RecDefTree.create(RecDef.read(stream)));
    }

    @Test
    void targetIsClearedWhenRecMappingIsReplaced() throws Exception {
        MappingModel mappingModel = new MappingModel();
        SipModel sipModel = mock(SipModel.class);
        when(sipModel.getDataSetModel()).thenReturn(mock(DataSetModel.class));
        when(sipModel.getMappingModel()).thenReturn(mappingModel);
        CreateModel createModel = new CreateModel(sipModel);

        mappingModel.setRecMapping(loadMapping());
        RecDefTreeNode target = (RecDefTreeNode) mappingModel
                .getTreePath(Path.create("/test:root/test:concept")).getLastPathComponent();
        createModel.setTarget(target);
        assertNotNull(createModel.getRecDefTreeNode(), "target should be set on the first mapping");

        mappingModel.setRecMapping(loadMapping());

        assertNull(createModel.getRecDefTreeNode(),
                "target from the discarded RecDefTreeNode tree must not survive a RecMapping swap");
    }
}
