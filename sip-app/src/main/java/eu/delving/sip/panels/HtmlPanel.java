/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.panels;

import eu.delving.sip.base.SwingHelper;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.io.StringReader;

/**
 * A bit of simple HTML can be put in a panel like this and displayed anywhere.
 *
 *
 */

public class HtmlPanel extends JPanel {
    private JEditorPane view = new JEditorPane();

    public HtmlPanel(String title) {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(title));
        view.setContentType("text/html");
        view.setDocument(createHtmlDocument());
        view.setEditable(false);
        add(SwingHelper.scrollVH(view));
        setPreferredSize(new Dimension(200, 200));
    }

    public HtmlPanel addHyperlinkListener(HyperlinkListener listener) {
        view.addHyperlinkListener(listener);
        return this;
    }

    public void setHtml(String html) {
        HTMLDocument doc = (HTMLDocument) view.getDocument();
        int docLength = doc.getLength();
        try {
            doc.remove(0, docLength);
            HTMLEditorKit.ParserCallback callback = doc.getReader(0);
            doc.getParser().parse(new StringReader(html), callback, true);
            callback.flush();
            view.setCaretPosition(0);
        }
        catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Document createHtmlDocument() {
        HTMLEditorKit kit = new HTMLEditorKit();
        // fresh sheet, otherwise you affect the one used everywhere!
        StyleSheet sheet = new StyleSheet();
        sheet.addStyleSheet(kit.getStyleSheet());
        for (String rule : getCSSRules()) sheet.addRule(rule);
        kit.setStyleSheet(sheet);
        return kit.createDefaultDocument();
    }

    protected String [] getCSSRules() {
        return new String[] {};
    }

}
