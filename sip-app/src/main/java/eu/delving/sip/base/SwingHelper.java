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

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Gathering together a number of things that are done with the Swing library from many parts of the code.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SwingHelper {
    private static Color NORMAL_BG = Color.WHITE;
    private static Color NORMAL_FG = Color.BLACK;
    private static Color ERROR_BG = new Color(255,200,200);
    public static Color UNEDITABLE_BG = new Color(255,255,200);
    private static Color DELIMITER_BG = new Color(255,255,200);
    public static Color MAPPED_COLOR = new Color(220,255,220);
    public static Color HILIGHTED_COLOR = new Color(255,205,205);
    public static Color LONG_TERM_JOB_COLOR = new Color(255,205,205);
    public static Color NORMAL_JOB_COLOR = new Color(220,255,220);

    public static final Icon VALUE_ELEMENT_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/value-element-icon.png"));
    public static final Icon COMPOSITE_ELEMENT_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/composite-element-icon.png"));
    public static final Icon DOWNLOAD_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/download-icon.png"));
    public static final Icon UPLOAD_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/upload-icon.png"));
    public static final Icon ATTRIBUTE_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/attribute-icon.png"));
    public static final Icon UNMAPPABLE_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/unmappable-element-icon.png"));
    public static final Icon IMPORT_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/import-icon.png"));
    public static final Icon VALIDATE_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/validate-icon.png"));
    public static final Icon RELEASE_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/release-icon.png"));
    public static final Icon REWIND_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/rewind-icon.png"));
    public static final Icon PLAY_ICON = new ImageIcon(SwingHelper.class.getResource("/icons/play-icon.png"));

    private static final StringTemplateGroup STRING_TEMPLATE = new StringTemplateGroup("Templates");

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
}
