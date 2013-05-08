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

package eu.delving.sip.base;

import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;

import javax.swing.JTextArea;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Listen to the caret position of the XML output area and use WebStart's acces to the launching browser
 * to open a page containing any URL found selected under the caret.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class URLLauncher implements CaretListener {
    private SipModel sipModel;
    private JTextArea outputArea;
    private Feedback feedback;

    public URLLauncher(SipModel sipModel, JTextArea outputArea, Feedback feedback) {
        this.sipModel = sipModel;
        this.outputArea = outputArea;
        this.feedback = feedback;
        outputArea.addCaretListener(this);
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        int dot = e.getDot();
        int mark = e.getMark();
        if (dot != mark) {
            String text = outputArea.getText();
            int min = Math.min(dot, mark);
            int max = Math.min(text.length() - 1, Math.max(dot, mark));
            String urlString = text.substring(min, max);
            if (urlString.contains("\n")) return;
            if (min > 1 && text.charAt(min - 1) == '>' && max < text.length() && text.charAt(max) == '<') {
                if (validUrl(urlString)) {
                    SwingHelper.launchURLFromXML(urlString);
                }
                else {
                    outputArea.select(min, min);
                }
            }
            else {
                while (min > 1 && text.charAt(min - 1) != '>') {
                    min--;
                }
                while (max < text.length() - 1 && text.charAt(max) != '<') {
                    max++;
                }
                final String url = text.substring(min, max);
                if (validUrl(url)) {
                    outputArea.select(min, max);
                }
                else {
                    outputArea.select(min, min);
                }
            }
        }
    }

    private boolean validUrl(String urlString) {
        try {
            if (urlString.contains(">") || urlString.contains("<")) {
                return false;
            }
            new URL(urlString);
            return true;
        }
        catch (MalformedURLException e1) {
            return false;
        }
    }

}
