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

import org.antlr.stringtemplate.AttributeRenderer;
import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.apache.commons.lang.StringEscapeUtils;

import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.net.URL;

import static javax.swing.Action.*;
import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;

/**
 * Gathering together a number of things that are done with the Swing library from many parts of the code.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SwingHelper {
    private static Color NORMAL_BG = Color.WHITE;
    private static Color NORMAL_FG = Color.BLACK;
    private static Color ERROR_BG = new Color(255, 200, 200);
    public static Color UNEDITABLE_BG = new Color(255, 255, 200);
    private static Color DELIMITER_BG = new Color(255, 255, 200);
    public static Color MAPPED_COLOR = new Color(220, 255, 220);
    public static Color HILIGHTED_COLOR = new Color(255, 205, 205);
    public static Color LONG_TERM_JOB_COLOR = new Color(255, 180, 180);
    public static Color NORMAL_JOB_COLOR = new Color(200, 255, 200);

    public static final Icon ICON_VALUE = icon("value");
    public static final Icon ICON_COMPOSITE = icon("composite");
    public static final Icon ICON_ATTRIBUTE = icon("attribute");
    public static final Icon ICON_UNMAPPABLE = icon("unmappable");
    public static final Icon ICON_REWIND = icon("rewind");
    public static final Icon ICON_PLAY = icon("play");
    public static final Icon ICON_VALIDATE = icon("validate");
    public static final Icon ICON_UNLOCK = icon("unlock");
    public static final Icon ICON_SELECT_ANOTHER = icon("choose-another");
    public static final Icon ICON_DOWNLOAD = icon("download");
    public static final Icon ICON_UPLOAD = icon("upload");
    public static final Icon ICON_IMPORT = icon("import");
    public static final Icon ICON_EMPTY = icon("empty");
    public static final Icon ICON_OWNED = icon("owned");
    public static final Icon ICON_UNAVAILABLE = icon("unavailable");
    public static final Icon ICON_BUSY = icon("busy");
    public static final Icon ICON_HUH = icon("huh");
    public static final Icon ICON_FETCH_LIST = icon("fetch-list");
    public static final Icon ICON_EDIT = icon("edit");

    public static final KeyStroke MENU_D = menuKeystroke(KeyEvent.VK_D);
    public static final KeyStroke MENU_E = menuKeystroke(KeyEvent.VK_E);
    public static final KeyStroke MENU_H = menuKeystroke(KeyEvent.VK_H);
    public static final KeyStroke MENU_I = menuKeystroke(KeyEvent.VK_I);
    public static final KeyStroke MENU_M = menuKeystroke(KeyEvent.VK_M);
    public static final KeyStroke MENU_S = menuKeystroke(KeyEvent.VK_S);
    public static final KeyStroke MENU_U = menuKeystroke(KeyEvent.VK_U);
    public static final KeyStroke MENU_V = menuKeystroke(KeyEvent.VK_V);
    public static final KeyStroke MENU_Z = menuKeystroke(KeyEvent.VK_Z);
    public static final KeyStroke SH_MENU_Z = menuShiftKeystroke(KeyEvent.VK_Z);
    public static KeyStroke menuDigit(int digit) {
        if (digit < 0 || digit > 9) throw new IllegalArgumentException("Not a digit: "+digit);
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
        component.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, name);
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
        String name = (String) action.getValue(Action.NAME);
        name += " - " + KeyEvent.getKeyModifiersText(stroke.getModifiers()) + KeyEvent.getKeyText(stroke.getKeyCode());
        action.putValue(Action.NAME, name);
    }

    public static void addSpaceAction(Action action, JComponent component) {
        String name = (String) action.getValue(Action.NAME);
        component.getActionMap().put(name, action);
        component.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), name);
    }

    private static KeyStroke menuKeystroke(int virtualKey) {
        return KeyStroke.getKeyStroke(virtualKey, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    }

    private static KeyStroke menuShiftKeystroke(int virtualKey) {
        return KeyStroke.getKeyStroke(virtualKey, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | KeyEvent.SHIFT_DOWN_MASK);
    }

    private static Icon icon(String resource) {
        String name = "/icons/"+ resource + ".png";
        URL url = SwingHelper.class.getResource(name);
        if (url == null) throw new RuntimeException("Cannot find " + name);
        return new ImageIcon(url);
    }

    private static final StringTemplateGroup STRING_TEMPLATE = new StringTemplateGroup("Templates");

    static {
        STRING_TEMPLATE.registerRenderer(String.class, new HtmlEncodedRenderer());
    }

    public static StringTemplate getTemplate(String name) {
        return STRING_TEMPLATE.getInstanceOf("st/" + name);
    }

    public static void setEditable(JTextComponent component, boolean editable) {
        component.setEditable(editable);
        component.setBackground(editable ? NORMAL_BG : UNEDITABLE_BG);
    }

    public static void setError(JTextComponent component, boolean error) {
        component.setBackground(error ? ERROR_BG : NORMAL_BG);
    }

    public static void setDelimitedColor(JComponent component, boolean selected) {
        component.setBackground(selected ? NORMAL_BG : DELIMITER_BG);
        component.setForeground(selected ? DELIMITER_BG : NORMAL_FG);
    }

    public static JComponent scrollVH(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return scroll;
    }

    public static JComponent scrollVH(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        p.add(scroll);
        return p;
    }

    public static JComponent scrollV(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return scroll;
    }

    public static JComponent scrollH(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        return scroll;
    }

    public static JComponent scrollV(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        p.add(scroll);
        return p;
    }

    public static boolean isDevelopmentMode() {
        try {
            ServiceManager.lookup("javax.jnlp.BasicService");
            return false;
        }
        catch (UnavailableServiceException ue) {
            return true;
        }
    }

    private static class HtmlEncodedRenderer implements AttributeRenderer {
        @Override
        public String toString(Object o) {
            StringBuilder out = new StringBuilder();
            for (String line : o.toString().split("\n")) {
                line = StringEscapeUtils.escapeHtml(line);
                boolean nonSpace = false;
                for (char c : line.toCharArray()) {
                    if (nonSpace) {
                        out.append(c);
                    }
                    else if (c == ' ') {
                        out.append("&nbsp;");
                    }
                    else {
                        nonSpace = true;
                        out.append(c);
                    }
                }
                out.append("<br>\n");
            }
            return out.toString();
        }
    }
}
