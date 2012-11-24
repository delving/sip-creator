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

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * This frame shows the entire builder that is responsible for transforming the input to output,
 * as well as the constants, dictionaries, and functions that it may have.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingCodeFrame extends FrameBase {
    public static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 10);
    private JTextArea listArea = new JTextArea();
    private JTextArea recordArea = new JTextArea();
    private JTextArea fieldArea = new JTextArea();
    private JCheckBox docuBox = new JCheckBox("Include Documentation");
    private JCheckBox codeBox = new JCheckBox("Include Groovy Code");

    public MappingCodeFrame(final SipModel sipModel) {
        super(Which.MAPPING_CODE, sipModel, "Mapping Code");
        listArea.setFont(MONOSPACED);
        recordArea.setFont(MONOSPACED);
        fieldArea.setFont(MONOSPACED);
        Ear ear = new Ear();
        sipModel.getFieldCompileModel().addListener(ear);
        sipModel.getRecordCompileModel().addListener(ear);
        docuBox.addActionListener(ear);
        codeBox.addActionListener(ear);
    }

    @Override
    protected void onOpen(boolean opened) {
        sipModel.getRecordCompileModel().setEnabled(opened);
    }

    @Override
    protected void buildContent(Container content) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Mapping Text", createListPanel());
        tabs.addTab("Whole Record Code", scrollVH(recordArea));
        tabs.addTab("Current Node Mapping Code", scrollVH(fieldArea));
        content.add(tabs);
    }

    private JPanel createListPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.add(scrollVH(listArea), BorderLayout.CENTER);
        p.add(createButtons(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createButtons() {
        JPanel p = new JPanel();
        p.add(docuBox);
        p.add(codeBox);
        return p;
    }

    private class Ear implements MappingCompileModel.Listener, ActionListener {
        @Override
        public void stateChanged(CompileState state) {
            // nothing
        }

        @Override
        public void codeCompiled(MappingCompileModel.Type type, String code) {
            switch (type) {
                case RECORD:
                    exec(new CodeUpdater(code, recordArea));
                    exec(new TextUpdater(sipModel.getMappingModel().getRecMapping(), listArea, docuBox.isSelected(), codeBox.isSelected()));
                    break;
                case FIELD:
                    exec(new CodeUpdater(code, fieldArea));
                    break;
            }
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            new TextUpdater(sipModel.getMappingModel().getRecMapping(), listArea, docuBox.isSelected(), codeBox.isSelected()).run();
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
        private JTextArea textArea;
        private boolean withDocs;
        private boolean withGroovy;

        private TextUpdater(RecMapping recMapping, JTextArea textArea, boolean withDocs, boolean withGroovy) {
            this.recMapping = recMapping;
            this.textArea = textArea;
            this.withDocs = withDocs;
            this.withGroovy = withGroovy;
        }

        @Override
        public void run() {
            if (recMapping != null) {
                StringBuilder text = new StringBuilder("\n\tDelving SIPCreator Mapping in Text Form\n");
                text.append(String.format("\nSchema: %s\n", recMapping.getSchemaVersion()));
                for (NodeMapping nodeMapping : recMapping.getNodeMappings()) {
                    text.append("\nTo: ").append(nodeMapping.outputPath).append('\n');
                    for (Path inputPath : nodeMapping.getInputPaths()) {
                        text.append("\tFrom: ").append(inputPath).append('\n');
                    }
                    if (withDocs && nodeMapping.documentation != null) {
                        text.append("\t\tDocumentation:").append('\n');
                        for (String line : nodeMapping.documentation) {
                            text.append("\t\t\t").append(line).append('\n');
                        }
                    }
                    if (withGroovy && nodeMapping.groovyCode != null) {
                        text.append("\t\tGroovy Code:").append('\n');
                        for (String line : nodeMapping.groovyCode) {
                            text.append("\t\t\t").append(line).append('\n');
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
