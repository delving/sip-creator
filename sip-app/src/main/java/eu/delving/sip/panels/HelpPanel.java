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

package eu.delving.sip.panels;

import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.Storage;
import eu.delving.sip.model.SipModel;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.awt.*;
import java.io.*;

/**
 * Show help fetched from github
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class HelpPanel extends HtmlPanel {
    private static final String URL = "https://github.com/delving/sip-creator/wiki/SIP-Creator";
    private static final String START_MARKER = "STARTPAGE</p>";
    private static final String END_MARKER = "<p>ENDPAGE";
    private static final Color TINT = new Color(0.95f, 0.95f, 1.0f);
    private SipModel sipModel;
    private HttpClient httpClient;
    private boolean fetched;

    public HelpPanel(SipModel sipModel, HttpClient httpClient) {
        super("Help");
        this.sipModel = sipModel;
        this.httpClient = httpClient;
        sipModel.exec(new PageFetcher());
        int width = getFontMetrics(getFont()).stringWidth("this string determines the width of the help panel, yeah");
        setPreferredSize(new Dimension(width, 400));
        setMinimumSize(getPreferredSize());
        setBackground(TINT);
    }

    private class PageFetcher implements Work {

        @Override
        public void run() {
            try {
                HttpGet get = new HttpGet(URL);
                HttpResponse response = httpClient.execute(get);
                String fullPage = EntityUtils.toString(response.getEntity());
                int start = fullPage.indexOf(START_MARKER);
                int end = fullPage.indexOf(END_MARKER);
                if (end > start) {
                    final String ourPage = fullPage.substring(start + START_MARKER.length(), end).trim();
                    sipModel.exec(new Swing() {
                        @Override
                        public void run() {
                            setHtml("<html>" + ourPage);
                        }
                    });
                    setHelpHtml(ourPage);
                }
                else {
                    throw new IOException("Unable to find markers on the page");
                }
            }
            catch (Exception e) {
                sipModel.getFeedback().say("Unable to fetch help page via https");
                final String page = getHelpHtml();
                if (page == null) {
                    sipModel.getFeedback().say("Unable to fetch help page from workspace");
                }
                else {
                    sipModel.exec(new Swing() {
                        @Override
                        public void run() {
                            setHtml("<html>" + page);
                        }
                    });
                    sipModel.getFeedback().say("Fetched help page from workspace");
                }
            }
        }

        public String getHelpHtml() {
            File helpFile = helpFile();
            if (!helpFile.exists()) return null;
            try {
                InputStream in = new FileInputStream(helpFile);
                String help = IOUtils.toString(in);
                in.close();
                return help;
            }
            catch (Exception e) {
                return null;
            }
        }

        private File helpFile() {
            return sipModel.getStorage().cache(Storage.HELP_FILE);
        }

        public void setHelpHtml(String html) {
            File helpFile = helpFile();
            try {
                OutputStream out = new FileOutputStream(helpFile);
                IOUtils.write(html, out, "UTF-8");
                out.close();
            }
            catch (Exception e) {
//            e.printStackTrace();
            }
        }


    }

    @Override
    public void setHtml(String html) {
        super.setHtml(String.format("<html><body bgcolor=\"F3F3FF\">%s</html>", html));
    }

    @Override
    protected String[] getCSSRules() {
        return new String[]{
                "p {margin: 10 10 10 10; }",
                "h1 { text-align:center }",
                "h2 { text-indent:15 }",
                "h3 { text-indent:25 }",
                "h4 { text-indent:35 }",
                "pre { margin: 15 15 15 15; }",
        };
    }
}
