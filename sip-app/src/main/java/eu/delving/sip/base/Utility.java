/*
 * Copyright 2011 DELVING BV
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

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.OptList;
import eu.delving.sip.model.SourceTreeNode;
import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;

import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Help with SpringLayout forms
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Utility {
    private static Color NORMAL_BG = Color.WHITE;
    private static Color NORMAL_FG = Color.BLACK;
    private static Color ERROR_BG = new Color(255,200,200);
    public static Color UNEDITABLE_BG = new Color(255,255,200);
    private static Color DELIMITER_BG = new Color(255,255,200);
    public static Color MAPPED_COLOR = new Color(220,255,220);
    public static Color HILIGHTED_COLOR = new Color(255,205,205);

    public static final Icon BOOKMARK_EXPANDED_ICON = new ImageIcon(Utility.class.getResource("/icons/bookmark-expanded-icon.png"));
    public static final Icon BOOKMARK_ICON = new ImageIcon(Utility.class.getResource("/icons/bookmark-icon.png"));
    public static final Icon VALUE_ELEMENT_ICON = new ImageIcon(Utility.class.getResource("/icons/value-element-icon.png"));
    public static final Icon COMPOSITE_ELEMENT_ICON = new ImageIcon(Utility.class.getResource("/icons/composite-element-icon.png"));
    public static final Icon DOWNLOAD_ICON = new ImageIcon(Utility.class.getResource("/icons/download-icon.png"));
    public static final Icon UPLOAD_ICON = new ImageIcon(Utility.class.getResource("/icons/upload-icon.png"));
    public static final Icon ATTRIBUTE_ICON = new ImageIcon(Utility.class.getResource("/icons/attribute-icon.png"));
    public static final Icon UNMAPPABLE_ICON = new ImageIcon(Utility.class.getResource("/icons/unmappable-element-icon.png"));
    public static final Icon IMPORT_ICON = new ImageIcon(Utility.class.getResource("/icons/import-icon.png"));
    public static final Icon VALIDATE_ICON = new ImageIcon(Utility.class.getResource("/icons/validate-icon.png"));
    public static final Icon RELEASE_ICON = new ImageIcon(Utility.class.getResource("/icons/release-icon.png"));
    public static final Icon REWIND_ICON = new ImageIcon(Utility.class.getResource("/icons/rewind-icon.png"));
    public static final Icon PLAY_ICON = new ImageIcon(Utility.class.getResource("/icons/play-icon.png"));

    private static final StringTemplateGroup STRING_TEMPLATE = new StringTemplateGroup("Templates");

    public static StringTemplate getTemplate(String name) {
        return STRING_TEMPLATE.getInstanceOf("st/" + name);
    }

    public static void attachUrlLauncher(JTextArea area) {
        new URLLauncher(area);
    }

    public static void setEditable(JTextComponent component, boolean editable) {
        component.setEditable(editable);
        component.setBackground(editable ? NORMAL_BG : UNEDITABLE_BG);
    }

    public static void setError(JTextComponent component, boolean error) {
        component.setBackground(error ? ERROR_BG : UNEDITABLE_BG);
    }

    public static void setDelimitedColor(JComponent component, boolean selected) {
        component.setBackground(selected ? NORMAL_BG : DELIMITER_BG);
        component.setForeground(selected ? DELIMITER_BG : NORMAL_FG);
    }

    public static class URLLauncher implements CaretListener {

        private JTextArea outputArea;

        private URLLauncher(JTextArea outputArea) {
            this.outputArea = outputArea;
            outputArea.addCaretListener(this);
        }

        @Override
        public void caretUpdate(CaretEvent e) {
            int dot = e.getDot();
            int mark = e.getMark();
            if (dot != mark) {
                String text = outputArea.getText();
                int min = Math.min(dot, mark);
                int max = Math.min(text.length() - 1, Math.max(dot, mark));
                String urlString = text.substring(min, max);
                if (min > 1 && text.charAt(min - 1) == '>' && max < text.length() && text.charAt(max) == '<') {
                    if (validUrl(urlString)) {
                        showURL(urlString);
                    }
                    else {
                        outputArea.select(min, min);
                    }
                }
                else {
                    while (min > 1 && text.charAt(min - 1) != '>') {
                        min--;
                    }
                    while (max < text.length() - 1 && text.charAt(max + 1) != '<') {
                        max++;
                    }
                    if (validUrl(text.substring(min, max + 1))) {
                        outputArea.select(min, max + 1);
                    }
                    else {
                        outputArea.select(min, min);
                    }
                }
            }
        }

        private boolean validUrl(String urlString) {
            try {
                if (urlString.contains(">") || urlString.contains("<")) {
                    return false;
                }
                new URL(urlString);
                return true;
            }
            catch (MalformedURLException e1) {
                return false;
            }
        }

        boolean showURL(String urlString) {
            try {
                urlString = urlString.replaceAll("&amp;", "&");
                URL url = new URL(urlString);
                BasicService bs = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
                return bs.showDocument(url);
            }
            catch (UnavailableServiceException ue) {
                System.out.println("Wanted to launch " + urlString);
                return false;
            }
            catch (MalformedURLException e1) {
                return false;
            }
        }
    }

    public static URL getCodebase() {
        try {
            BasicService bs = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
            return bs.getCodeBase();
        }
        catch (UnavailableServiceException ue) {
            throw new RuntimeException("Unable to use JNLP service", ue);
        }
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

    public static JComponent scrollV(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        p.add(scroll);
        return p;
    }

    public static boolean isDictionaryPossible(NodeMapping nodeMapping) {
        if (nodeMapping == null || nodeMapping.recDefNode == null || !nodeMapping.hasOneStatsTreeNode()) return false;
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        Set<String> values = sourceTreeNode.getStatistics().getHistogramValues();
        OptList optList = nodeMapping.recDefNode.getOptList();
        return values != null && optList != null && nodeMapping.dictionary == null;
    }

    public static boolean refreshDictionary(NodeMapping nodeMapping) {
        if (!isDictionaryPossible(nodeMapping)) throw new RuntimeException("Should have checked");
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        return setDictionaryDomain(nodeMapping, sourceTreeNode.getStatistics().getHistogramValues());
    }

    public static int countNonemptyDictionaryEntries(NodeMapping nodeMapping) {
        int nonemptyEntries = 0;
        if (nodeMapping != null && nodeMapping.dictionary != null) {
            for (String value : nodeMapping.dictionary.values()) {
                if (!value.trim().isEmpty()) nonemptyEntries++;
            }
        }
        return nonemptyEntries;
    }

    public static boolean setDictionaryDomain(NodeMapping nodeMapping, Collection<String> domainValues) {
        if (nodeMapping == null) return false;
        boolean changed = false;
        if (nodeMapping.dictionary == null) {
            nodeMapping.dictionary = new TreeMap<String, String>();
            changed = true;
        }
        for (String key : domainValues)
            if (!nodeMapping.dictionary.containsKey(key)) {
                nodeMapping.dictionary.put(key, "");
                changed = true;
            }
        Set<String> unused = new HashSet<String>(nodeMapping.dictionary.keySet());
        unused.removeAll(domainValues);
        for (String unusedKey : unused) {
            nodeMapping.dictionary.remove(unusedKey);
            changed = true;
        }
        nodeMapping.groovyCode = null;
        if (changed) nodeMapping.notifyChanged();
        return changed;
    }

    public static void removeDictionary(NodeMapping nodeMapping) {
        if (nodeMapping == null) return;
        nodeMapping.dictionary = null;
        nodeMapping.groovyCode = null;
        nodeMapping.notifyChanged();
    }
}
