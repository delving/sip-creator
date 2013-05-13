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

import eu.delving.MappingResult;
import eu.delving.metadata.XPathContext;
import eu.delving.sip.base.*;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.LinkChecker;
import eu.delving.sip.model.MappingCompileModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.HtmlPanel;
import eu.delving.sip.xml.LinkCheckExtractor;
import eu.delving.sip.xml.ResultLinkChecks;
import org.apache.http.client.HttpClient;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.xml.xpath.XPathExpressionException;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
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
    private JTabbedPane tabs = new JTabbedPane();
    private HttpClient httpClient = HttpClientFactory.createLinkCheckClient();
    private HtmlPanel htmlPanel;

    public OutputFrame(final SipModel sipModel) {
        super(Which.OUTPUT, sipModel, "Output");
        htmlPanel = new HtmlPanel("Link Checks").addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
                    SwingHelper.launchURL(e.getURL().toString());
            }
        });
        sipModel.getRecordCompileModel().setEnabled(false);
        sipModel.getRecordCompileModel().addListener(new MappingCompileModel.Listener() {
            @Override
            public void stateChanged(CompileState state) {
            }

            @Override
            public void codeCompiled(MappingCompileModel.Type type, String code) {
            }

            @Override
            public void mappingComplete(MappingResult mappingResult) {
                try {
                    LinkCheckExtractor extractor = new LinkCheckExtractor(
                            mappingResult.getRecDefTree().getRecDef().fieldMarkers,
                            new XPathContext(mappingResult.getRecDefTree().getRecDef().namespaces)
                    );
                    final List<String> checkLines = extractor.getChecks(mappingResult);
                    DataSet dataSet = sipModel.getDataSetModel().getDataSet();
                    String prefix = sipModel.getMappingModel().getPrefix();
                    final LinkChecker linkChecker = new LinkChecker(httpClient);
                    ResultLinkChecks checks = new ResultLinkChecks(dataSet, prefix, linkChecker);
                    Work.DataSetPrefixWork work = checks.checkLinks(mappingResult.getLocalId(), checkLines, sipModel.getFeedback(), new Swing() {
                        @Override
                        public void run() {
                            StringBuilder out = new StringBuilder();
                            ResultLinkChecks.validLinesToHTML(checkLines, linkChecker, out);
                            htmlPanel.setHtml(out.toString());
                        }
                    });
                    if (work != null) {
                        sipModel.exec(work);
                    }
                }
                catch (XPathExpressionException e) {
                    sipModel.getFeedback().alert("XPath problem", e);
                }
            }
        });
    }

    @Override
    protected void onOpen(boolean opened) {
        sipModel.getRecordCompileModel().setEnabled(opened);
    }

    @Override
    protected void buildContent(Container content) {
        tabs.addTab("XML Output", createOutputPanel());
        tabs.addTab("Link Check", htmlPanel);
        content.add(tabs, BorderLayout.CENTER);
    }

    private JPanel createOutputPanel() {
        final JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output record"));
        final JTextArea area = new JTextArea(sipModel.getRecordCompileModel().getOutputDocument());
        area.setLineWrap(true);
        area.setFont(MONOSPACED);
        area.setWrapStyleWord(true);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                try {
                    String first = documentEvent.getDocument().getText(0, 1);
                    final boolean error = first.startsWith("#");
                    setError(area, error);
                    area.setCaretPosition(0);
                }
                catch (BadLocationException e) {
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
        p.add(scrollVH(area));
        return p;
    }
}
