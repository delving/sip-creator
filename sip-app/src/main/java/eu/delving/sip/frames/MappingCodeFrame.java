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

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.CompileState;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.model.MappingCompileModel;
import eu.delving.sip.model.SipModel;

import javax.swing.Action;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import java.awt.*;
import java.awt.event.KeyEvent;

import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * This frame shows the entire builder that is responsible for transforming the input to output,
 * as well as the constants, dictionaries, and functions that it may have.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingCodeFrame extends FrameBase {
    private static int MARG = 30;
    public static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 10);
    private JTextArea pathListArea = new JTextArea();
    private JTextArea pathListGroovyArea = new JTextArea();
    private JTextArea recordArea = new JTextArea();
    private JTextArea fieldArea = new JTextArea();

    public MappingCodeFrame(final SipModel sipModel) {
        super(Which.MAPPING_CODE, sipModel, "Mapping Code");
        pathListArea.setFont(MONOSPACED);
        pathListGroovyArea.setFont(MONOSPACED);
        recordArea.setFont(MONOSPACED);
        fieldArea.setFont(MONOSPACED);
        Ear ear = new Ear();
        sipModel.getFieldCompileModel().addListener(ear);
        sipModel.getRecordCompileModel().addListener(ear);
        setPlacement(new Placement() {
            @Override
            public Point getLocation() {
                return new Point(MARG, MARG);
            }

            @Override
            public Dimension getSize() {
                return new Dimension(sipModel.getDesktop().getSize().width - MARG * 2, sipModel.getDesktop().getSize().height - MARG * 2);
            }
        });
        getAction().putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_M, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
    }

    @Override
    protected void onOpen(boolean opened) {
        sipModel.getRecordCompileModel().setEnabled(opened);
    }

    @Override
    protected void buildContent(Container content) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Mapping Text", scrollVH(pathListArea));
        tabs.addTab("Mapping Text with Groovy Code", scrollVH(pathListGroovyArea));
        tabs.addTab("Whole Record Code", scrollVH(recordArea));
        tabs.addTab("Current Node Mapping Code", scrollVH(fieldArea));
        content.add(tabs);
    }

    private class Ear implements MappingCompileModel.Listener {
        @Override
        public void stateChanged(CompileState state) {
            // nothing
        }

        @Override
        public void codeCompiled(MappingCompileModel.Type type, String code) {
            switch (type) {
                case RECORD:
                    exec(new CodeUpdater(code, recordArea));
                    exec(new TextUpdater(sipModel.getMappingModel().getRecMapping(), false, pathListArea));
                    exec(new TextUpdater(sipModel.getMappingModel().getRecMapping(), true, pathListGroovyArea));
                    break;
                case FIELD:
                    exec(new CodeUpdater(code, fieldArea));
                    break;
            }
        }
    }

    private static class CodeUpdater implements Swing {
        private String code;
        private JTextArea textArea;

        private CodeUpdater(String code, JTextArea textArea) {
            this.code = code;
            this.textArea = textArea;
        }

        @Override
        public void run() {
            if (code != null) {
                StringBuilder numbered = new StringBuilder();
                int index = 0;
                for (String line : code.split("\n")) {
                    numbered.append(String.format("%3d: ", ++index));
                    numbered.append(line).append('\n');
                }
                textArea.setText(numbered.toString());
            }
            else {
                textArea.setText("// No code");
            }
        }
    }

    private static class TextUpdater implements Swing {
        private RecMapping recMapping;
        private boolean withGroovy;
        private JTextArea textArea;

        private TextUpdater(RecMapping recMapping, boolean withGroovy, JTextArea textArea) {
            this.recMapping = recMapping;
            this.withGroovy = withGroovy;
            this.textArea = textArea;
        }

        @Override
        public void run() {
            if (recMapping != null) {
                StringBuilder text = new StringBuilder("\n\tDelving SIPCreator Mapping in Text Form\n");
                text.append(String.format("\nSchema: %s\n", recMapping.getSchemaVersion()));
                for (NodeMapping nodeMapping : recMapping.getNodeMappings()) {
                    text.append('\n').append(nodeMapping.outputPath).append('\n');
                    for (Path inputPath : nodeMapping.getInputPaths()) {
                        text.append('\t').append(inputPath).append('\n');
                    }
                    if (withGroovy && nodeMapping.groovyCode != null) {
                        for (String line : nodeMapping.groovyCode) {
                            text.append("\t\t").append(line).append('\n');
                        }
                    }
                }
                textArea.setText(text.toString());
            }
            else {
                textArea.setText("// No mapping");
            }
        }
    }

}
