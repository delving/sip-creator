/*
 * Copyright 2011 DELVING BV
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

import eu.delving.sip.base.Exec;
import eu.delving.sip.model.SipModel;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.awt.*;
import java.io.IOException;

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
        Exec.work(new PageFetcher());
        setPreferredSize(new Dimension(360, 400));
        setMinimumSize(getPreferredSize());
        setBackground(TINT);
    }

    private class PageFetcher implements Runnable {

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
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            setHtml("<html>" + ourPage);
                        }
                    });
                    sipModel.getStorage().setHelpHtml(ourPage);
                }
                else {
                    throw new IOException("Unable to find markers on the page");
                }
            }
            catch (Exception e) {
                sipModel.getFeedback().say("Unable to fetch help page via https");
                final String page = sipModel.getStorage().getHelpHtml();
                if (page == null) {
                    sipModel.getFeedback().say("Unable to fetch help page from workspace");
                }
                else {
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            setHtml("<html>" + page);
                        }
                    });
                    sipModel.getFeedback().say("Fetched help page from workspace");
                }
            }
        }
    }

    @Override
    public void setHtml(String html) {
        super.setHtml(String.format("<html><body bgcolor=\"F3F3FF\"><table cellpadding=\"10\"><tr><td>%s</td></tr></table></html>", html));
    }
}
