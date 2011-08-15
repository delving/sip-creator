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

package eu.delving.sip.desktop.navigation;

import eu.delving.sip.desktop.DesktopLauncher;
import eu.delving.sip.desktop.DesktopPreferences;
import eu.delving.sip.desktop.DesktopStateImpl;
import eu.delving.sip.desktop.WindowState;
import eu.delving.sip.desktop.WorkspaceImpl;
import eu.delving.sip.desktop.windows.DesktopManager;
import eu.delving.sip.desktop.windows.WindowId;
import eu.europeana.sip.localization.Constants;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grouping and controlling actions per subject.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class Actions {

    private static final Logger LOG = Logger.getRootLogger();
    private DesktopManager desktopManager;
    private Map<WindowId, Action> navigationActions = new HashMap<WindowId, Action>();

    {
        for (WindowId windowId : WindowId.values()) {
            Action action = windowId.getAction();
            if (null == action) {
                action = new GenericAction(windowId);
            }
            else {
                action = new ActionWrapper(windowId);
            }
            navigationActions.put(windowId, action);
        }
    }

    public Actions(DesktopManager desktopManager) {
        this.desktopManager = desktopManager;
    }

    public void setEnabled(boolean enabled) {
        for (Map.Entry<WindowId, Action> entry : navigationActions.entrySet()) {
            entry.getValue().setEnabled(enabled);
        }
    }

    public Action getAction(WindowId windowId) {
        return navigationActions.get(windowId);
    }

    /**
     * These actions are not bound to a menu item, and thus will be shown in the left navigation bar.
     *
     * @return The actions for the navigation bar.
     */
    public Map<WindowId, Action> getBarActions() {
        Map<WindowId, Action> map = new HashMap<WindowId, Action>();
        for (Map.Entry<WindowId, Action> entry : navigationActions.entrySet()) {
            if (null == entry.getKey().getMenuGroup()) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    private class ActionWrapper extends AbstractAction {

        private Action action;

        private ActionWrapper(WindowId windowId) {
            super(windowId.getTitle());
            this.action = windowId.getAction();
            putValue(ACCELERATOR_KEY, windowId.getAccelerator());
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            action.actionPerformed(actionEvent);
        }
    }

    private class GenericAction extends AbstractAction {

        private WindowId windowId;

        private GenericAction(WindowId windowId) {
            super(windowId.getTitle());
            this.windowId = windowId;
            putValue(ACCELERATOR_KEY, windowId.getAccelerator());
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            desktopManager.add(windowId);
        }
    }

    public static class ExitAction extends AbstractAction {

        private DesktopLauncher desktopLauncher;

        public ExitAction(DesktopLauncher desktopLauncher) {
            this.desktopLauncher = desktopLauncher;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            switch (JOptionPane.showConfirmDialog(null, Constants.CLOSE, Constants.CLOSE, JOptionPane.YES_NO_OPTION)) {
                case JOptionPane.NO_OPTION:
                    return;
                case JOptionPane.YES_OPTION:
                    List<WindowState> allWindowStates = desktopLauncher.getDesktopManager().getWindowStates();
                    // todo: should come from preferences
                    desktopLauncher.getDesktopPreferences().saveDesktopState(
                            new DesktopStateImpl(null == desktopLauncher.getCurrentStore() ? "-" : desktopLauncher.getCurrentStore().getSpec(),
                                    allWindowStates));
                    System.exit(0);
                    break;
            }
        }
    }

    public static class WorkspaceAction extends AbstractAction {

        private DesktopPreferences desktopPreferences;

        public WorkspaceAction(DesktopPreferences desktopPreferences) {
            this.desktopPreferences = desktopPreferences;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            JFileChooser chooser = new JFileChooser("Select workspace");
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(null)) {
                LOG.info("Selected path : " + chooser.getSelectedFile());
                desktopPreferences.saveWorkspace(new WorkspaceImpl(chooser.getSelectedFile().getAbsolutePath()));
            }
        }
    }

    public Action getDataSetAction() {
        return new AbstractAction() {

            @Override
            public void actionPerformed
                    (ActionEvent actionEvent) {
                Actions.this.desktopManager.getDataSetWindow().setVisible(true);
            }
        };
    }
}
