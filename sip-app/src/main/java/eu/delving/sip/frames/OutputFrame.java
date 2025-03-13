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

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.KeystrokeHelper;
import eu.delving.sip.model.MappingCompileModel;
import eu.delving.sip.model.SipModel;
import org.apache.jena.riot.RDFFormat;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.*;

/**
 * This frame shows the entire output record so that the user can see the "big
 * picture" with only the
 * input tree and the output XML. This frame also shows the errors as reported
 * by the compile/validate
 * system.
 *
 *
 */

public class OutputFrame extends FrameBase {

    private RSyntaxTextArea outputArea;
    private String themeMode;

    public OutputFrame(final SipModel sipModel) {
        super(Which.OUTPUT, sipModel, "Output");
        sipModel.getRecordCompileModel().setEnabled(false);
    }

    @Override
    protected void onOpen(boolean opened) {
        sipModel.getRecordCompileModel().setEnabled(opened);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createOutputPanel(), BorderLayout.CENTER);
    }

    private JPanel createOutputPanel() {
        final JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output record"));

        final DefaultComboBoxModel outputTypes = new DefaultComboBoxModel();

        outputTypes.addElement("RDF/XML");
        outputTypes.addElement("JSONLD,COMPACT/PRETTY");
        outputTypes.addElement("NQUADS");
        outputTypes.addElement("NTRIPLES");
        outputTypes.addElement("TURTLE");

        final JComboBox outputTypesBox = new JComboBox(outputTypes);
        outputTypesBox.setSelectedIndex(0);

        p.add(outputTypesBox, BorderLayout.NORTH);

        outputArea = new RSyntaxTextArea(sipModel.getRecordCompileModel().getOutputDocument());
        setRSyntaxTheme(outputArea, themeMode);
        outputArea.setCodeFoldingEnabled(true);
        outputArea.setEditable(false);
        RTextScrollPane tsp = new RTextScrollPane(outputArea);
        p.add(tsp);
        addOutputDocumentListener(outputArea, outputArea.getDocument());

        outputTypesBox.addActionListener(e -> {
            String selection = outputTypesBox.getSelectedItem().toString();
            setRSyntaxTheme(outputArea, themeMode);
            stopSyntaxTextAreaSearch(outputArea);

            final MappingCompileModel mappingModel = sipModel.getRecordCompileModel();
            Document document;
            if (selection.contains("JSONLD")) {
                document = mappingModel.setOutputDocument(SyntaxConstants.SYNTAX_STYLE_JSON, outputArea,
                        RDFFormat.JSONLD_COMPACT_PRETTY);
            } else if (selection.contains("NQUADS")) {
                document = mappingModel.setOutputDocument(SyntaxConstants.SYNTAX_STYLE_NONE, outputArea,
                        RDFFormat.NQUADS);
            } else if (selection.contains("TURTLE")) {
                document = mappingModel.setOutputDocument(SyntaxConstants.SYNTAX_STYLE_NONE, outputArea,
                        RDFFormat.TURTLE);
            } else if (selection.contains("NTRIPLES")) {
                document = mappingModel.setOutputDocument(SyntaxConstants.SYNTAX_STYLE_NONE, outputArea,
                        RDFFormat.NTRIPLES);
            } else {
                document = mappingModel.setOutputDocument(SyntaxConstants.SYNTAX_STYLE_XML, outputArea,
                        RDFFormat.RDFXML);
            }
            addOutputDocumentListener(outputArea, document);

            mappingModel.triggerCompile();
        });

        p.add(scrollVH(outputArea), BorderLayout.CENTER);
        p.add(createSyntaxTextAreaSearch(outputArea), BorderLayout.SOUTH);
        return p;
    }

    private void addOutputDocumentListener(RSyntaxTextArea outputArea, Document document) {
        document.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                try {
                    String first = documentEvent.getDocument().getText(0, 1);
                    final boolean error = first.startsWith("#");
                    setRSyntaxTheme(outputArea, error ? "error" : themeMode);
                    stopSyntaxTextAreaSearch(outputArea);
                    sipModel.exec(() -> {
                        outputArea.setCaretPosition(0);
                    });
                } catch (BadLocationException e) {
                    // who cares
                }
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });
    }

    public static JPanel createSyntaxTextAreaSearch(RSyntaxTextArea outputArea) {
        boolean wasEditable = outputArea.isEditable();
        JPanel sp = new JPanel();

        JTextField searchField = new JTextField(25);
        searchField.addActionListener(new ActionListener() {
            List<Integer> found = new ArrayList<>();
            int foundSelected;

            @Override
            public void actionPerformed(ActionEvent e) {
                found.clear();
                foundSelected = 0;
                if (wasEditable) {
                    outputArea.setEditable(false);
                }
                String xml = outputArea.getText().toLowerCase();
                String sought = searchField.getText().toLowerCase();
                if (!sought.isEmpty()) {
                    int start = 0;
                    while (found.size() < 10000) {
                        int pos = xml.indexOf(sought, start);
                        if (pos < 0)
                            break;
                        found.add(pos);
                        start = pos + sought.length();
                    }
                    selectFound();

                    outputArea.getInputMap().put(KeystrokeHelper.DOWN, "next");
                    outputArea.getInputMap().put(KeystrokeHelper.RIGHT, "next");
                    outputArea.getInputMap().put(KeystrokeHelper.UP, "prev");
                    outputArea.getInputMap().put(KeystrokeHelper.LEFT, "prev");
                    outputArea.getActionMap().put("next", new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            foundSelected++;
                            selectFound();
                        }
                    });
                    outputArea.getActionMap().put("prev", new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            foundSelected--;
                            selectFound();
                        }
                    });
                } else {
                    stopSyntaxTextAreaSearch(outputArea);
                    if (wasEditable) {
                        outputArea.setEditable(true);
                    }
                }
            }

            void selectFound() {
                if (!found.isEmpty()) {
                    while (foundSelected < 0)
                        foundSelected += found.size();
                    foundSelected = foundSelected % found.size();
                    final Integer pos = found.get(foundSelected);
                    final int findLength = searchField.getText().length();
                    if (findLength > 0) {
                        outputArea.requestFocus();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                Rectangle viewRect = outputArea.modelToView(pos);
                                outputArea.scrollRectToVisible(viewRect);
                                outputArea.setCaretPosition(pos);
                                outputArea.moveCaretPosition(pos + findLength);
                            } catch (BadLocationException e) {
                                throw new RuntimeException("Location bad", e);
                            }
                        });
                    }
                }
            }
        });

        JLabel label = new JLabel("Search:", JLabel.RIGHT);
        label.setLabelFor(searchField);
        sp.add(label);
        sp.add(searchField);

        sp.add(new JLabel("Press ENTER, then use arrow keys"));
        return sp;
    }

    public static void stopSyntaxTextAreaSearch(JTextArea outputArea) {
        outputArea.getInputMap().remove(KeystrokeHelper.DOWN);
        outputArea.getInputMap().remove(KeystrokeHelper.RIGHT);
        outputArea.getInputMap().remove(KeystrokeHelper.UP);
        outputArea.getInputMap().remove(KeystrokeHelper.LEFT);
    }

    @Override
    public void setTheme(String themeMode) {
        this.themeMode = themeMode;
        setRSyntaxTheme(outputArea, themeMode);
    }

}
