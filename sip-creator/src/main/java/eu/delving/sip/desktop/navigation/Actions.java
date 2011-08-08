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

import eu.delving.sip.desktop.windows.DesktopManager;
import eu.delving.sip.desktop.windows.WindowId;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Grouping and controlling actions per subject.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class Actions {

    private DesktopManager desktopManager;
    private Map<WindowId, AbstractAction> navigationActions = new HashMap<WindowId, AbstractAction>();
    private static final Logger LOG = Logger.getRootLogger();

    {
        for (WindowId windowId : WindowId.values()) {
            navigationActions.put(windowId, new GenericAction(windowId));
        }
    }

    public Actions(DesktopManager desktopManager) {
        this.desktopManager = desktopManager;
    }

    public Map<WindowId, AbstractAction> getMenuActions() {
        return filterActions(Type.NAVIGATION_MENU);
    }

    public Map<WindowId, AbstractAction> getBarActions() {
        return filterActions(Type.NAVIGATION_BAR);
    }

    private Map<WindowId, AbstractAction> filterActions(Type type) {
        Map<WindowId, AbstractAction> menuActions = new HashMap<WindowId, AbstractAction>();
        for (Map.Entry<WindowId, AbstractAction> entry : navigationActions.entrySet()) {
            if (type == entry.getKey().getType()) {
                menuActions.put(entry.getKey(), entry.getValue());
            }
        }
        return menuActions;
    }

    public Map<WindowId, AbstractAction> getNavigationActions() {
        return navigationActions;
    }

    public void setEnabled(boolean enabled) {
        LOG.info(String.format("Setting state to %s for %s%n", enabled, navigationActions.entrySet()));
        for (Map.Entry<WindowId, AbstractAction> entry : navigationActions.entrySet()) {
            entry.getValue().setEnabled(enabled);
        }
    }

    public AbstractAction getAction(WindowId windowId) {
        return navigationActions.get(windowId);
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

    public enum Type {
        NAVIGATION_BAR,
        NAVIGATION_MENU
    }
}
