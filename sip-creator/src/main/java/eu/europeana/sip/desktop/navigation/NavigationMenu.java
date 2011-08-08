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

package eu.europeana.sip.desktop.navigation;

import eu.europeana.sip.desktop.windows.DesktopManager;
import eu.europeana.sip.desktop.windows.DesktopWindow;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * todo: add description
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class NavigationMenu extends JMenuBar {

    private DesktopManager desktopManager;
    private Actions actions;

    public NavigationMenu(DesktopManager desktopManager, Actions actions) {
        this.desktopManager = desktopManager;
        this.actions = actions;
        createMenus();
    }

    private void createMenus() {
        add(createFileMenu());
        add(createAccountMenu());
        add(createSipMenu());
    }

    private JMenu createSipMenu() {
        JMenu menu = new JMenu("SIP");
        for(AbstractAction action : actions.getNavigationActions().values()) {
            menu.add(action);
        }
        return menu;
    }

    private JMenu createAccountMenu() {
        JMenu menu = new JMenu("Account");
        JMenuItem signIn = new JMenuItem(
                new AbstractAction("Sign in") {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        desktopManager.add(DesktopWindow.WindowId.AUTHENTICATION);
                    }
                }
        );
        signIn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_MASK));
        menu.add(signIn);
        return menu;
    }

    private JMenu createFileMenu() {
        JMenu menu = new JMenu("File");
        JMenuItem exit = new JMenuItem(
                new AbstractAction("Exit") {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        // todo: show alert and ask to save state
                        System.exit(0);
                    }
                }
        );
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.ALT_MASK));
        JMenuItem loadDataSet = new JMenuItem(
                new AbstractAction(DesktopWindow.WindowId.DATA_SET.getTitle()) {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        // todo: add data set window
                    }
                }
        );
        loadDataSet.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_MASK));
        menu.add(loadDataSet);
        menu.add(exit);
        return menu;
    }
}
