/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.frames;

import eu.delving.metadata.MappingModel;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;

/**
 * This frame shows the entire builder that is responsible for transforming the input to output,
 * as well as the constants and dictionaries that it may have.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CodeFrame extends FrameBase {
    private static int MARG = 30;
    private JTextArea codeArea = new JTextArea();

    public CodeFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Mapping Code", false);
        codeArea.setFont(new Font("Monospaced", Font.BOLD, 14));
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                refresh();
            }

            @Override
            public void factChanged(MappingModel mappingModel) {
                refresh();
            }

            @Override
            public void nodeMappingSelected(MappingModel mappingModel) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                refresh();
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                refresh();
            }
        });
    }

    @Override
    protected void buildContent(Container content) {
        content.add(scroll(codeArea), BorderLayout.CENTER);
        setPlacement(new Placement() {
            @Override
            public Point getLocation() {
                return new Point(MARG, MARG);
            }

            @Override
            public Dimension getSize() {
                return new Dimension(desktopPane.getSize().width - MARG * 2, desktopPane.getSize().height - MARG * 2);
            }
        });
    }

    @Override
    protected void refresh() {
        Exec.swingAny(new CodeUpdater());
    }

    private class CodeUpdater implements Runnable {

        @Override
        public void run() {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                String code = recMapping.toCode(null, null);
                codeArea.setText(code);
            }
            else {
                codeArea.setText("// No code");
            }
        }
    }

}
