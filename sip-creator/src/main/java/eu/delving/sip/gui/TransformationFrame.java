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

package eu.delving.sip.gui;

import eu.delving.groovy.MetadataRecord;
import eu.europeana.sip.gui.RecordSearchPanel;
import eu.europeana.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
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
import java.awt.FlowLayout;
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

public class TransformationFrame extends FrameBase {
    private HTMLDocument inputDocument = (HTMLDocument) new HTMLEditorKit().createDefaultDocument();
    private JButton firstButton = new JButton("First");
    private JButton nextButton = new JButton("Next");
    private CriterionFrame criterionFrame;
    private JLabel criterionLabel = new JLabel();

    public TransformationFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Transformation", false);
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
    }

    @Override
    protected void buildContent(Container content) {
        this.criterionFrame = new CriterionFrame(sipModel);
        content.add(createCenter(), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    private JComponent createCenter() {
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.add(createRecordPanel());
        p.add(createOutputPanel());
        return p;
    }

    private JComponent createSouth() {
        JButton validate = new JButton("Validate all records");
        JCheckBox discardInvalid = new JCheckBox("Discard invalid records");
        JButton upload = new JButton("Upload Dataset and mapping");
        upload.setEnabled(false);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(validate);
        p.add(discardInvalid);
        p.add(upload);
        return p;
    }

    // todo: view invalid records popup

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output Record"));
        JTextArea area = new JTextArea(sipModel.getRecordCompileModel().getOutputDocument());
        area.setEditable(false);
        p.add(scroll(area));
        return p;
    }

    private JPanel createRecordPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Input Record"));
        p.add(scroll(createRecordView()), BorderLayout.CENTER);
        p.add(createRecordButtonPanel(), BorderLayout.SOUTH);
        return p;
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
        p.add(new JButton(criterionFrame.getAction()));
        criterionFrame.init();
        criterionLabel.setText(criterionFrame.recordSearchPanel.getPredicateDescription());
        p.add(criterionLabel);
        return p;
    }

    private class CriterionFrame extends FrameBase {

        private RecordSearchPanel recordSearchPanel;

        public CriterionFrame(SipModel sipModel) {
            super(TransformationFrame.this, sipModel, "Criteria", true);
            setDefaultSize(500, 200);
        }

        @Override
        protected void buildContent(Container content) {
            recordSearchPanel = new RecordSearchPanel(sipModel, new RecordSearchPanel.Listener() {
                @Override
                public void searchStarted(String description) {
                    closeFrame();
                    criterionLabel.setText(description);
                }

                @Override
                public void searchFinished() {
                    // todo: remove this function eventually
                }
            });
            firstButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    recordSearchPanel.scan(false);
                }
            });
            nextButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    recordSearchPanel.scan(true);
                }
            });
            content.add(recordSearchPanel);
        }

        @Override
        protected void refresh() {
            recordSearchPanel.clear();
        }
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
