/*
 * Copyright 2010 DELVING BV
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

package eu.delving.sip.base;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.IOException;
import java.io.StringReader;

/**
 * A bit of simple HTML can be put in a panel like this
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class HtmlPanel extends JPanel {
    private static final StringTemplateGroup TEMPLATE_GROUP = new StringTemplateGroup("Templates");
    private HTMLDocument doc = (HTMLDocument) new HTMLEditorKit().createDefaultDocument();
    private StringTemplate template;

    public HtmlPanel(String title) {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(title));
        JEditorPane view = new JEditorPane();
        view.setContentType("text/html");
        view.setDocument(doc);
        add(new JScrollPane(view));
    }

    public HtmlPanel setTemplate(String template) {
        this.template = TEMPLATE_GROUP.getInstanceOf(template);
        return this;
    }

    public HtmlPanel put(String key, Object value) {
        if (template == null) throw new RuntimeException("No template!");
        template.setAttribute(key, value);
        return this;
    }

    public void render() {
        int docLength = doc.getLength();
        try {
            String html = template.toString();
            doc.remove(0, docLength);
            HTMLEditorKit.ParserCallback callback = doc.getReader(0);
            doc.getParser().parse(new StringReader(html), callback, true);
            callback.flush();
        }
        catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
