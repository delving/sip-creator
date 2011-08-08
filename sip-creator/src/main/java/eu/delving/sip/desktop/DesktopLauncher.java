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

package eu.delving.sip.desktop;

import eu.delving.sip.desktop.navigation.Actions;
import eu.delving.sip.desktop.navigation.Navigation;
import eu.delving.sip.desktop.navigation.NavigationMenu;
import eu.delving.sip.desktop.windows.AuthenticationWindow;
import eu.delving.sip.desktop.windows.DesktopManager;
import eu.delving.sip.desktop.windows.DesktopWindow;
import eu.delving.sip.desktop.windows.WindowId;
import eu.europeana.sip.localization.Constants;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.util.List;

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
public class DesktopLauncher {

    private static final Logger LOG = Logger.getRootLogger();
    private final DesktopManager desktopManager = DesktopManager.getInstance();
    private DesktopPreferences desktopPreferences;
    private DesktopPreferences.DesktopState desktopState;
    private Navigation navigation;
    // todo: create user object
    private Object user;
    private AuthenticationWindow authenticationWindow;
    private DesktopPreferences.Credentials credentials;
    private Actions actions;

    {
        actions = new Actions(desktopManager);
        desktopPreferences = new DesktopPreferencesImpl(
                new DesktopPreferences.Listener() {

                    @Override
                    public void desktopStateFound(DesktopPreferences.DesktopState desktopState) {
                        DesktopLauncher.this.desktopState = desktopState;
                    }

                    @Override
                    public void credentialsFound(DesktopPreferences.Credentials credentials) {
                        LOG.info("Received credentials : " + credentials);
                        DesktopLauncher.this.credentials = credentials;
                    }
                }
        );
        buildLayout();
    }

    private void buildLayout() {
        authenticationWindow = new AuthenticationWindow(WindowId.AUTHENTICATION,
                new AuthenticationWindow.Listener() {

                    @Override
                    public void success(Object user) {
                        DesktopLauncher.this.user = user;
                        actions.setEnabled(true);
                        if (null != desktopState) {
                            restoreWindows(desktopState);
                        }
                    }

                    @Override
                    public void failed(Exception exception) {
                        // todo: show error window
                        LOG.error("Authentication failed", exception);
                    }
                }, desktopPreferences
        );
        if (null != credentials) {
            authenticationWindow.setCredentials(credentials);
        }
        desktopManager.add(authenticationWindow);
    }

    private void restoreWindows(DesktopPreferences.DesktopState desktopState) {
        for (DesktopWindow window : desktopState.getWindows()) {
            LOG.info("Adding window : " + window);
            try {
                desktopManager.add(window);
            }
            catch (Exception e) {
                LOG.error("Error adding window " + e);
            }
            window.setVisible(true);
            window.setSize(window.getWindowState().getSize());
            window.setLocation(window.getWindowState().getPoint());
            try {
                window.setSelected(window.getWindowState().isSelected());
            }
            catch (PropertyVetoException e) {
                LOG.error("Can't select window", e);
            }
        }
    }

    private JComponent buildNavigation() {
        navigation = new Navigation(actions);
        actions.setEnabled(false);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigation, desktopManager.getDesktop());
        splitPane.setBorder(null);
        splitPane.setSize(new Dimension(400, 400));
        splitPane.setDividerLocation(200);
        splitPane.setVisible(true);
        return splitPane;
    }

    public DesktopPreferences getDesktopPreferences() {
        return desktopPreferences;
    }

    public static void main(String... args) {
        final DesktopLauncher desktopLauncher = new DesktopLauncher();
        final JFrame main = new JFrame(Constants.SIP_CREATOR_TITLE);
        main.getContentPane().add(desktopLauncher.buildNavigation(), BorderLayout.CENTER);
        main.setExtendedState(Frame.MAXIMIZED_BOTH);
        main.setLocationRelativeTo(null);
        main.setJMenuBar(new NavigationMenu(desktopLauncher.desktopManager, desktopLauncher.actions));
        main.setVisible(true);
        main.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent windowEvent) {
                        // todo: do you want to close? and remember the state? or maybe silently remember.
                        switch (JOptionPane.showConfirmDialog(main, Constants.CLOSE, Constants.CLOSE, JOptionPane.YES_NO_OPTION)) {
                            case JOptionPane.NO_OPTION:
                                return;
                            case JOptionPane.YES_OPTION:
                                if (null == desktopLauncher.user) {
                                    System.exit(0);
                                }
                                desktopLauncher.getDesktopPreferences().saveDesktopState(
                                        new DesktopPreferences.DesktopState() {

                                            @Override
                                            public List<DesktopWindow> getWindows() {
                                                return desktopLauncher.desktopManager.getAllWindows();
                                            }
                                        }
                                );
                                System.exit(0);
                                break;

                        }
                    }
                }
        );
    }
}
