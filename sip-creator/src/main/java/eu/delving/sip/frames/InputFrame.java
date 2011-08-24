/*
 * Copyright 2010 DELVING BV
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

import eu.delving.groovy.MetadataRecord;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.StringReader;

/**
 * The transformation from input record to output
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class InputFrame extends FrameBase {
    private HTMLDocument inputDocument = (HTMLDocument) new HTMLEditorKit().createDefaultDocument();
    private JButton firstButton = new JButton("First");
    private JButton nextButton = new JButton("Next");
    private RecordScanPopup recordScanPopup;
    private JLabel criterionLabel = new JLabel();

    public InputFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Input", false);
        sipModel.addParseListener(new SipModel.ParseListener() {
            @Override
            public void updatedRecord(MetadataRecord metadataRecord) {
                if (metadataRecord == null) {
                    SwingUtilities.invokeLater(new DocumentSetter(inputDocument, "<html><h1>No input</h1>"));
                }
                else {
                    SwingUtilities.invokeLater(new DocumentSetter(inputDocument, metadataRecord.toHtml()));
                }
            }
        });
        this.recordScanPopup = new RecordScanPopup(this, sipModel, new RecordScanPopup.Listener() {
            @Override
            public void searchStarted(String description) {
                closeFrame();
                criterionLabel.setText(description);
            }
        });
        firstButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                recordScanPopup.scan(false);
            }
        });
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                recordScanPopup.scan(true);
            }
        });
        setDefaultSize(400, 600);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(scroll(createRecordView()), BorderLayout.CENTER);
        content.add(createRecordButtonPanel(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    private JComponent createRecordView() {
        final JEditorPane recordView = new JEditorPane();
        recordView.setContentType("text/html");
        recordView.setDocument(inputDocument);
        recordView.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        recordView.setCaretPosition(0);
                    }
                });
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });
        recordView.setEditable(false);
        return recordView;

    }

    private JPanel createRecordButtonPanel() {
        int margin = 4;
        criterionLabel.setHorizontalAlignment(JLabel.CENTER);
        criterionLabel.setBackground(Color.WHITE);
        criterionLabel.setOpaque(true);
        criterionLabel.setBorder(BorderFactory.createEtchedBorder());
        JPanel p = new JPanel(new GridLayout(2, 2, margin, margin));
        p.setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin));
        p.add(firstButton);
        p.add(nextButton);
        p.add(new JButton(recordScanPopup.getAction()));
        recordScanPopup.init();
        criterionLabel.setText(recordScanPopup.getPredicateDescription());
        p.add(criterionLabel);
        return p;
    }

    private class DocumentSetter implements Runnable {

        private Document document;
        private String content;

        private DocumentSetter(Document document, String content) {
            this.document = document;
            this.content = content;
        }

        @Override
        public void run() {
            if (document instanceof HTMLDocument) {
                HTMLDocument htmlDocument = (HTMLDocument) document;
                int docLength = document.getLength();
                try {
                    document.remove(0, docLength);
                    HTMLEditorKit.ParserCallback callback = htmlDocument.getReader(0);
                    htmlDocument.getParser().parse(new StringReader(content), callback, true);
                    callback.flush();
                }
                catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                int docLength = document.getLength();
                try {
                    document.remove(0, docLength);
                    document.insertString(0, content, null);
                }
                catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
