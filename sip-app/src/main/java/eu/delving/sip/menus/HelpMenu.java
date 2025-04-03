/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.menus;

import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.net.URISyntaxException;

import static eu.delving.sip.files.Storage.TELEMETRY_ENABLED;

/**
 * A menu for help-related options and actions.
 *
 */
public class HelpMenu extends JMenu {
    private final SipModel sipModel;
    private final AllFrames allFrames;

    public HelpMenu(SipModel sipModel, AllFrames allFrames, boolean isTelemetryIncluded) {
        super("Help");
        this.sipModel = sipModel;
        this.allFrames = allFrames;
        if (isTelemetryIncluded) {
            add(new TelemetryMenuItem());
            add(new JSeparator());
        }
        add(new AboutMenuItem());
    }

    private class TelemetryMenuItem extends JCheckBoxMenuItem implements ItemListener {
        private final boolean isTelemetryEnabled;

        public TelemetryMenuItem() {
            super("Enable telemetry");
            isTelemetryEnabled = sipModel.getAppPreferences().getProperty(TELEMETRY_ENABLED, "false").contentEquals("true");
            setSelected(isTelemetryEnabled);
            addItemListener(this);
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            int state = e.getStateChange();
            //boolean selected = state == ItemEvent.SELECTED;

            // Clear the telemetry setting
            sipModel.getAppPreferences().remove(TELEMETRY_ENABLED);
            sipModel.saveAppProperties();
            JOptionPane.showMessageDialog(sipModel.getDesktop(), "Please restart the application now to change the telemetry setting.",
                    "Telemetry", JOptionPane.INFORMATION_MESSAGE);

            // Restore the selections state to the original setting
            removeItemListener(this);
            setState(isTelemetryEnabled);
            addItemListener(this);
        }
    }

    private class AboutMenuItem extends JMenuItem implements ActionListener {
        public AboutMenuItem() {
            super("About");
            addActionListener(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String text = """
<b>Copyright 2011-2025 Delving BV</b><br>
<br>
Licensed under the EUPL, Version 1.2 or – as soon they will be approved by<br>
the European Commission – subsequent versions of the EUPL (the "Licence");<br>
You may not use this work except in compliance with the Licence.<br>
You may obtain a copy of the Licence at:<br>
<br>
<a href="https://joinup.ec.europa.eu/software/page/eupl">https://joinup.ec.europa.eu/software/page/eupl</a><br>
<br>
Unless required by applicable law or agreed to in writing, software<br>
distributed under the Licence is distributed on an "AS IS" basis,<br>
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.<br>
See the Licence for the specific language governing permissions and<br>
limitations under the Licence.""";
            ImageIcon logo = new ImageIcon(getClass().getResource("/sip-creator-logo.png"));

            JEditorPane editorPane = new JEditorPane("text/html", text);
            editorPane.setEditable(false);
            editorPane.addHyperlinkListener(new HyperlinkListener() {
                @Override
                public void hyperlinkUpdate(HyperlinkEvent e)  {
                    if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                        try {
                            Desktop.getDesktop().browse(e.getURL().toURI());
                        } catch (IOException | URISyntaxException ex) {
                            // Ignore
                        }
                    }
                }
            });
            JOptionPane.showMessageDialog(sipModel.getDesktop(), editorPane,"About",
                    JOptionPane.PLAIN_MESSAGE, logo);
        }
    }

}
