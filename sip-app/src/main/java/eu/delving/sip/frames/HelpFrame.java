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

package eu.delving.sip.frames;

import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.HtmlPanel;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 * Show help fetched from github
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class HelpFrame extends FrameBase {
    private static final String URL = "https://github.com/delving/sip-creator/wiki/SIP-Creator";
    private static final String START_MARKER = "STARTPAGE</p>";
    private static final String END_MARKER = "<p>ENDPAGE";
    private HttpClient httpClient;
    private HtmlPanel html = new HtmlPanel("Help");
    private boolean fetched;

    public HelpFrame(JDesktopPane desktop, SipModel sipModel, HttpClient httpClient) {
        super(Which.HELP, desktop, sipModel, "Help", false);
        this.httpClient = httpClient;
        addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameOpened(InternalFrameEvent internalFrameEvent) {
                if (!fetched) {
                    fetched = true;
                    Exec.work(new PageFetcher());
                }
            }
        });
        html.setHtml("<html><h3>Fetching...</h3");
        getAction().putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_MASK)
        );
    }

    @Override
    protected void buildContent(Container content) {
        content.add(html);
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
                    final String ourPage = fullPage.substring(start+START_MARKER.length(), end).trim();
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            html.setHtml("<html>" + ourPage);
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
                            html.setHtml("<html>" + page);
                        }
                    });
                    sipModel.getFeedback().say("Fetched help page from workspace");
                }
            }
        }
    }
}
