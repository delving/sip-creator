/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.base;

import javax.swing.*;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

import static javax.swing.Action.*;
import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;

/**
 * Keystroke configurations
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class KeystrokeHelper {
    public static final KeyStroke SPACE = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
    public static final KeyStroke UP = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
    public static final KeyStroke DOWN = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
    public static final KeyStroke LEFT = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0);
    public static final KeyStroke RIGHT = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0);
    public static final KeyStroke MENU_G = menuKeystroke(KeyEvent.VK_G);
    public static final KeyStroke MENU_E = menuKeystroke(KeyEvent.VK_E);
    public static final KeyStroke MENU_H = menuKeystroke(KeyEvent.VK_H);
    public static final KeyStroke MENU_I = menuKeystroke(KeyEvent.VK_I);
    public static final KeyStroke MENU_S = menuKeystroke(KeyEvent.VK_S);
    public static final KeyStroke MENU_U = menuKeystroke(KeyEvent.VK_U);
    public static final KeyStroke MENU_W = menuKeystroke(KeyEvent.VK_W);
    public static final KeyStroke MENU_Z = menuKeystroke(KeyEvent.VK_Z);
    public static final KeyStroke SH_MENU_Z = menuShiftKeystroke(KeyEvent.VK_Z);

    public static KeyStroke menuDigit(int digit) {
        if (digit < 0 || digit > 9) throw new IllegalArgumentException("Not a digit: " + digit);
        return menuKeystroke(KeyEvent.VK_0 + digit);
    }

    public static void configAction(Action action, String text, Icon icon, KeyStroke keyStroke) {
        action.putValue(NAME, text);
        action.putValue(SMALL_ICON, icon);
        action.putValue(ACCELERATOR_KEY, keyStroke);
    }

    public static void attachAccelerator(Action action, JFrame frame) {
        KeyStroke stroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
        String name = (String) action.getValue(Action.NAME);
        JComponent component = (JComponent) frame.getContentPane();
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        component.getActionMap().put(name, action);
        addStrokeToName(action);
    }

    public static void attachAccelerator(Action action, JComponent component) {
        KeyStroke stroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
        String name = (String) action.getValue(Action.NAME);
        component.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        component.getActionMap().put(name, action);
        addStrokeToName(action);
    }

    public static void addStrokeToName(Action action) {
        KeyStroke stroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
        if (stroke == null) return;
        String name = String.format(
                "%s%s - %s",
                KeyEvent.getKeyModifiersText(stroke.getModifiers()), KeyEvent.getKeyText(stroke.getKeyCode()), action.getValue(Action.NAME)
        );
        action.putValue(Action.NAME, name);
    }

    public static void addKeyboardAction(Action action, KeyStroke keyStroke, JComponent component) {
        String name = (String) action.getValue(Action.NAME);
        component.getActionMap().put(name, action);
        component.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, name);
    }

    private static KeyStroke menuKeystroke(int virtualKey) {
        return KeyStroke.getKeyStroke(virtualKey, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    }

    private static KeyStroke menuShiftKeystroke(int virtualKey) {
        return KeyStroke.getKeyStroke(virtualKey, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | KeyEvent.SHIFT_DOWN_MASK);
    }
}
