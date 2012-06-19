/*
 * Copyright 2011, 2012 Delving BV
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

import eu.delving.metadata.*;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

import static eu.delving.metadata.NodeMappingChange.DOCUMENTATION;
import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * This frame shows the entire builder that is responsible for transforming the input to output,
 * as well as the constants and dictionaries that it may have.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingCodeFrame extends FrameBase {
    private static int MARG = 30;
    private JTextArea codeArea = new JTextArea();

    public MappingCodeFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.CODE, desktop, sipModel, "Mapping Code", false);
        codeArea.setFont(new Font("Monospaced", Font.BOLD, 10));
        codeArea.setEditable(false);
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                refresh();
            }
        });
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
                refresh();
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
                if (change != DOCUMENTATION) refresh();
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
        getAction().putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_M, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
    }

    void refresh() {
        Exec.run(new CodeUpdater());
    }

    @Override
    protected void buildContent(Container content) {
        content.add(scrollVH(codeArea));
    }

    private class CodeUpdater implements Swing {

        @Override
        public void run() {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                String code = recMapping.toCode();
                StringBuilder numbered = new StringBuilder();
                int index = 0;
                for (String line : code.split("\n")) {
                    numbered.append(String.format("%3d: ", ++index));
                    numbered.append(line).append('\n');
                }
                codeArea.setText(numbered.toString());
            }
            else {
                codeArea.setText("// No code");
            }
        }
    }

}
