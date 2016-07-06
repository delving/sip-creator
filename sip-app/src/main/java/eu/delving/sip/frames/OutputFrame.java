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
import eu.delving.sip.model.SipModel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.scrollVH;
import static eu.delving.sip.base.SwingHelper.setError;

/**
 * This frame shows the entire output record so that the user can see the "big picture" with only the
 * input tree and the output XML.  This frame also shows the errors as reported by the compile/validate
 * system.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class OutputFrame extends FrameBase {
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 12);
    private JTextField searchField = new JTextField(25);
    private JTextArea outputArea;
    private List<Integer> found = new ArrayList<>();
    private int foundSelected;

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

        final RSyntaxTextArea outputArea = new RSyntaxTextArea(sipModel.getRecordCompileModel().getOutputDocument());
        outputArea.setCodeFoldingEnabled(true);
        RTextScrollPane tsp = new RTextScrollPane(outputArea);
        p.add(tsp);
        outputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                try {
                    String first = documentEvent.getDocument().getText(0, 1);
                    final boolean error = first.startsWith("#");
                    setError(outputArea, error);
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
        outputArea.getInputMap().put(KeystrokeHelper.DOWN, "next");
        outputArea.getInputMap().put(KeystrokeHelper.RIGHT, "next");
        outputArea.getInputMap().put(KeystrokeHelper.UP, "prev");
        outputArea.getInputMap().put(KeystrokeHelper.LEFT, "prev");
        outputArea.getActionMap().put("next", new NextAction());
        outputArea.getActionMap().put("prev", new PrevAction());
        searchField.addActionListener(e -> {
            found.clear();
            foundSelected = 0;
            String xml = outputArea.getText().toLowerCase();
            String sought = searchField.getText().toLowerCase();
            if (!sought.isEmpty()) {
                int start = 0;
                while (found.size() < 30) {
                    int pos = xml.indexOf(sought, start);
                    if (pos < 0) break;
                    found.add(pos);
                    start = pos + sought.length();
                }
            }
            selectFound();
        });
        JPanel sp = new JPanel();
        JLabel label = new JLabel("Search:", JLabel.RIGHT);
        label.setLabelFor(searchField);
        sp.add(label);
        sp.add(searchField);
        sp.add(new JLabel("Press ENTER, then use arrow keys"));
        p.add(scrollVH(outputArea), BorderLayout.CENTER);
        p.add(sp, BorderLayout.SOUTH);
        return p;
    }

    private class PrevAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            foundSelected--;
            selectFound();
        }
    }

    private class NextAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            foundSelected++;
            selectFound();
        }
    }

    private void selectFound() {
        if (found.isEmpty()) {
            outputArea.select(0, 0);
        } else {
            foundSelected = (foundSelected + found.size()) % found.size();
            final Integer pos = found.get(foundSelected);
            final int findLength = searchField.getText().length();
            if (findLength == 0) return;
            outputArea.requestFocus();
            sipModel.exec(() -> {
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
