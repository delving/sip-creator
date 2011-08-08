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

package eu.delving.sip.desktop.windows;

import eu.delving.sip.desktop.navigation.Actions;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * The identifier of a window and additional properties.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public enum WindowId {

    ANALYZE("Analyze", true, KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.ALT_MASK), Actions.Type.NAVIGATION_BAR),
    DATA_SET("Open data set", true, KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.ALT_MASK), Actions.Type.NAVIGATION_MENU),
    WELCOME("Welcome", true, KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_MASK), Actions.Type.NAVIGATION_MENU),
    AUTHENTICATION("Authentication", false, KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_MASK), Actions.Type.NAVIGATION_MENU),
    MAPPING("Mapping", true, KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.ALT_MASK), Actions.Type.NAVIGATION_BAR),
    PREVIEW("Preview", true, KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.ALT_MASK), Actions.Type.NAVIGATION_BAR),
    UPLOAD("Upload", true, KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.ALT_MASK), Actions.Type.NAVIGATION_BAR),
    NORMALIZE("Normalize", true, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), Actions.Type.NAVIGATION_BAR);

    private String title;
    private boolean draggable;
    private KeyStroke accelerator;
    private Actions.Type type;

    private WindowId(String title, @Deprecated boolean draggable, KeyStroke accelerator, Actions.Type type) {
        this.title = title;
        this.draggable = draggable;
        this.accelerator = accelerator;
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDraggable() {
        return draggable;
    }

    public KeyStroke getAccelerator() {
        return accelerator;
    }

    public Actions.Type getType() {
        return type;
    }
}
