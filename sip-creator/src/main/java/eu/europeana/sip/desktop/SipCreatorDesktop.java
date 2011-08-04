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
 * The SIP-Creator contains the following windows:
 *
 * <ul>
 * <li>DataSet window</li>
 * <li>Analysis window</li>
 * <li>Mapping window</li>
 * <li>Log window</li>
 * <ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class SipCreatorDesktop {

    private SipCreatorPreferences sipCreatorPreferences;
    private JDesktopPane desktopPane = new JDesktopPane();

    {
        buildLayout();
        buildNavigation();
        sipCreatorPreferences = new SipCreatorPreferencesImpl(
                new SipCreatorPreferences.Listener() {

                    @Override
                    public void windowStateFound(SipCreatorPreferences.WindowState windowState) {
                        // todo: ask to restore window state.
                        restoreWindows(windowState);
                    }

                    @Override
                    public void credentialsFound(SipCreatorPreferences.Credentials credentials) {
                        // todo: pass to User object.
                    }
                }
        );
    }

    private void buildLayout() {
        WelcomeWindow welcomeWindow = new WelcomeWindow();
        welcomeWindow.setVisible(true);
        desktopPane.setLayout(new BorderLayout());
        desktopPane.add(welcomeWindow, BorderLayout.CENTER);
    }

    private void restoreWindows(SipCreatorPreferences.WindowState windowState) {
        for (JInternalFrame window : windowState.getWindows()) {
            desktopPane.add(window);
        }
    }

    private JComponent buildNavigation() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new Navigation(), desktopPane);
        splitPane.setBorder(null);
        splitPane.setSize(new Dimension(400, 400));
        splitPane.setDividerLocation(200);
        splitPane.setVisible(true);
        return splitPane;
    }

    public SipCreatorPreferences getSipCreatorPreferences() {
        return sipCreatorPreferences;
    }

    public static void main(String... args) {
        final SipCreatorDesktop sipCreatorDesktop = new SipCreatorDesktop();
        JFrame main = new JFrame();
        main.getContentPane().add(sipCreatorDesktop.buildNavigation(), BorderLayout.CENTER);
        main.setExtendedState(Frame.MAXIMIZED_BOTH);
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
