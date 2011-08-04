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

package eu.europeana.sip.desktop;

import eu.europeana.sip.localization.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * This is the main window of the SIP-Creator.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class SipCreatorDesktop extends JDesktopPane {

    private SipCreatorPreferences sipCreatorPreferences;

    {
        buildLayout();
        sipCreatorPreferences = new SipCreatorPreferencesImpl(
                new SipCreatorPreferences.Listener() {

                    @Override
                    public void windowStateFound(SipCreatorPreferences.WindowState windowState) {
                        // todo: ask to restore window state.
                    }

                    @Override
                    public void credentialsFound(SipCreatorPreferences.Credentials credentials) {
                        // todo: pass to User object.
                    }
                }
        );
    }

    private void buildLayout() {
        AuthenticationWindow authenticationWindow = new AuthenticationWindow();
        authenticationWindow.setVisible(true);
        authenticationWindow.setSize(new Dimension(300, 200));
        add(authenticationWindow);
    }

    public SipCreatorPreferences getSipCreatorPreferences() {
        return sipCreatorPreferences;
    }

    public static void main(String... args) {
        final SipCreatorDesktop sipCreatorDesktop = new SipCreatorDesktop();
        JFrame main = new JFrame();
        main.getContentPane().add(sipCreatorDesktop, BorderLayout.CENTER);
        main.setSize(new Dimension(1200, 800));
        main.setLocationRelativeTo(null);
        main.setVisible(true);
        main.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent windowEvent) {
                        // todo: do you want to close? and remember the state? or maybe silently remember.
                        switch (JOptionPane.showConfirmDialog(null, Constants.CLOSE)) {
                            case JOptionPane.NO_OPTION:
                                return;
                            case JOptionPane.CANCEL_OPTION:
                                return;
                            case JOptionPane.YES_OPTION:
                                sipCreatorDesktop.getSipCreatorPreferences().saveWindowState(null /** todo: window state */);
                                // todo: ask to save state
                                System.exit(0);
                                break;

                        }
                    }
                }
        );
    }
}
