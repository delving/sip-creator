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

import eu.delving.metadata.MappingResult;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.CompileState;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.KeystrokeHelper;
import eu.delving.sip.base.Swing;
import eu.delving.sip.model.MappingCompileModel;
import eu.delving.sip.model.SipModel;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.*;

/**
 * This frame shows the entire builder that is responsible for transforming the input to output,
 * as well as the constants, dictionaries, and functions that it may have.
 *
 *
 */

public class MappingCodeFrame extends FrameBase {
    public static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 18);
    private JTextArea listArea = new JTextArea();
    private RSyntaxTextArea recordArea = new RSyntaxTextArea(new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_GROOVY));
    private RSyntaxTextArea fieldArea = new RSyntaxTextArea(new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_GROOVY));
    private JCheckBox docuBox = new JCheckBox("Include Documentation");
    private JCheckBox codeBox = new JCheckBox("Include Groovy Code");
    private JCheckBox traceBox = new JCheckBox("Include Line Number Traces");
    private String themeMode;

    public MappingCodeFrame(final SipModel sipModel) {
        super(Which.MAPPING_CODE, sipModel, "Mapping Code");
        listArea.setFont(MONOSPACED);
        listArea.setEditable(false);
        setRSyntaxTheme(recordArea, themeMode);
        recordArea.setFont(MONOSPACED);
        recordArea.setEditable(false);
        setRSyntaxTheme(fieldArea, themeMode);
        fieldArea.setFont(MONOSPACED);
        fieldArea.setEditable(false);
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
        tabs.addTab("Whole Record Code", createWholeRecord());
        tabs.addTab("Current Node Mapping Code", scrollCodeVH(fieldArea));
        content.add(tabs);
    }

    private JPanel createWholeRecord() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.add(scrollCodeVH(recordArea), BorderLayout.CENTER);
        p.add(createCheckbox(), BorderLayout.SOUTH);
        p.add(OutputFrame.createSyntaxTextAreaSearch(recordArea), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createCheckbox() {
        JPanel p = new JPanel();
        p.add(traceBox);
        traceBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sipModel.getRecordCompileModel().setTrace(traceBox.isSelected());
            }
        });
        return p;
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
        public void mappingComplete(MappingResult mappingResult) {
            // nothing
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
                textArea.setText(code);
                textArea.setCaretPosition(0);
                OutputFrame.stopSyntaxTextAreaSearch(textArea);
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
                textArea.setCaretPosition(0);
            }
            else {
                textArea.setText("// No mapping");
            }
        }
    }

    @Override
    public void setTheme(String themeMode) {
        this.themeMode = themeMode;
        setRSyntaxTheme(recordArea, themeMode);
        setRSyntaxTheme(fieldArea, themeMode);
    }

}
