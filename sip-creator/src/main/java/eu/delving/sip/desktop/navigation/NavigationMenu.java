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

import eu.delving.sip.desktop.windows.MenuGroup;
import eu.delving.sip.desktop.windows.WindowId;

import javax.swing.*;

/**
 * Populate the navigation menu by collecting actions with the WindowId.MenuGroup property.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 * @see WindowId
 * @see Actions
 * @see MenuGroup
 */
public class NavigationMenu extends JMenuBar {

    private Actions actions;

    public NavigationMenu(Actions actions) {
        this.actions = actions;
        createMenus();
    }

    private void createMenus() {
        for (MenuGroup group : MenuGroup.values()) {
            add(createMenu(group));
        }
    }

    private JMenu createMenu(MenuGroup group) {
        JMenu menu = new JMenu(group.getTitle());
        for (WindowId windowId : WindowId.values()) {
            if (group == windowId.getMenuGroup()) {
                menu.add(actions.getAction(windowId));
            }
        }
        return menu;
    }
}
